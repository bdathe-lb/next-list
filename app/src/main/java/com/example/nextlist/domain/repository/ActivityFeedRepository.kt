package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.ActivityFeedCursor
import com.example.nextlist.domain.model.ActivityFeedPage
import kotlinx.coroutines.flow.Flow

interface ActivityFeedRepository {
    fun observeLatest(pageSize: Int = 30): Flow<AppResult<ActivityFeedPage>>

    suspend fun loadMore(
        cursor: ActivityFeedCursor,
        pageSize: Int = 30,
    ): AppResult<ActivityFeedPage>

    suspend fun markRead(feedId: String): AppResult<Unit>

    suspend fun markAllRead(): AppResult<Unit>
}
