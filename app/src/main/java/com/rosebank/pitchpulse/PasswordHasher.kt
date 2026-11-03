package com.rosebank.pitchpulse

import java.security.MessageDigest
import java.security.SecureRandom

/** One-way SHA-256. The plain password is never written to the account document. */
object PasswordHasher {
    fun salt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(password: String, salt: String, expected: String): Boolean {
        val actual = hash(password, salt)
        if (actual.length != expected.length) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].code xor expected[i].code)
        return diff == 0
    }
}
