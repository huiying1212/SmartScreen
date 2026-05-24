package com.datacollector.android.domain.usecase

import com.datacollector.android.data.repository.JournalRepository
import com.datacollector.android.domain.model.JournalEntry
import java.time.LocalDate
import javax.inject.Inject

class SaveJournalUseCase @Inject constructor(
    private val repo: JournalRepository,
    private val unlockAchievements: UnlockAchievementsUseCase,
) {
    suspend operator fun invoke(
        existing: JournalEntry?,
        text: String,
        mood: String?,
        relatedScore: Int?,
        date: LocalDate = LocalDate.now(),
    ): Long {
        val entry = existing?.copy(text = text, mood = mood, relatedScore = relatedScore)
            ?: JournalEntry(
                id = 0,
                date = date.toString(),
                mood = mood,
                text = text,
                relatedScore = relatedScore,
                createdAt = 0L,
                updatedAt = 0L,
            )
        val id = repo.upsert(entry)
        unlockAchievements.checkJournalAchievements()
        return id
    }
}
