package com.matrix.launcher.core

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN storage helper for the launcher-only vault. The PIN is never stored: only a random salt and an
 * iterated SHA-256 digest. This protects launcher shortcuts only - it is NOT Android's lock screen.
 */
object PinHasher {
    private const val ROUNDS = 20_000

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return toHex(b)
    }

    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var cur = (salt + ":" + pin).toByteArray(Charsets.UTF_8)
        repeat(ROUNDS) { cur = md.digest(cur) }
        return toHex(cur)
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        val actual = hash(pin, salt)
        return MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expectedHash.toByteArray(Charsets.UTF_8))
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 15])
        }
        return sb.toString()
    }
}
