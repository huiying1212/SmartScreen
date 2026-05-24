package com.datacollector.android.domain.model

import java.time.LocalDate

data class ScorePoint(val ts: Long, val score: Int, val delta: Int, val reason: String?)

data class JournalEntry(
    val id: Long,
    val date: String,
    val mood: String?,
    val text: String,
    val relatedScore: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class GoalTargetType(val raw: String) {
    FREE_TEXT("free_text"),
    APP_LIMIT("app_limit"),
    NIGHT_CURFEW("night_curfew"),
    DAILY_JOURNAL("daily_journal");

    companion object {
        fun from(raw: String?): GoalTargetType =
            values().firstOrNull { it.raw == raw } ?: FREE_TEXT
    }
}

data class Goal(
    val id: Long,
    val title: String,
    val description: String?,
    val targetType: GoalTargetType,
    val targetValue: String?,
    val period: String,
    val createdAt: Long,
    val archivedAt: Long?,
)

data class GoalCheckin(
    val id: Long,
    val goalId: Long,
    val date: String,
    val achieved: Boolean,
    val note: String?,
)

data class Achievement(
    val code: String,
    val title: String,
    val description: String,
    val unlockedAt: Long?,
    val progress: Int,
    val target: Int,
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}

data class ReflectionMessage(
    val id: Long,
    val ts: Long,
    val kind: String,
    val content: String,
    val scoreAtTime: Int?,
)

data class WallpaperRecord(
    val id: Long,
    val ts: Long,
    val keywords: String,
    val styleName: String,
    val filePath: String,
)

data class TopApp(val packageName: String, val category: String?, val totalMinutes: Int)
data class LocationBucket(val name: String, val count: Int)

data class WeekScoreSummary(
    val weekStart: LocalDate,
    val averageScore: Double,
    val pointCount: Int,
    val highestScore: Int,
    val lowestScore: Int,
)
