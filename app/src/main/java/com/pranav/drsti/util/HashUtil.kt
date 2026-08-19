package com.pranav.drsti.util

import java.security.MessageDigest

/** SHA-256 hashing for inputHash/outputHash provenance fields (spec §37). */
object HashUtil {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
