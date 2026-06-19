package org.eltech.application.port.`in`

import io.vertx.core.Future
import java.util.UUID

interface CancelPaymentUseCase {
    fun cancelPayment(paymentId: UUID): Future<Void>
}
