package org.eltech.application.port.`in`

import io.vertx.core.Future
import org.eltech.domain.model.Payment

interface ListPaymentsUseCase {
    fun listPayments(clientId: String?): Future<List<Payment>>
}

