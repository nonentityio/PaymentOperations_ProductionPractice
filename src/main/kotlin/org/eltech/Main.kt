package org.eltech

import io.vertx.core.Vertx
import org.eltech.app.PaymentApplicationVerticle

fun main() {
    val vertx = Vertx.vertx()
    vertx.deployVerticle(PaymentApplicationVerticle())
        .onSuccess { println("PaymentOperations backend started") }
        .onFailure { it.printStackTrace() }
}
