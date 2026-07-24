package com.example.nextlist.data.messaging

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.repository.DeviceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.deviceDataStore by preferencesDataStore(name = "nextlist_device_state")

@Singleton
class FirebaseDeviceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceRepository {
    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null
    }
    private val firestore: FirebaseFirestore? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseFirestore.getInstance() else null
    }

    @Suppress("DEPRECATION")
    override suspend fun registerCurrentDevice(): AppResult<Unit> {
        if (!BuildConfig.FIREBASE_CONFIGURED) return AppResult.Failure(AppError.UNKNOWN)
        return try {
            val token = FirebaseMessaging.getInstance().token.awaitTask()
            registerToken(token)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun registerToken(token: String): AppResult<Unit> {
        if (token.isBlank() || token.length > 4096) {
            return AppResult.Failure(AppError.VALIDATION)
        }
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = auth?.currentUser?.uid
            ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            val deviceId = installationId()
            val reference = database.collection("users").document(uid)
                .collection("devices").document(deviceId)
            database.runTransaction { transaction ->
                val existing = transaction.get(reference)
                val serverTime = FieldValue.serverTimestamp()
                transaction.set(
                    reference,
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "locale" to Locale.getDefault().toLanguageTag(),
                        "createdAt" to (
                            existing.getTimestamp("createdAt") ?: serverTime
                            ),
                        "updatedAt" to serverTime,
                    ),
                )
            }.awaitTask()
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun unregisterCurrentDevice(): AppResult<Unit> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = auth?.currentUser?.uid
            ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            val deviceId = installationId()
            database.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .delete()
                .awaitTask()
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private suspend fun installationId(): String {
        val values = context.deviceDataStore.data.first()
        values[INSTALLATION_ID_KEY]?.let { return it }
        val generated = UUID.randomUUID().toString()
        context.deviceDataStore.edit { it[INSTALLATION_ID_KEY] = generated }
        return generated
    }

    private companion object {
        val INSTALLATION_ID_KEY = stringPreferencesKey("installation_id")
    }
}
