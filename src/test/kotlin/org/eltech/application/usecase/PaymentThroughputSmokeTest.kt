package org.eltech.application.usecase

import io.vertx.core.Future
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.out.PaymentEventPublisher
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentOutboxItem
import org.eltech.domain.model.PaymentStatus
import org.eltech.domain.model.PaymentStatusChange
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentThroughputSmokeTest {
    @Test
    fun serviceAcceptsOneThousandPaymentsAboveTargetRps() {
        val repository = FastRepository()
        val service = PaymentService(repository, NoopLoadPublisher)
        val total = 1000

        val elapsedMs = measureTimeMillis {
            repeat(total) { index ->
                val result = service.createPayment(command(index))
                assertTrue(result.succeeded(), result.cause()?.message)
            }
        }

        val rps = total * 1000.0 / elapsedMs.coerceAtLeast(1)
        println("payment service smoke throughput: total=$total elapsedMs=$elapsedMs rps=$rps")

        assertEquals(total, repository.created)
        assertTrue(rps >= 70.0, "Expected at least 70 RPS, actual RPS was $rps")
    }

    private fun command(index: Int): CreatePaymentCommand {
        return CreatePaymentCommand(
            clientId = "eldik-test-bank",
            providerId = "eldik2-test-bank",
            serviceCategory = "TRANSFER",
            serviceId = "transfer.internal",
            amount = BigDecimal("10.00"),
            currency = "KGS",
            requisite = "ELDIK2-996700333444",
            idempotencyKey = "load-$index",
            requestHash = "hash-$index"
        )
    }
}

private object NoopLoadPublisher : PaymentEventPublisher {
    override fun publishPaymentCreated(result: CreatePaymentResult) = Unit
}

private class FastRepository : PaymentRepositoryPort {
    var created = 0

    override fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult> {
        created++
        return Future.succeededFuture(
            CreatePaymentResult(
                paymentId = UUID.randomUUID(),
                clientId = command.clientId,
                providerId = command.providerId,
                serviceCategory = command.serviceCategory,
                amount = command.amount,
                currency = command.currency,
                status = PaymentStatus.CREATED,
                idempotentReplay = false
            )
        )
    }

    override fun updateStatus(paymentId: UUID, newStatus: PaymentStatus, failureReason: String?): Future<Void> =
        Future.succeededFuture()

    override fun addAttempt(paymentId: UUID, providerId: String, attemptNumber: Int, status: String, errorCode: String?): Future<Void> =
        Future.succeededFuture()

    override fun addProviderResponse(paymentId: UUID, providerId: String, responseCode: Int, responseStatus: String): Future<Void> =
        Future.succeededFuture()

    override fun fetchPayment(paymentId: UUID): Future<Payment> {
        val now = OffsetDateTime.now()
        return Future.succeededFuture(
            Payment(
                paymentId = paymentId,
                clientId = "eldik-test-bank",
                providerId = "eldik2-test-bank",
                serviceCategory = "TRANSFER",
                amount = BigDecimal("10.00"),
                currency = "KGS",
                requisite = "ELDIK2-996700333444",
                status = PaymentStatus.CREATED,
                failureReason = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override fun fetchHistory(paymentId: UUID): Future<List<PaymentStatusChange>> =
        Future.succeededFuture(emptyList())

    override fun listPayments(clientId: String?): Future<List<Payment>> =
        Future.succeededFuture(emptyList())

    override fun claimPaymentOutboxBatch(limit: Int): Future<List<PaymentOutboxItem>> =
        Future.succeededFuture(emptyList())

    override fun markPaymentOutboxDone(outboxId: UUID): Future<Void> =
        Future.succeededFuture()

    override fun releaseStalePaymentOutbox(afterSeconds: Int): Future<Int> =
        Future.succeededFuture(0)
}
