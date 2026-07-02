package org.eltech.adapter.`in`.eventbus

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.infrastructure.vertx.EventBusAddresses
import org.eltech.util.envInt

class PaymentOutboxWorker(
    private val vertx: Vertx,
    private val repository: PaymentRepositoryPort,
    private val batchSize: Int = envInt("PAYMENT_OUTBOX_BATCH_SIZE", 64),
    private val intervalMs: Long = envInt("PAYMENT_OUTBOX_INTERVAL_MS", 50).toLong()
) {
    private var running = false

    fun start() {
        vertx.setPeriodic(intervalMs) { drain() }
        drain()
    }

    private fun drain() {
        if (running) return
        running = true

        repository.releaseStalePaymentOutbox(30)
            .onFailure { running = false }
            .onSuccess {
                repository.claimPaymentOutboxBatch(batchSize)
                    .onFailure { running = false }
                    .onSuccess { items ->
                        if (items.isEmpty()) {
                            running = false
                            return@onSuccess
                        }

                        val marks = items.map { item ->
                            vertx.eventBus().publish(
                                EventBusAddresses.PAYMENT_CREATED,
                                JsonObject()
                                    .put("paymentId", item.paymentId.toString())
                                    .put("providerId", item.providerId)
                            )
                            repository.markPaymentOutboxDone(item.outboxId)
                        }
                        Future.all(marks).onComplete { running = false }
                    }
            }
    }
}
