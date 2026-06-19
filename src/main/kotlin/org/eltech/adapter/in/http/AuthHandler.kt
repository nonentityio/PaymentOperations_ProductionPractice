package org.eltech.adapter.`in`.http

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext

class AuthHandler {
    fun requireBearerToken(ctx: RoutingContext) {
        val token = ctx.request().getHeader(HttpHeaders.AUTHORIZATION)
        if (token == "Bearer demo-token") ctx.next()
        else fail(ctx, 401, "Authorization: Bearer demo-token is required for demo")
    }
}

