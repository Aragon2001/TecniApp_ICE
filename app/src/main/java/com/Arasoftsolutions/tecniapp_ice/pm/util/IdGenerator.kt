package com.Arasoftsolutions.tecniapp_ice.pm.util

import java.security.MessageDigest

object IdGenerator {
    fun deterministicId(vararg parts: String): String {
        val normalized = parts.joinToString("|") { it.trim().lowercase() }
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
}
