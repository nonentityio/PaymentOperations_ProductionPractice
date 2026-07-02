package org.eltech.domain.model

import java.util.UUID

data class PaymentOutboxItem(
    val outboxId: UUID,
    val paymentId: UUID,
    val providerId: String,
    val eventType: String
)
