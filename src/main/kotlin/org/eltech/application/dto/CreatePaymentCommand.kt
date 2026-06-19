package org.eltech.application.dto

import java.math.BigDecimal

data class CreatePaymentCommand(
    val clientId: String,
    val providerId: String,
    val amount: BigDecimal,
    val currency: String,
    val requisite: String,
    val idempotencyKey: String,
    val requestHash: String
)

