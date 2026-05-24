package com.datacollector.android.domain.usecase

import com.datacollector.android.data.repository.AchievementRepository
import com.datacollector.android.data.repository.GoalRepository
import com.datacollector.android.data.repository.JournalRepository
import com.datacollector.android.data.repository.ReflectionRepository
import com.datacollector.android.data.repository.ScoreRepository
import com.datacollector.android.domain.model.Achievement
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Centralised "did anything just unlock?" logic. Each public method is
 * idempotent: codes already unlocked are skipped silently. Returns the
 * set of newly-unlocked Achievements so the caller can show toasts.
 */
class UnlockAchievementsUseCase @Inject constructor(
    private val achievementRepo: AchievementRepository,
    private val journalRepo: JournalRepository,
    private val goalRepo: GoalRepository,
    private val scoreRepo: ScoreRepository,
    private val reflectionRepo: ReflectionRepository,
    private val computeStreak: ComputeStreakUseCase,
) {

    suspend fun checkJournalAchievements(): List<Achievement> {
        achievementRepo.ensureSeeded()
        val newly = mutableListOf<Achievement>()
        val count = journalRepo.observeCount().first()
        if (count >= 1) tryUnlock("FIRST_JOURNAL", newly)
        val streaks = computeStreak.invoke()
        if (streaks.journalDays >= 7) tryUnlock("STREAK_7_JOURNAL", newly)
        if (streaks.journalDays >= 30) tryUnlock("STREAK_30_JOURNAL", newly)
        achievementRepo.setProgress("STREAK_7_JOURNAL", streaks.journalDays.coerceAtMost(7))
        achievementRepo.setProgress("STREAK_30_JOURNAL", streaks.journalDays.coerceAtMost(30))
        return newly
    }

    suspend fun checkGoalAchievements(): List<Achievement> {
        achievementRepo.ensureSeeded()
        val newly = mutableListOf<Achievement>()
        if (goalRepo.observeAll().first().isNotEmpty()) tryUnlock("FIRST_GOAL", newly)
        val streaks = computeStreak.invoke()
        if (streaks.goalCheckinDays >= 7) tryUnlock("GOAL_HIT_7", newly)
        achievementRepo.setProgress("GOAL_HIT_7", streaks.goalCheckinDays.coerceAtMost(7))
        return newly
    }

    suspend fun checkScoreAchievements(): List<Achievement> {
        achievementRepo.ensureSeeded()
        val newly = mutableListOf<Achievement>()
        val avg = scoreRepo.averageBetween(TimeWindow.startOfWeek(), TimeWindow.endOfWeek())
        if (avg != null && avg < 30.0) tryUnlock("LOW_AVG_WEEK", newly)
        return newly
    }

    suspend fun checkFirstReflection(): List<Achievement> {
        achievementRepo.ensureSeeded()
        val newly = mutableListOf<Achievement>()
        val latest = reflectionRepo.observeRecent(1).first()
        if (latest.isNotEmpty()) tryUnlock("FIRST_REFLECTION", newly)
        return newly
    }

    private suspend fun tryUnlock(code: String, sink: MutableList<Achievement>) {
        val before = achievementRepo.byCode(code)
        if (before?.isUnlocked == true) return
        achievementRepo.unlock(code)
        achievementRepo.byCode(code)?.let { sink.add(it) }
    }
}
