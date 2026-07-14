package com.lucas.album.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "album_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val HAS_ANSWERED_PROPOSAL = booleanPreferencesKey("has_answered_proposal")
        val PIN_HASH = stringPreferencesKey("pin_hash")
    }

    suspend fun hasAnsweredProposal(): Boolean =
        context.dataStore.data.first()[Keys.HAS_ANSWERED_PROPOSAL] ?: false

    suspend fun setHasAnsweredProposal(value: Boolean) {
        context.dataStore.edit { it[Keys.HAS_ANSWERED_PROPOSAL] = value }
    }

    suspend fun pinHash(): String? =
        context.dataStore.data.first()[Keys.PIN_HASH]

    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { it[Keys.PIN_HASH] = hash }
    }
}
