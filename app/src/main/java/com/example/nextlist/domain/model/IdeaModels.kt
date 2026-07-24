package com.example.nextlist.domain.model

import java.time.Instant

enum class IdeaCategory(
    val wireValue: String,
    val label: String,
    val glyph: String,
) {
    PLACE("place", "地点", "⌖"),
    RESTAURANT("restaurant", "餐厅", "餐"),
    KTV_SONG("ktv_song", "KTV 歌曲", "歌"),
    MOVIE("movie", "电影", "影"),
    ACTIVITY("activity", "活动", "动"),
    OTHER("other", "其他", "想"),
    ;

    companion object {
        fun fromWire(value: String?): IdeaCategory? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class IdeaStatus(
    val wireValue: String,
    val label: String,
) {
    IDEA("idea", "想法"),
    SCHEDULED("scheduled", "已安排"),
    COMPLETED("completed", "已完成"),
    ;

    companion object {
        fun fromWire(value: String?): IdeaStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class IdeaMedia(
    val storagePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val downloadUrl: String? = null,
)

data class ReactionCounts(
    val want: Int = 0,
    val ok: Int = 0,
    val notInterested: Int = 0,
)

data class Idea(
    val id: String,
    val groupId: String,
    val title: String,
    val category: IdeaCategory,
    val note: String?,
    val media: IdeaMedia?,
    val locationOrLink: String?,
    val createdBy: String,
    val creatorSnapshot: MemberSnapshot,
    val status: IdeaStatus,
    val reactionCounts: ReactionCounts,
    val commentCount: Int,
    val lastModifiedBy: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val hasPendingWrites: Boolean = false,
)

data class IdeaDraft(
    val title: String,
    val category: IdeaCategory,
    val note: String?,
    val locationOrLink: String?,
    val media: IdeaMedia?,
)

enum class ReactionValue(
    val wireValue: String,
    val label: String,
) {
    WANT("want", "想参加"),
    OK("ok", "都可以"),
    NOT_INTERESTED("not_interested", "不感兴趣"),
    ;

    companion object {
        fun fromWire(value: String?): ReactionValue? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class IdeaReaction(
    val userId: String,
    val value: ReactionValue,
    val userSnapshot: MemberSnapshot,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val hasPendingWrites: Boolean = false,
)

data class IdeaComment(
    val id: String,
    val content: String,
    val createdBy: String,
    val creatorSnapshot: MemberSnapshot,
    val createdAt: Instant?,
    val hasPendingWrites: Boolean = false,
)

data class RealtimeItems<T>(
    val items: List<T>,
    val hasPendingWrites: Boolean,
    val isFromCache: Boolean,
)
