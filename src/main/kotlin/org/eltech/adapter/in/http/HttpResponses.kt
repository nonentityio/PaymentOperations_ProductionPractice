package org.eltech.adapter.`in`.http

import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

fun ok(ctx: RoutingContext, payload: JsonObject) {
    ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end(payload.encode())
}

fun fail(ctx: RoutingContext, statusCode: Int, message: String) {
    ctx.response()
        .setStatusCode(statusCode)
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end(JsonObject().put("error", message).encode())
}

