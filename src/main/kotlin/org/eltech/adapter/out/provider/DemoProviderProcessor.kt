package org.eltech.adapter.out.provider

import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.PaymentStatus
import java.util.UUID

class DemoProviderProcessor(
    private val vertx: Vertx,
    private val repository: PaymentRepositoryPort
) {
    fun processPayment(message: Message<JsonObject>) {
        val paymentId = UUID.fromString(message.body().getString("paymentId"))
        val providerId = message.body().getString("providerId")

        repository.updateStatus(paymentId, PaymentStatus.CHECK_REQUISITE, null)
            .compose { repository.addAttempt(paymentId, providerId, 1, "STARTED", null) }
            .onSuccess {
                vertx.setTimer(250) {
                    repository.fetchPayment(paymentId).onSuccess { payment ->
                        if (payment.status == PaymentStatus.CANCELLED) {
                            return@onSuccess
                        }

                        if (payment.requisite.contains("HOLD", ignoreCase = true)) {
                            holdPaymentBeforeProvider(paymentId, providerId)
                        } else if (payment.requisite.startsWith("BAD", ignoreCase = true)) {
                            rejectInvalidRequisite(paymentId, providerId)
                        } else if (payment.requisite.contains("TIMEOUT", ignoreCase = true)) {
                            confirmPayment(paymentId).onSuccess {
                                simulateRetry(paymentId, providerId, 1)
                            }
                        } else {
                            confirmPayment(paymentId).onSuccess {
                                acceptPayment(paymentId, providerId)
                            }
                        }
                    }
                }
            }
    }

    private fun confirmPayment(paymentId: UUID) =
        repository.updateStatus(paymentId, PaymentStatus.CONFIRMED, null)

    private fun holdPaymentBeforeProvider(paymentId: UUID, providerId: String) {
        vertx.setTimer(30_000) {
            repository.fetchPayment(paymentId).onSuccess { payment ->
                if (payment.status != PaymentStatus.CANCELLED) {
                    confirmPayment(paymentId).onSuccess {
                        acceptPayment(paymentId, providerId)
                    }
                }
            }
        }
    }

    private fun rejectInvalidRequisite(paymentId: UUID, providerId: String) {
        repository.addProviderResponse(paymentId, providerId, 422, "REQUISITE_INVALID")
            .compose { repository.updateStatus(paymentId, PaymentStatus.FAILED, "Invalid requisite") }
    }

    private fun acceptPayment(paymentId: UUID, providerId: String) {
        repository.updateStatus(paymentId, PaymentStatus.PROCESSING, null)
            .compose { repository.addProviderResponse(paymentId, providerId, 200, "ACCEPTED") }
            .onSuccess {
                vertx.setTimer(350) {
                    repository.updateStatus(paymentId, PaymentStatus.SUCCESS, null)
                }
            }
    }

    private fun simulateRetry(paymentId: UUID, providerId: String, attempt: Int) {
        repository.updateStatus(paymentId, PaymentStatus.PROCESSING, "Provider timeout, retry scheduled")
            .compose { repository.addProviderResponse(paymentId, providerId, 504, "TIMEOUT") }
            .onSuccess {
                if (attempt >= 3) {
                    vertx.setTimer(300) {
                        repository.updateStatus(paymentId, PaymentStatus.FAILED, "Provider timeout after retries")
                    }
                } else {
                    vertx.setTimer(600) {
                        repository.addAttempt(paymentId, providerId, attempt + 1, "RETRY", "TIMEOUT")
                            .onSuccess { simulateRetry(paymentId, providerId, attempt + 1) }
                    }
                }
            }
    }
}
