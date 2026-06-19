package org.eltech.infrastructure.db

import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import org.eltech.infrastructure.config.PgConnectOptionsFactory
import org.eltech.util.envInt

object Database {
    fun createPool(vertx: Vertx): Pool {
        return PgBuilder.pool()
            .connectingTo(PgConnectOptionsFactory.fromEnv())
            .with(PoolOptions().setMaxSize(envInt("PG_POOL_SIZE", 16)))
            .using(vertx)
            .build()
    }
}
