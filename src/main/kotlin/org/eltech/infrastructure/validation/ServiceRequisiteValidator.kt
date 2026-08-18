package org.eltech.infrastructure.validation

import java.math.BigDecimal

data class ServiceValidationResult(
    val valid: Boolean,
    val message: String
)

object ServiceRequisiteValidator {
    private val services = listOf(
        PaymentServiceRule("transfer.internal", "TRANSFER", RequisiteKind.Transfer),
        PaymentServiceRule("mobile.topup", "MOBILE_TOPUP", RequisiteKind.Mobile, BigDecimal("100000.00")),
        PaymentServiceRule("mobile.operator", "MOBILE_TOPUP", RequisiteKind.Mobile, BigDecimal("100000.00")),
        PaymentServiceRule("mobile.plus", "MOBILE_TOPUP", RequisiteKind.Mobile, BigDecimal("100000.00")),
        PaymentServiceRule("mobile.lite", "MOBILE_TOPUP", RequisiteKind.Mobile, BigDecimal("100000.00")),
        PaymentServiceRule("utility.electricity", "UTILITY", RequisiteKind.PrefixedDigits("EL")),
        PaymentServiceRule("utility.water", "UTILITY", RequisiteKind.PrefixedDigits("WATER")),
        PaymentServiceRule("utility.gas", "UTILITY", RequisiteKind.PrefixedDigits("GAS")),
        PaymentServiceRule("utility.heating", "UTILITY", RequisiteKind.PrefixedDigits("HEAT")),
        PaymentServiceRule("utility.trash", "UTILITY", RequisiteKind.PrefixedDigits("TRASH")),
        PaymentServiceRule("utility.rent", "UTILITY", RequisiteKind.PrefixedDigits("RENT")),
        PaymentServiceRule("utility.general", "UTILITY", RequisiteKind.PrefixedDigits("UTIL")),
        PaymentServiceRule("internet.home", "UTILITY", RequisiteKind.PrefixedDigits("NET")),
        PaymentServiceRule("internet.failed", "UTILITY", RequisiteKind.PrefixedDigits("NET-FAIL")),
        PaymentServiceRule("card.repayment", "CARD_PAYMENT", RequisiteKind.Card),
        PaymentServiceRule("wallet.topup", "WALLET", RequisiteKind.Wallet, BigDecimal("100000.00"))
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
        if (!rule.kind.accepts(requisite)) {
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
        val kind: RequisiteKind,
        val maxAmount: BigDecimal? = null
    )

    private sealed interface RequisiteKind {
        fun accepts(value: String): Boolean

        data object Mobile : RequisiteKind {
            override fun accepts(value: String) = value.length == 12 && value.startsWith("996") && value.all(Char::isDigit)
        }

        data class PrefixedDigits(private val prefix: String) : RequisiteKind {
            override fun accepts(value: String): Boolean {
                val marker = "$prefix-"
                if (!value.startsWith(marker)) return false
                val payload = value.substring(marker.length)
                return payload.length in 6..12 && payload.all(Char::isDigit)
            }
        }

        data object Card : RequisiteKind {
            override fun accepts(value: String) = value.length == 16 && value.all(Char::isDigit)
        }

        data object Wallet : RequisiteKind {
            override fun accepts(value: String): Boolean {
                if (!value.startsWith("WAL-")) return false
                val payload = value.substring(4)
                return payload.length in 6..20 && payload.all(Char::isLetterOrDigit)
            }
        }

        data object Transfer : RequisiteKind {
            override fun accepts(value: String) = value.length in 6..34 && value.all { it.isLetterOrDigit() || it == '-' || it == ' ' }
        }
    }
}
