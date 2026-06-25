package org.eltech.infrastructure.validation

import java.math.BigDecimal

data class ServiceValidationResult(
    val valid: Boolean,
    val message: String
)

object ServiceRequisiteValidator {
    private val services = listOf(
        PaymentServiceRule("transfer.internal", "TRANSFER", Regex("^[A-Za-z0-9 -]{6,34}$"), null),
        PaymentServiceRule("mobile.topup", "MOBILE_TOPUP", Regex("^996[0-9]{9}$"), BigDecimal("100000.00")),
        PaymentServiceRule("mobile.operator", "MOBILE_TOPUP", Regex("^996[0-9]{9}$"), BigDecimal("100000.00")),
        PaymentServiceRule("mobile.plus", "MOBILE_TOPUP", Regex("^996[0-9]{9}$"), BigDecimal("100000.00")),
        PaymentServiceRule("mobile.lite", "MOBILE_TOPUP", Regex("^996[0-9]{9}$"), BigDecimal("100000.00")),
        PaymentServiceRule("utility.electricity", "UTILITY", Regex("^EL-[0-9]{6,12}$"), null),
        PaymentServiceRule("utility.water", "UTILITY", Regex("^WATER-[0-9]{6,12}$"), null),
        PaymentServiceRule("utility.gas", "UTILITY", Regex("^GAS-[0-9]{6,12}$"), null),
        PaymentServiceRule("internet.home", "UTILITY", Regex("^NET-[0-9]{6,12}$"), null),
        PaymentServiceRule("card.repayment", "CARD_PAYMENT", Regex("^(?:[0-9]{16}|[0-9]{4} [0-9]{4} [0-9]{4} [0-9]{4})$"), null),
        PaymentServiceRule("wallet.topup", "WALLET", Regex("^WAL-[A-Za-z0-9]{6,20}$"), BigDecimal("100000.00"))
    ).associateBy { it.serviceId }

    fun defaultServiceId(category: String): String {
        return when (category.uppercase()) {
            "MOBILE_TOPUP" -> "mobile.operator"
            "UTILITY" -> "utility.electricity"
            "CARD_PAYMENT" -> "card.repayment"
            "WALLET" -> "wallet.topup"
            else -> "transfer.internal"
        }
    }

    fun validate(category: String, serviceId: String, requisite: String, amount: BigDecimal): ServiceValidationResult {
        val normalizedCategory = category.uppercase()
        val rule = services[serviceId] ?: return ServiceValidationResult(false, "unknown serviceId: $serviceId")

        if (rule.category != normalizedCategory) {
            return ServiceValidationResult(false, "serviceId $serviceId does not match category $normalizedCategory")
        }
        if (!rule.requisitePattern.matches(requisite)) {
            return ServiceValidationResult(false, "requisite does not match service $serviceId format")
        }
        if (rule.maxAmount != null && amount > rule.maxAmount) {
            return ServiceValidationResult(false, "amount exceeds ${rule.maxAmount} limit for service $serviceId")
        }

        return ServiceValidationResult(true, "service requisite validation passed")
    }

    private data class PaymentServiceRule(
        val serviceId: String,
        val category: String,
        val requisitePattern: Regex,
        val maxAmount: BigDecimal?
    )
}
