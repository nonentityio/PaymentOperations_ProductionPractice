package org.eltech.adapter.`in`.http

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.eltech.application.dto.CreatePaymentCommand
import org.eltech.infrastructure.routing.NativePaymentRouter
import org.eltech.infrastructure.security.PaymentRequestFingerprint
import java.math.BigDecimal
import java.math.RoundingMode

data class CreatePaymentHttpRequest(
    val clientId: String,
    val providerId: String,
    val amount: BigDecimal,
    val currency: String,
    val requisite: String,
    val idempotencyKey: String
) {
    fun toCommand(): CreatePaymentCommand {
        val amountMinor = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        val routing = NativePaymentRouter.route(requisite, providerId, currency, amountMinor)
        val effectiveProviderId = routing.providerId()
        val amountText = amount.toPlainString()
        return CreatePaymentCommand(
            clientId = clientId,
            providerId = effectiveProviderId,
            amount = amount,
            currency = currency,
            requisite = requisite,
            idempotencyKey = idempotencyKey,
            requestHash = PaymentRequestFingerprint.hash(clientId, amountText, currency, requisite, effectiveProviderId)
        )
    }

    companion object {
        fun from(ctx: RoutingContext): CreatePaymentHttpRequest {
            val body = ctx.body().asJsonObject() ?: JsonObject()
            val clientId = body.getString("clientId")?.trim().orEmpty()
            val currency = body.getString("currency")?.uppercase()?.trim().orEmpty()
            val requisite = body.getString("requisite")?.trim().orEmpty()
            var providerId = body.getString("providerId")?.trim().orEmpty()
            if (providerId.isBlank()) {
                providerId = "demo-provider"
            }
            val amount = parseAmount(body)
            val idempotencyKey = ctx.request().getHeader("Idempotency-Key")?.trim().orEmpty()

            if (clientId.isBlank() || currency.isBlank() || requisite.isBlank() || amount == null) {
                throw IllegalArgumentException("clientId, amount, currency and requisite are required")
            }
            if (currency.length != 3) {
                throw IllegalArgumentException("currency must be ISO-4217 code, for example KGS or USD")
            }
            if (idempotencyKey.isBlank()) {
                throw IllegalArgumentException("Idempotency-Key header is required")
            }

            return CreatePaymentHttpRequest(clientId, providerId, amount, currency, requisite, idempotencyKey)
        }

        private fun parseAmount(body: JsonObject): BigDecimal? {
            val raw = body.getValue("amount")
            val amount = if (raw is Number) {
                BigDecimal(raw.toString())
            } else if (raw is String) {
                raw.toBigDecimalOrNull()
            } else {
                null
            }

            if (amount == null || amount <= BigDecimal.ZERO) {
                return null
            }
            return amount
        }
    }
}
