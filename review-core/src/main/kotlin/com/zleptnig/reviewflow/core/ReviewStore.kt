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
) {
    private object Keys {
        val APP_STARTS = intPreferencesKey("app_starts")
        val SUCCESS_MOMENTS = intPreferencesKey("success_moments")
        val LAST_ATTEMPT_EPOCH_MS = longPreferencesKey("last_attempt_epoch_ms")
        val LAST_SHOWN_VERSION = stringPreferencesKey("last_shown_version")
    }

    suspend fun incrementAppStarts() {
        context.reviewFlowDataStore.edit { prefs ->
            prefs[Keys.APP_STARTS] = (prefs[Keys.APP_STARTS] ?: 0) + 1
        }
    }

    suspend fun incrementSuccessMoments() {
        context.reviewFlowDataStore.edit { prefs ->
            prefs[Keys.SUCCESS_MOMENTS] = (prefs[Keys.SUCCESS_MOMENTS] ?: 0) + 1
        }
    }

    suspend fun markAttempt(nowEpochMs: Long) {
        context.reviewFlowDataStore.edit { prefs ->
            prefs[Keys.LAST_ATTEMPT_EPOCH_MS] = nowEpochMs
        }
    }

    suspend fun markShown(versionName: String?) {
        if (versionName == null) return
        context.reviewFlowDataStore.edit { prefs ->
            prefs[Keys.LAST_SHOWN_VERSION] = versionName
        }
    }

    suspend fun getSnapshot(): Snapshot {
        val prefs = context.reviewFlowDataStore.data.first()
        return Snapshot(
            appStarts = prefs[Keys.APP_STARTS] ?: 0,
            successMoments = prefs[Keys.SUCCESS_MOMENTS] ?: 0,
            lastAttemptEpochMs = prefs[Keys.LAST_ATTEMPT_EPOCH_MS] ?: 0L,
            lastShownVersion = prefs[Keys.LAST_SHOWN_VERSION],
        )
    }

    data class Snapshot(
        val appStarts: Int,
        val successMoments: Int,
        val lastAttemptEpochMs: Long,
        val lastShownVersion: String?,
    )
}
