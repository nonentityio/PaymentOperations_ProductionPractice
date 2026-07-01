package org.eltech.app

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.sqlclient.Pool
import org.eltech.adapter.`in`.eventbus.VertxPaymentEventPublisher
import org.eltech.adapter.`in`.http.AuthHandler
import org.eltech.adapter.`in`.http.HealthRoutes
import org.eltech.adapter.`in`.http.PaymentRoutes
import org.eltech.adapter.out.persistence.PostgresPaymentRepository
import org.eltech.adapter.out.provider.DemoProviderProcessor
import org.eltech.application.usecase.PaymentService
import org.eltech.infrastructure.db.Database
import org.eltech.infrastructure.routing.NativePaymentRouter
import org.eltech.infrastructure.validation.NativePaymentValidator
import org.eltech.infrastructure.vertx.EventBusAddresses
import org.eltech.util.envInt

class PaymentApplicationVerticle : AbstractVerticle() {
    private lateinit var db: Pool

    override fun start(startPromise: Promise<Void>) {
        requireNativeEngines()
        db = Database.createPool(vertx)
        val repository = PostgresPaymentRepository(db)
        val paymentService = PaymentService(repository, VertxPaymentEventPublisher(vertx))
        val processor = DemoProviderProcessor(vertx, repository)

        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PAYMENT_CREATED, processor::processPayment)
        startHttpServer(paymentService)
            .onSuccess { startPromise.complete() }
            .onFailure(startPromise::fail)
    }

    private fun startHttpServer(paymentService: PaymentService): Future<Void> {
        val router = Router.router(vertx)
        router.route().handler { ctx ->
            ctx.response()
                .putHeader("x-content-type-options", "nosniff")
                .putHeader("referrer-policy", "no-referrer")
                .putHeader("x-frame-options", "DENY")
                .putHeader("permissions-policy", "geolocation=(), camera=(), microphone=()")
            ctx.next()
        }
        router.route().handler(BodyHandler.create().setBodyLimit(16 * 1024))

        router.get("/").handler { ctx -> ctx.redirect("/admin/") }
        router.route("/admin/*").handler { ctx ->
            ctx.response()
                .putHeader("x-content-type-options", "nosniff")
                .putHeader("referrer-policy", "no-referrer")
                .putHeader("content-security-policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")
            ctx.next()
        }
        router.route("/admin/*").handler(
            StaticHandler.create("webroot/admin")
                .setIndexPage("index.html")
                .setDirectoryListing(false)
                .setIncludeHidden(false)
                .setCachingEnabled(false)
        )

        val authHandler = AuthHandler(apiToken(), apiTokenHash())
        HealthRoutes(db).mount(router)
        PaymentRoutes(paymentService, paymentService, paymentService, paymentService, authHandler).mount(router)

        val port = envInt("PORT", envInt("HTTP_PORT", 8080))
        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .map<Void> { null }
            .onSuccess { println("HTTP API listening on http://localhost:$port") }
    }

    private fun requireNativeEngines() {
        NativePaymentValidator.isNativeAvailable()
        NativePaymentRouter.isNativeAvailable()
    }

    private fun apiToken(): String? {
        return System.getenv("PAYMENT_API_TOKEN")
            ?: System.getenv("PAYMENT_SERVICE_TOKEN")
            ?: "local-dev-payment-token"
    }

    private fun apiTokenHash(): String? {
        return System.getenv("PAYMENT_API_TOKEN_SHA256")
            ?: System.getenv("PAYMENT_SERVICE_TOKEN_SHA256")
    }
}
