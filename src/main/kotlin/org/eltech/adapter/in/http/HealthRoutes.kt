package org.eltech.adapter.`in`.http

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.sqlclient.Pool

class HealthRoutes(private val db: Pool) {
    fun mount(router: Router) {
        router.get("/health").handler { ctx ->
            db.query("select 1").execute()
                .onSuccess {
                    ok(
                        ctx,
                        JsonObject()
                            .put("status", "UP")
                            .put("paymentValidationEngine", "native-c")
                            .put("paymentRoutingEngine", "native-cpp")
                    )
                }
                .onFailure { fail(ctx, 503, it.message ?: "database unavailable") }
        }
    }
}
