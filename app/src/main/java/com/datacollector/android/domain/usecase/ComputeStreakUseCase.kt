package com.datacollector.android.domain.usecase

import com.datacollector.android.data.repository.GoalRepository
import com.datacollector.android.data.repository.JournalRepository
import java.time.LocalDate
import javax.inject.Inject

data class Streaks(val journalDays: Int, val goalCheckinDays: Int)

class ComputeStreakUseCase @Inject constructor(
    private val journalRepo: JournalRepository,
    private val goalRepo: GoalRepository,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): Streaks {
        val journalDates = journalRepo.distinctDates().toSet()
        val goalDates = goalRepo.distinctAchievedDates().toSet()
        return Streaks(
            journalDays = computeStreak(journalDates, today),
            goalCheckinDays = computeStreak(goalDates, today),
        )
    }

    private fun computeStreak(dates: Set<String>, today: LocalDate): Int {
        var count = 0
        var cursor = today
        while (true) {
            if (dates.contains(cursor.toString())) {
                count++
                cursor = cursor.minusDays(1)
            } else {
                if (cursor == today && dates.contains(cursor.minusDays(1).toString())) {
                    cursor = cursor.minusDays(1)
                    continue
                }
                break
            }
        }
        return count
    }
}
