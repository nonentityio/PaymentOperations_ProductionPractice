package org.eltech.adapter.`in`.http

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext

class AuthHandler(
    private val expectedToken: String
) {
    fun requireBearerToken(ctx: RoutingContext) {
        val token = ctx.request().getHeader(HttpHeaders.AUTHORIZATION)
        if (token == "Bearer $expectedToken") ctx.next()
        else fail(ctx, 401, "valid bearer token is required")
    }
}
