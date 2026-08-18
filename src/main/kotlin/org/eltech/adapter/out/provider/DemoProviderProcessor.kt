package org.eltech.adapter.out.provider

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.domain.model.Payment
import org.eltech.domain.model.PaymentStatus
import org.eltech.util.envInt
import java.util.UUID

class DemoProviderProcessor(
    private val vertx: Vertx,
    private val repository: PaymentRepositoryPort,
    private val finalDelayMs: Long = envInt("DEMO_PROVIDER_FINAL_DELAY_MS", 2_000).toLong()
) {
    fun processPayment(message: Message<JsonObject>) {
        val body = message.body()
        val paymentId = runCatching { UUID.fromString(body.getString("paymentId")) }.getOrNull() ?: return
        val providerId = body.getString("providerId") ?: return

        repository.fetchPayment(paymentId)
            .compose { payment ->
                if (payment.status in terminalStatuses) {
                    Future.succeededFuture()
                } else {
                    repository.updateStatus(paymentId, PaymentStatus.CHECK_REQUISITE, null)
                        .compose { repository.addAttempt(paymentId, providerId, 1, "STARTED", null) }
                        .compose { repository.updateStatus(paymentId, PaymentStatus.CONFIRMED, null) }
                        .compose { repository.updateStatus(paymentId, PaymentStatus.PROCESSING, null) }
                        .compose { completeAfterDelay(paymentId, providerId) }
                }
            }
            .onFailure { error -> println("provider processing failed for $paymentId: ${error.message}") }
    }

    private fun completeAfterDelay(paymentId: UUID, providerId: String): Future<Void> {
        val promise = io.vertx.core.Promise.promise<Void>()
        vertx.setTimer(finalDelayMs.coerceAtLeast(0)) {
            repository.fetchPayment(paymentId)
                .compose { payment ->
                    if (payment.status in terminalStatuses) {
                        Future.succeededFuture()
                    } else {
                        completePayment(payment, providerId)
                    }
                }
                .onSuccess { promise.complete() }
                .onFailure(promise::fail)
        }
        return promise.future()
    }

    private fun completePayment(payment: Payment, providerId: String): Future<Void> {
        val failed = payment.requisite.startsWith("NET-FAIL-", ignoreCase = true) ||
            payment.requisite.startsWith("BAD", ignoreCase = true)

        return if (failed) {
            repository.addProviderResponse(payment.paymentId, providerId, 502, "ERROR")
                .compose { repository.addAttempt(payment.paymentId, providerId, 1, "FAILED", "DEMO_PROVIDER_ERROR") }
                .compose { repository.updateStatus(payment.paymentId, PaymentStatus.FAILED, "Demo provider rejected payment") }
        } else {
            repository.addProviderResponse(payment.paymentId, providerId, 200, "ACCEPTED")
                .compose { repository.addAttempt(payment.paymentId, providerId, 1, "SUCCESS", null) }
                .compose { repository.updateStatus(payment.paymentId, PaymentStatus.SUCCESS, null) }
        }
    }

    private companion object {
        val terminalStatuses = setOf(PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.CANCELLED)
    }
}
