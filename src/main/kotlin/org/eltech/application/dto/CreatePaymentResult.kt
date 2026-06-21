package org.eltech.application.dto

import org.eltech.domain.model.PaymentStatus
import java.math.BigDecimal
import java.util.UUID

data class CreatePaymentResult(
    val paymentId: UUID,
    val clientId: String?,
    val providerId: String?,
    val serviceCategory: String?,
    val amount: BigDecimal?,
    val currency: String?,
    val status: PaymentStatus,
    val idempotentReplay: Boolean
)
