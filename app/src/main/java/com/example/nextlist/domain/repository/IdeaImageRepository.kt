package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.IdeaMedia

interface IdeaImageRepository {
    suspend fun uploadCover(
        groupId: String,
        ideaId: String,
        sourceUri: String,
    ): AppResult<IdeaMedia>

    suspend fun getDownloadUrl(storagePath: String): AppResult<String>

    suspend fun delete(storagePath: String): AppResult<Unit>
}
