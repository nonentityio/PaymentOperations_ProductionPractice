package org.eltech.adapter.out.persistence

import io.vertx.core.Future
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.Tuple
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentStatus
import org.eltech.domain.model.PaymentStatusChange
import org.eltech.util.firstOrNull
import java.util.UUID

class PostgresPaymentRepository(private val db: Pool) : PaymentRepositoryPort {
    override fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult> {
        return db.preparedQuery(
            """
            select payment_id, client_id, provider_id, amount, currency, status, idempotent_replay
            from create_payment_request($1, $2, $3, $4::currency_code, $5, $6, $7)
            """.trimIndent()
        ).execute(
            Tuple.of(
                command.clientId,
                command.providerId,
                command.amount,
                command.currency,
                command.requisite,
                command.idempotencyKey,
                command.requestHash
            )
        ).map { rows ->
            val row = rows.firstOrNull() ?: error("payment was not returned")
            CreatePaymentResult(
                paymentId = row.getUUID("payment_id"),
                clientId = row.getString("client_id"),
                providerId = row.getString("provider_id"),
                amount = row.getBigDecimal("amount"),
                currency = row.getString("currency"),
                status = PaymentStatus.valueOf(row.getString("status")),
                idempotentReplay = row.getBoolean("idempotent_replay")
            )
        }.recover { error ->
            if (error.message?.contains("Idempotency-Key", ignoreCase = true) == true) {
                Future.failedFuture(IllegalArgumentException("Idempotency-Key already used with different payload"))
            } else {
                Future.failedFuture(error)
            }
        }
    }

    override fun updateStatus(paymentId: UUID, newStatus: PaymentStatus, failureReason: String?): Future<Void> {
        return db.preparedQuery("select change_payment_status($1, $2::payment_status, $3)")
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
        return db.preparedQuery(
            """
            select register_payment_attempt($1, $2, $3, $4::attempt_status, $5)
            """.trimIndent()
        ).execute(Tuple.of(paymentId, providerId, attemptNumber, status, errorCode)).mapEmpty()
    }

    override fun addProviderResponse(
        paymentId: UUID,
        providerId: String,
        responseCode: Int,
        responseStatus: String
    ): Future<Void> {
        return db.preparedQuery(
            """
            select register_provider_response($1, $2, $3::smallint, $4::provider_response_status)
            """.trimIndent()
        ).execute(Tuple.of(paymentId, providerId, responseCode, responseStatus)).mapEmpty()
    }

    override fun fetchPayment(paymentId: UUID): Future<Payment> {
        return db.preparedQuery(
            """
            select payment_id, client_id, provider_id, amount, currency, requisite, status, failure_reason, created_at, updated_at
            from get_payment($1)
            """.trimIndent()
        ).execute(Tuple.of(paymentId)).compose { rows ->
            val row = rows.firstOrNull()
            if (row == null) Future.failedFuture("payment not found")
            else Future.succeededFuture(paymentFromRow(row))
        }
    }

    override fun fetchHistory(paymentId: UUID): Future<List<PaymentStatusChange>> {
        return db.preparedQuery(
            """
            select old_status, new_status, changed_at
            from get_payment_history($1)
            """.trimIndent()
        ).execute(Tuple.of(paymentId)).map { rows ->
            rows.map { row ->
                PaymentStatusChange(
                    oldStatus = row.getString("old_status")?.let(PaymentStatus::valueOf),
                    newStatus = PaymentStatus.valueOf(row.getString("new_status")),
                    changedAt = row.getOffsetDateTime("changed_at")
                )
            }
        }
    }

    override fun listPayments(clientId: String?): Future<List<Payment>> {
        return db.preparedQuery(
            """
            select payment_id, client_id, provider_id, amount, currency, requisite, status, failure_reason, created_at, updated_at
            from list_payments($1, $2)
            """.trimIndent()
        ).execute(Tuple.of(clientId, 50)).map { rows -> rows.map(::paymentFromRow) }
    }

    private fun paymentFromRow(row: Row): Payment {
        return Payment(
            paymentId = row.getUUID("payment_id"),
            clientId = row.getString("client_id"),
            providerId = row.getString("provider_id"),
            amount = row.getBigDecimal("amount"),
            currency = row.getString("currency"),
            requisite = row.getString("requisite"),
            status = PaymentStatus.valueOf(row.getString("status")),
            failureReason = row.getString("failure_reason"),
            createdAt = row.getOffsetDateTime("created_at"),
            updatedAt = row.getOffsetDateTime("updated_at")
        )
    }
}
