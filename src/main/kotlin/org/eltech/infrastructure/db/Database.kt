package org.eltech.infrastructure.db

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import org.eltech.infrastructure.config.PgConnectOptionsFactory
import org.eltech.util.envInt
import java.nio.charset.StandardCharsets

object Database {
    fun createPool(vertx: Vertx): Pool {
        return PgBuilder.pool()
            .connectingTo(PgConnectOptionsFactory.fromEnv())
            .with(
                PoolOptions()
                    .setMaxSize(defaultPoolSize())
                    .setMaxWaitQueueSize(envInt("PG_WAIT_QUEUE_SIZE", 1024))
            )
            .using(vertx)
            .build()
    }

    fun applySchema(pool: Pool): Future<Void> {
        return pool.query(loadSchemaSql()).execute().mapEmpty()
    }

    private fun loadSchemaSql(): String {
        val stream = Database::class.java.getResourceAsStream("/db/schema.sql")
            ?: error("db/schema.sql resource was not found")
        return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    private fun defaultPoolSize(): Int {
        val fallback = if (System.getenv("DYNO") != null) 8 else 16
        return envInt("PG_POOL_SIZE", fallback)
    }
}
