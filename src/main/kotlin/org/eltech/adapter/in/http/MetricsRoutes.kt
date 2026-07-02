package org.eltech.adapter.`in`.http

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.sqlclient.Pool
import org.eltech.util.firstOrNull

class MetricsRoutes(
    private val db: Pool,
    private val authHandler: AuthHandler
) {
    fun mount(router: Router) {
        router.get("/metrics/payments").handler(authHandler::requireBearerToken).handler { ctx ->
            db.query("select payment_operation_metrics() as metrics")
                .execute()
                .onSuccess { rows ->
                    val metrics = rows.firstOrNull()?.getJsonObject("metrics") ?: JsonObject()
                    ok(ctx, metrics)
                }
                .onFailure { fail(ctx, 503, "metrics unavailable") }
        }
    }
}
