package org.eltech.infrastructure.security

import java.security.MessageDigest

object SecureTokens {
    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun bearerMatches(header: String?, expectedToken: String?, expectedTokenHash: String?): Boolean {
        val token = bearerToken(header) ?: return false
        val expectedHash = expectedTokenHash?.takeIf { it.isNotBlank() } ?: expectedToken?.let(::sha256Hex) ?: return false
        return constantTimeEquals(sha256Hex(token), expectedHash.lowercase())
    }

    private fun bearerToken(header: String?): String? {
        if (header == null) return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substringAfter(' ').trim().takeIf { it.isNotBlank() }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
    }
}
