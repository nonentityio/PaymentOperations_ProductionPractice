package org.eltech.adapter.out.persistence

import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentOutboxItem
import org.eltech.domain.model.PaymentStatus
import org.eltech.domain.model.PaymentStatusChange
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

class PostgresPaymentRepository(private val pool: Pool) : PaymentRepositoryPort {
    override fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult> {
        val sql = """
            select *
            from create_payment_request(
                $1::varchar,
                $2::varchar,
                $3::service_category,
                $4::numeric,
                $5::currency_code,
                $6::varchar,
                $7::varchar,
                $8::varchar
            )
        """.trimIndent()

        return pool.preparedQuery(sql)
            .execute(
                Tuple.of(
                    command.clientId,
                    command.providerId,
                    command.serviceCategory,
                    command.amount,
                    command.currency,
                    command.requisite,
                    command.idempotencyKey,
                    command.requestHash
                )
            )
            .map { rows -> rows.first().toCreatePaymentResult() }
            .recover { error ->
                if (error.message?.contains("Idempotency-Key", ignoreCase = true) == true) {
                    Future.failedFuture(IllegalArgumentException(error.message))
                } else {
                    Future.failedFuture(error)
                }
            }
    }

    override fun updateStatus(paymentId: UUID, newStatus: PaymentStatus, failureReason: String?): Future<Void> {
        return pool.preparedQuery("select change_payment_status($1::uuid, $2::payment_status, $3::varchar)")
            .execute(Tuple.of(paymentId, newStatus.name, failureReason))
            .mapEmpty()
    }

    override fun addAttempt(
        paymentId: UUID,
        providerId: String,
        attemptNumber: Int,
        status: String,
        errorCode: String?
    ): Future<Void> {
        return pool.preparedQuery("select register_payment_attempt($1::uuid, $2::varchar, $3::integer, $4::attempt_status, $5::varchar)")
            .execute(Tuple.of(paymentId, providerId, attemptNumber, status, errorCode))
            .mapEmpty()
    }

    override fun addProviderResponse(
        paymentId: UUID,
        providerId: String,
        responseCode: Int,
        responseStatus: String
    ): Future<Void> {
        return pool.preparedQuery("select register_provider_response($1::uuid, $2::varchar, $3::smallint, $4::provider_response_status)")
            .execute(Tuple.of(paymentId, providerId, responseCode, responseStatus))
            .mapEmpty()
    }

    override fun fetchPayment(paymentId: UUID): Future<Payment> {
        return pool.preparedQuery("select * from get_payment($1::uuid)")
            .execute(Tuple.of(paymentId))
            .compose { rows ->
                val row = rows.firstOrNull()
                if (row == null) Future.failedFuture(NoSuchElementException("payment not found"))
                else Future.succeededFuture(row.toPayment())
            }
    }

    override fun fetchHistory(paymentId: UUID): Future<List<PaymentStatusChange>> {
        return pool.preparedQuery("select * from get_payment_history($1::uuid)")
            .execute(Tuple.of(paymentId))
            .map { rows ->
                val changes = rows.map { it.toStatusChange() }
                changes.mapIndexed { index, change ->
                    val previousAt = changes.getOrNull(index - 1)?.changedAt ?: change.changedAt
                    change.copy(durationMs = Duration.between(previousAt, change.changedAt).toMillis().coerceAtLeast(0))
                }
            }
    }

    override fun listPayments(clientId: String?): Future<List<Payment>> {
        return pool.preparedQuery("select * from list_payments($1::varchar, 100::integer)")
            .execute(Tuple.of(clientId))
            .map { rows -> rows.map { it.toPayment() } }
    }

    override fun claimPaymentOutboxBatch(limit: Int): Future<List<PaymentOutboxItem>> {
        return pool.preparedQuery("select * from claim_payment_outbox_batch($1::integer)")
            .execute(Tuple.of(limit))
            .map { rows -> rows.map { it.toPaymentOutboxItem() } }
    }

    override fun markPaymentOutboxDone(outboxId: UUID): Future<Void> {
        return pool.preparedQuery("select mark_payment_outbox_done($1::uuid)")
            .execute(Tuple.of(outboxId))
            .mapEmpty()
    }

    override fun releaseStalePaymentOutbox(afterSeconds: Int): Future<Int> {
        return pool.preparedQuery("select release_stale_payment_outbox($1::integer) as released")
            .execute(Tuple.of(afterSeconds))
            .map { rows -> rows.first().getInteger("released") }
    }

    override fun failStaleProcessing(afterSeconds: Int): Future<Int> {
        return pool.preparedQuery("select expire_stale_processing_payments($1::integer) as expired")
            .execute(Tuple.of(afterSeconds))
            .map { rows -> rows.first().getInteger("expired") }
    }

    private fun Row.toCreatePaymentResult(): CreatePaymentResult {
        return CreatePaymentResult(
            paymentId = getUUID("payment_id"),
            clientId = getString("client_id"),
            providerId = getString("provider_id"),
            serviceCategory = getString("service_category"),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            status = PaymentStatus.valueOf(getString("status")),
            idempotentReplay = getBoolean("idempotent_replay")
        )
    }

    private fun Row.toPayment(): Payment {
        return Payment(
            paymentId = getUUID("payment_id"),
            clientId = getString("client_id"),
            providerId = getString("provider_id"),
            serviceCategory = getString("service_category"),
            amount = getBigDecimal("amount"),
            currency = getString("currency"),
            requisite = getString("requisite"),
            status = PaymentStatus.valueOf(getString("status")),
            failureReason = getString("failure_reason"),
            createdAt = getOffsetDateTime("created_at"),
            updatedAt = getOffsetDateTime("updated_at")
        )
    }

    private fun Row.toStatusChange(): PaymentStatusChange {
        return PaymentStatusChange(
            oldStatus = getString("old_status")?.let(PaymentStatus::valueOf),
            newStatus = PaymentStatus.valueOf(getString("new_status")),
            changedAt = getOffsetDateTime("changed_at"),
            durationMs = 0
        )
    }

    private fun Row.toPaymentOutboxItem(): PaymentOutboxItem {
        return PaymentOutboxItem(
            outboxId = getUUID("outbox_id"),
            paymentId = getUUID("payment_id"),
            providerId = getString("provider_id"),
            eventType = getString("event_type")
        )
    }

    private fun <T> Iterable<T>.firstOrNull(): T? = iterator().let { if (it.hasNext()) it.next() else null }
}
