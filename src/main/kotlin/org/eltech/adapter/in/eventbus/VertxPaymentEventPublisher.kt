package org.eltech.adapter.`in`.eventbus

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.application.port.out.PaymentEventPublisher
import org.eltech.infrastructure.vertx.EventBusAddresses

class VertxPaymentEventPublisher(private val vertx: Vertx) : PaymentEventPublisher {
    override fun publishPaymentCreated(result: CreatePaymentResult) {
        vertx.eventBus().publish(
            EventBusAddresses.PAYMENT_CREATED,
            JsonObject()
                .put("paymentId", result.paymentId.toString())
                .put("providerId", result.providerId)
        )
    }
}
