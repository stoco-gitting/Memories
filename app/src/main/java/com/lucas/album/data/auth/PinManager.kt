package com.lucas.album.data.auth

import com.lucas.album.data.prefs.AppPreferences
import java.security.MessageDigest

class PinManager(private val preferences: AppPreferences) {

    suspend fun setUpFixedPin() {
        if (preferences.pinHash() == null) {
            preferences.setPinHash(hash(FIXED_PIN))
        }
    }

    suspend fun verify(candidate: String): Boolean {
        val storedHash = preferences.pinHash() ?: return false
        return MessageDigest.isEqual(hash(candidate).toByteArray(), storedHash.toByteArray())
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PIN_LENGTH = 6
        private const val FIXED_PIN = "271024"
        private const val SALT = "album-v1-"
    }
}
