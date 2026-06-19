package org.eltech.infrastructure.config

import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.SslMode
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object PgConnectOptionsFactory {
    fun fromEnv(): PgConnectOptions {
        val databaseUrl = System.getenv("DATABASE_URL")
        if (!databaseUrl.isNullOrBlank()) {
            return fromDatabaseUrl(databaseUrl)
        }

        return PgConnectOptions()
            .setHost(System.getenv("PGHOST") ?: "localhost")
            .setPort(System.getenv("PGPORT")?.toIntOrNull() ?: 55432)
            .setDatabase(System.getenv("PGDATABASE") ?: "payment_ops")
            .setUser(System.getenv("PGUSER") ?: "payment")
            .setPassword(System.getenv("PGPASSWORD") ?: "payment")
            .setCachePreparedStatements(true)
            .setPipeliningLimit(32)
    }

    private fun fromDatabaseUrl(value: String): PgConnectOptions {
        val uri = URI(value)
        val userInfo = uri.rawUserInfo?.split(":", limit = 2).orEmpty()
        val query = uri.rawQuery.orEmpty()
        val sslRequired = query.contains("sslmode=require", ignoreCase = true) || System.getenv("DYNO") != null

        return PgConnectOptions()
            .setHost(uri.host)
            .setPort(if (uri.port > 0) uri.port else 5432)
            .setDatabase(uri.path.trimStart('/'))
            .setUser(userInfo.getOrNull(0)?.urlDecode().orEmpty())
            .setPassword(userInfo.getOrNull(1)?.urlDecode().orEmpty())
            .setSslMode(if (sslRequired) SslMode.REQUIRE else SslMode.DISABLE)
            .setCachePreparedStatements(true)
            .setPipeliningLimit(32)
    }

    private fun String.urlDecode(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8)
}
