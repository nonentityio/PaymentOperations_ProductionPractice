package org.eltech.util

import io.vertx.sqlclient.RowSet

fun <T> RowSet<T>.firstOrNull(): T? = iterator().asSequence().toList().firstOrNull()

