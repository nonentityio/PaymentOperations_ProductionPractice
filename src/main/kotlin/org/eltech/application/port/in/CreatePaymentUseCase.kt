package org.eltech.application.port.`in`

import io.vertx.core.Future
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.application.dto.CreatePaymentResult

interface CreatePaymentUseCase {
    fun createPayment(command: CreatePaymentCommand): Future<CreatePaymentResult>
}

