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
import kotlin.test.assertNotNull

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

    @Test
    fun createPaymentRejectsWrongServiceRequisiteBeforeRepositoryCall() {
        val repository = FakePaymentRepository(payment(UUID.randomUUID(), PaymentStatus.CREATED))
        val service = PaymentService(repository, NoopPublisher)

        val result = service.createPayment(
            CreatePaymentCommand(
                clientId = "merchant-network",
                providerId = "merchant-network",
                serviceCategory = "UTILITY",
                serviceId = "utility.electricity",
                amount = BigDecimal("250.00"),
                currency = "KGS",
                requisite = "WATER-12345678",
                idempotencyKey = "idem-utility-bad",
                requestHash = "hash"
            )
        )

        assertTrue(result.failed())
        assertFalse(repository.createWasCalled)
    }

    @Test
    fun createPaymentAcceptsServiceSpecificRequisiteAndPublishesEvent() {
        val repository = FakePaymentRepository(payment(UUID.randomUUID(), PaymentStatus.CREATED))
        val publisher = RecordingPublisher()
        val service = PaymentService(repository, publisher)

        val result = service.createPayment(
            CreatePaymentCommand(
                clientId = "merchant-network",
                providerId = "merchant-network",
                serviceCategory = "UTILITY",
                serviceId = "utility.electricity",
                amount = BigDecimal("250.00"),
                currency = "KGS",
                requisite = "EL-12345678",
                idempotencyKey = "idem-utility-good",
                requestHash = "hash"
            )
        )

        assertTrue(result.succeeded())
        assertTrue(repository.createWasCalled)
        assertNotNull(repository.createdCommand)
        assertEquals("utility.electricity", repository.createdCommand?.serviceId)
        assertEquals("UTILITY", repository.createdCommand?.serviceCategory)
        assertEquals(1, publisher.published)
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

private class RecordingPublisher : PaymentEventPublisher {
    var published = 0

    override fun publishPaymentCreated(result: CreatePaymentResult) {
        published++
    }
}

private class FakePaymentRepository(private val payment: Payment) : PaymentRepositoryPort {
    var lastStatus: PaymentStatus? = null
    var lastFailureReason: String? = null
    var statusWasUpdated = false
    var lastAttemptProvider: String? = null
    var lastAttemptStatus: String? = null
    var createWasCalled = false
    var createdCommand: CreatePaymentCommand? = null

    override fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult> {
        createWasCalled = true
        createdCommand = command
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
