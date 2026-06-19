package org.eltech.domain.model

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Payment(
    val paymentId: UUID,
    val clientId: String,
    val providerId: String,
    val amount: BigDecimal,
    val currency: String,
    val requisite: String,
    val status: PaymentStatus,
    val failureReason: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

