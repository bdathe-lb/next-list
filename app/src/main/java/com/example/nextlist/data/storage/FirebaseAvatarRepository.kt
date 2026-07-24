package com.example.nextlist.data.storage

import android.util.Log
import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.repository.AvatarRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAvatarRepository @Inject constructor(
    private val imageProcessor: AvatarImageProcessor,
) : AvatarRepository {
    private val storage: FirebaseStorage? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseStorage.getInstance() else null
    }

    override suspend fun uploadAvatar(
        userId: String,
        sourceUri: String,
    ): AppResult<String> {
        val firebaseStorage = storage ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            val avatar = imageProcessor.process(sourceUri)
            val path = "users/$userId/avatar/${UUID.randomUUID()}.webp"
            val metadata = StorageMetadata.Builder()
                .setContentType(avatar.contentType)
                .build()
            firebaseStorage.reference.child(path)
                .putBytes(avatar.bytes, metadata)
                .awaitTask()
            AppResult.Success(path)
        } catch (error: InvalidAvatarException) {
            Log.w(LOG_TAG, "Avatar rejected during local processing: ${error.reason}")
            AppResult.Failure(AppError.VALIDATION)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun getDownloadUrl(storagePath: String): AppResult<String> {
        val firebaseStorage = storage ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            val uri = firebaseStorage.reference.child(storagePath).downloadUrl.awaitTask()
            AppResult.Success(uri.toString())
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun deleteAvatar(storagePath: String): AppResult<Unit> {
        val firebaseStorage = storage ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            firebaseStorage.reference.child(storagePath).delete().awaitTask()
            AppResult.Success(Unit)
        } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(error.toAppError())
            }
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private companion object {
        const val LOG_TAG = "NextListAvatar"
    }
}
