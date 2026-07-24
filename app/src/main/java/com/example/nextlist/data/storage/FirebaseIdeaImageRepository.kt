package com.example.nextlist.data.storage

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.IdeaMedia
import com.example.nextlist.domain.repository.IdeaImageRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseIdeaImageRepository @Inject constructor(
    private val imageProcessor: AvatarImageProcessor,
) : IdeaImageRepository {
    private val storage: FirebaseStorage? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseStorage.getInstance() else null
    }

    override suspend fun uploadCover(
        groupId: String,
        ideaId: String,
        sourceUri: String,
    ): AppResult<IdeaMedia> {
        return upload(groupId, ideaId, "cover", sourceUri)
    }

    override suspend fun uploadCompletion(
        groupId: String,
        ideaId: String,
        sourceUri: String,
    ): AppResult<IdeaMedia> = upload(groupId, ideaId, "completion", sourceUri)

    private suspend fun upload(
        groupId: String,
        ideaId: String,
        folder: String,
        sourceUri: String,
    ): AppResult<IdeaMedia> {
        val firebaseStorage = storage ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            val image = imageProcessor.process(sourceUri)
            val path = "groups/$groupId/ideas/$ideaId/$folder/${UUID.randomUUID()}.webp"
            val metadata = StorageMetadata.Builder()
                .setContentType(image.contentType)
                .build()
            firebaseStorage.reference.child(path)
                .putBytes(image.bytes, metadata)
                .awaitTask()
            AppResult.Success(
                IdeaMedia(
                    storagePath = path,
                    mimeType = image.contentType,
                    width = image.width,
                    height = image.height,
                    byteSize = image.bytes.size.toLong(),
                ),
            )
        } catch (_: InvalidAvatarException) {
            AppResult.Failure(AppError.VALIDATION)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun getDownloadUrl(storagePath: String): AppResult<String> {
        val firebaseStorage = storage ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            AppResult.Success(
                firebaseStorage.reference.child(storagePath)
                    .downloadUrl
                    .awaitTask()
                    .toString(),
            )
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun delete(storagePath: String): AppResult<Unit> {
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
}
