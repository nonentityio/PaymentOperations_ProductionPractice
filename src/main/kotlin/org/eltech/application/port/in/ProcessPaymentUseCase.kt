package org.eltech.application.port.`in`

import io.vertx.core.Future
import java.util.UUID

interface ProcessPaymentUseCase {
    fun processPayment(paymentId: UUID, providerId: String): Future<Void>
}

