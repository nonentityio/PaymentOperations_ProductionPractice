package org.eltech.util

fun envInt(name: String, default: Int): Int =
    System.getenv(name)?.toIntOrNull() ?: default

