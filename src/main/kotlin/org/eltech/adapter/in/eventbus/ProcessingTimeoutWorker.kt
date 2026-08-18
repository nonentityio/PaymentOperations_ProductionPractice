package org.eltech.adapter.`in`.eventbus

import io.vertx.core.Vertx
import org.eltech.application.port.out.PaymentRepositoryPort
import org.eltech.util.envInt

class ProcessingTimeoutWorker(
    private val vertx: Vertx,
    private val repository: PaymentRepositoryPort,
    private val timeoutSeconds: Int = envInt("PAYMENT_PROCESSING_TIMEOUT_SECONDS", 30),
    private val intervalMs: Long = envInt("PAYMENT_TIMEOUT_SCAN_INTERVAL_MS", 5_000).toLong()
) {
    private var running = false

    fun start() {
        vertx.setPeriodic(intervalMs.coerceAtLeast(1_000)) { sweep() }
        sweep()
    }

    private fun sweep() {
        if (running) return
        running = true
        repository.failStaleProcessing(timeoutSeconds.coerceAtLeast(5))
            .onSuccess { expired ->
                if (expired > 0) println("expired $expired stale processing payment(s)")
            }
            .onFailure { error -> println("processing timeout sweep failed: ${error.message}") }
            .onComplete { running = false }
    }
}
