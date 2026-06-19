package org.eltech.domain.model

data class PaymentDetails(
    val payment: Payment,
    val history: List<PaymentStatusChange>
)

