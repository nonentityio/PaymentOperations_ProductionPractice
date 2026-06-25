package org.eltech.application.usecase

import io.vertx.core.Future
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.`in`.CancelPaymentUseCase
import org.eltech.application.port.`in`.CreatePaymentUseCase
import org.eltech.application.port.`in`.GetPaymentUseCase
import org.eltech.application.port.`in`.ListPaymentsUseCase
import org.eltech.application.port.`in`.ProcessPaymentUseCase
import org.eltech.application.port.out.PaymentEventPublisher
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentDetails
import org.eltech.domain.model.PaymentStatus
import org.eltech.infrastructure.validation.NativePaymentValidator
import org.eltech.infrastructure.validation.ServiceRequisiteValidator
import java.math.RoundingMode
import java.util.UUID

class PaymentService(
    private val repository: PaymentRepositoryPort,
    private val eventPublisher: PaymentEventPublisher
) : CreatePaymentUseCase, GetPaymentUseCase, ListPaymentsUseCase, ProcessPaymentUseCase, CancelPaymentUseCase {
    override fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult> {
        val amountMinor = try {
            command.amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (error: ArithmeticException) {
            return Future.failedFuture(IllegalArgumentException("amount must have no more than two decimal places"))
        }

        val serviceValidation = ServiceRequisiteValidator.validate(
            command.serviceCategory,
            command.serviceId,
            command.requisite,
            command.amount
        )
        if (!serviceValidation.valid) {
            return Future.failedFuture(IllegalArgumentException(serviceValidation.message))
        }

        val validation = NativePaymentValidator.validate(
            command.requisite,
            command.providerId,
            command.currency,
            amountMinor
        )
        if (!validation.valid()) {
            return Future.failedFuture(IllegalArgumentException(validation.message()))
        }

        return repository.createPayment(command)
            .onSuccess { result ->
                if (!result.idempotentReplay) {
                    eventPublisher.publishPaymentCreated(result)
                }
            }
    }

    override fun getPayment(paymentId: UUID): Future<PaymentDetails> {
        return repository.fetchPayment(paymentId).compose { payment ->
            repository.fetchHistory(paymentId).map { history ->
                PaymentDetails(payment, history)
            }
        }
    }

    override fun listPayments(clientId: String?): Future<List<Payment>> {
        return repository.listPayments(clientId)
    }

    override fun processPayment(paymentId: UUID, providerId: String): Future<Void> {
        return repository.updateStatus(paymentId, PaymentStatus.CHECK_REQUISITE, null)
            .compose { repository.addAttempt(paymentId, providerId, 1, "STARTED", null) }
    }

    override fun cancelPayment(paymentId: UUID): Future<Void> {
        return repository.fetchPayment(paymentId).compose { payment ->
            if (payment.status == PaymentStatus.SUCCESS || payment.status == PaymentStatus.FAILED) {
                Future.failedFuture(IllegalArgumentException("finished payment cannot be cancelled"))
            } else {
                repository.updateStatus(paymentId, PaymentStatus.CANCELLED, "Cancelled by client")
            }
        }
    }
}
