package org.eltech.adapter.`in`.http

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.sqlclient.Pool
import org.eltech.infrastructure.routing.NativePaymentRouter
import org.eltech.infrastructure.validation.NativePaymentValidator

class HealthRoutes(private val db: Pool) {
    fun mount(router: Router) {
        router.get("/health").handler { ctx ->
            db.query("select 1").execute()
                .onSuccess {
                    ok(
                        ctx,
                        JsonObject()
                            .put("status", "UP")
                            .put("paymentValidationEngine", if (NativePaymentValidator.isNativeAvailable()) "native-c" else "jvm-fallback")
                            .put("paymentRoutingEngine", if (NativePaymentRouter.isNativeAvailable()) "native-cpp" else "jvm-fallback")
                    )
                }
                .onFailure { fail(ctx, 503, it.message ?: "database unavailable") }
        }
    }
}
