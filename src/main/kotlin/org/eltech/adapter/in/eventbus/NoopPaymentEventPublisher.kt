package org.eltech.adapter.`in`.eventbus

import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.out.PaymentEventPublisher

object NoopPaymentEventPublisher : PaymentEventPublisher {
    override fun publishPaymentCreated(result: CreatePaymentResult) = Unit
}
