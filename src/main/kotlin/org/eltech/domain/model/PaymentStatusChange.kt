package org.eltech.domain.model

import java.time.OffsetDateTime

data class PaymentStatusChange(
    val oldStatus: PaymentStatus?,
    val newStatus: PaymentStatus,
    val changedAt: OffsetDateTime
)

