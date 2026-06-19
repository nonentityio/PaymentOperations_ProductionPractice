package org.eltech.application.port.`in`

import io.vertx.core.Future
import org.eltech.domain.model.PaymentDetails
import java.util.UUID

interface GetPaymentUseCase {
    fun getPayment(paymentId: UUID): Future<PaymentDetails>
}

