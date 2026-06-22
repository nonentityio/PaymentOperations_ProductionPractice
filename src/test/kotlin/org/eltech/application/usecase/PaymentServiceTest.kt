package org.eltech.application.usecase

import io.vertx.core.Future
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.out.PaymentEventPublisher
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentStatus
import org.eltech.domain.model.PaymentStatusChange
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentServiceTest {
    @Test
    fun cancelMovesUnfinishedPaymentToCancelled() {
        val id = UUID.randomUUID()
        val repository = FakePaymentRepository(payment(id, PaymentStatus.CREATED))
        val service = PaymentService(repository, NoopPublisher)

        val result = service.cancelPayment(id)

        assertTrue(result.succeeded())
        assertEquals(PaymentStatus.CANCELLED, repository.lastStatus)
        assertEquals("Cancelled by client", repository.lastFailureReason)
    }

    @Test
    fun cancelRejectsFinishedPayment() {
        val id = UUID.randomUUID()
        val repository = FakePaymentRepository(payment(id, PaymentStatus.SUCCESS))
        val service = PaymentService(repository, NoopPublisher)

        val result = service.cancelPayment(id)

        assertTrue(result.failed())
        assertFalse(repository.statusWasUpdated)
    }

    @Test
    fun processPaymentWritesFirstProviderAttempt() {
        val id = UUID.randomUUID()
        val repository = FakePaymentRepository(payment(id, PaymentStatus.CREATED))
        val service = PaymentService(repository, NoopPublisher)

        val result = service.processPayment(id, "eldik2-test-bank")

        assertTrue(result.succeeded())
        assertEquals(PaymentStatus.CHECK_REQUISITE, repository.lastStatus)
        assertEquals("eldik2-test-bank", repository.lastAttemptProvider)
        assertEquals("STARTED", repository.lastAttemptStatus)
    }

    private fun payment(id: UUID, status: PaymentStatus): Payment {
        val now = OffsetDateTime.now()
        return Payment(
            paymentId = id,
            clientId = "eldik-test-bank",
            providerId = "eldik2-test-bank",
            serviceCategory = "TRANSFER",
            amount = BigDecimal("10.00"),
            currency = "KGS",
            requisite = "ELDIK2-996700333444",
            status = status,
            failureReason = null,
            createdAt = now,
            updatedAt = now
        )
    }
}

private object NoopPublisher : PaymentEventPublisher {
    override fun publishPaymentCreated(result: CreatePaymentResult) = Unit
}

private class FakePaymentRepository(private val payment: Payment) : PaymentRepositoryPort {
    var lastStatus: PaymentStatus? = null
    var lastFailureReason: String? = null
    var statusWasUpdated = false
    var lastAttemptProvider: String? = null
    var lastAttemptStatus: String? = null

    override fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult> {
        return Future.failedFuture("not used")
    }

    override fun updateStatus(paymentId: UUID, newStatus: PaymentStatus, failureReason: String?): Future<Void> {
        statusWasUpdated = true
        lastStatus = newStatus
        lastFailureReason = failureReason
        return Future.succeededFuture()
    }

    override fun addAttempt(paymentId: UUID, providerId: String, attemptNumber: Int, status: String, errorCode: String?): Future<Void> {
        lastAttemptProvider = providerId
        lastAttemptStatus = status
        return Future.succeededFuture()
    }

    override fun addProviderResponse(paymentId: UUID, providerId: String, responseCode: Int, responseStatus: String): Future<Void> {
        return Future.succeededFuture()
    }

    override fun fetchPayment(paymentId: UUID): Future<Payment> {
        return Future.succeededFuture(payment)
    }

    override fun fetchHistory(paymentId: UUID): Future<List<PaymentStatusChange>> {
        return Future.succeededFuture(emptyList())
    }

    override fun listPayments(clientId: String?): Future<List<Payment>> {
        return Future.succeededFuture(listOf(payment))
    }
}
