package org.eltech.application.port.out

import io.vertx.core.Future
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentStatus
import org.eltech.domain.model.PaymentStatusChange
import java.util.UUID

interface PaymentRepositoryPort {
    fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult>
    fun updateStatus(paymentId: UUID, newStatus: PaymentStatus, failureReason: String?): Future<Void>
    fun addAttempt(paymentId: UUID, providerId: String, attemptNumber: Int, status: String, errorCode: String?): Future<Void>
    fun addProviderResponse(paymentId: UUID, providerId: String, responseCode: Int, responseStatus: String): Future<Void>
    fun fetchPayment(paymentId: UUID): Future<Payment>
    fun fetchHistory(paymentId: UUID): Future<List<PaymentStatusChange>>
    fun listPayments(clientId: String?): Future<List<Payment>>
}

