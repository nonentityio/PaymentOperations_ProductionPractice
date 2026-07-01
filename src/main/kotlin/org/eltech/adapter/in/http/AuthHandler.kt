package org.eltech.adapter.`in`.http

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import org.eltech.infrastructure.security.SecureTokens

class AuthHandler(
    private val expectedToken: String?,
    private val expectedTokenHash: String? = null
) {
    fun requireBearerToken(ctx: RoutingContext) {
        val header = ctx.request().getHeader(HttpHeaders.AUTHORIZATION)
        if (SecureTokens.bearerMatches(header, expectedToken, expectedTokenHash)) ctx.next()
        else fail(ctx, 401, "valid bearer token is required")
    }
}
