package org.eltech.application.port.out

import org.eltech.application.dto.CreatePaymentResult

interface PaymentEventPublisher {
    fun publishPaymentCreated(result: CreatePaymentResult)
}

