package org.eltech.application.dto

import java.util.UUID

data class ProviderResponseCommand(
    val paymentId: UUID,
    val providerId: String,
    val responseCode: Int,
    val responseStatus: String
)

