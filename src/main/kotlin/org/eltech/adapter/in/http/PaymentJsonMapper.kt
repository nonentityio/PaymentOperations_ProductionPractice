package org.eltech.adapter.`in`.http

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.eltech.application.dto.CreatePaymentResult
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentDetails

fun createPaymentResultToJson(result: CreatePaymentResult): JsonObject {
    val json = JsonObject()
        .put("paymentId", result.paymentId.toString())
        .put("status", result.status.name)
        .put("idempotentReplay", result.idempotentReplay)

    result.clientId?.let { json.put("clientId", it) }
    result.providerId?.let { json.put("providerId", it) }
    result.serviceCategory?.let { json.put("serviceCategory", it) }
    result.amount?.let { json.put("amount", it.toPlainString()) }
    result.currency?.let { json.put("currency", it) }

    return json
}

fun paymentToJson(payment: Payment): JsonObject {
    return JsonObject()
        .put("paymentId", payment.paymentId.toString())
        .put("clientId", payment.clientId)
        .put("providerId", payment.providerId)
        .put("serviceCategory", payment.serviceCategory)
        .put("amount", payment.amount.toPlainString())
        .put("currency", payment.currency)
        .put("requisite", payment.requisite)
        .put("status", payment.status.name)
        .put("failureReason", payment.failureReason)
        .put("createdAt", payment.createdAt.toString())
        .put("updatedAt", payment.updatedAt.toString())
}

fun paymentDetailsToJson(details: PaymentDetails): JsonObject {
    return paymentToJson(details.payment)
        .put(
            "history",
            JsonArray(details.history.map { change ->
                JsonObject()
                    .put("oldStatus", change.oldStatus?.name)
                    .put("newStatus", change.newStatus.name)
                    .put("changedAt", change.changedAt.toString())
            })
        )
}
