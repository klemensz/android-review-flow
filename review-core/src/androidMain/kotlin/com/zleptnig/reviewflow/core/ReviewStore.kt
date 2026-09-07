package com.zleptnig.reviewflow.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.reviewFlowDataStore by preferencesDataStore(name = "reviewflow_lib_preferences")

internal class ReviewStore(
    private val context: Context,
) : ReviewStateStore {
    private object Keys {
        val APP_STARTS = intPreferencesKey("app_starts")
        val SUCCESS_MOMENTS = intPreferencesKey("success_moments")
        val LAST_ATTEMPT_EPOCH_MS = longPreferencesKey("last_attempt_epoch_ms")
        val LAST_SHOWN_VERSION = stringPreferencesKey("last_shown_version")
    }

    override suspend fun read(): ReviewSnapshot {
        val prefs = context.reviewFlowDataStore.data.first()
        return prefs.toSnapshot()
    }

    override suspend fun update(transform: (ReviewSnapshot) -> ReviewSnapshot) {
        context.reviewFlowDataStore.edit { prefs ->
            val updated = transform(prefs.toSnapshot())
            prefs[Keys.APP_STARTS] = updated.appStarts
            prefs[Keys.SUCCESS_MOMENTS] = updated.successMoments
            prefs[Keys.LAST_ATTEMPT_EPOCH_MS] = updated.lastAttemptEpochMs
            val version = updated.lastRequestCompletedVersion
            if (version == null) {
                prefs.remove(Keys.LAST_SHOWN_VERSION)
            } else {
                prefs[Keys.LAST_SHOWN_VERSION] = version
            }
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toSnapshot(): ReviewSnapshot {
        return ReviewSnapshot(
            appStarts = this[Keys.APP_STARTS] ?: 0,
            successMoments = this[Keys.SUCCESS_MOMENTS] ?: 0,
            lastAttemptEpochMs = this[Keys.LAST_ATTEMPT_EPOCH_MS] ?: 0L,
            lastRequestCompletedVersion = this[Keys.LAST_SHOWN_VERSION],
        )
    }

}
