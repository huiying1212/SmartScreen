package com.datacollector.android.domain.usecase

import com.datacollector.android.data.repository.GoalRepository
import java.time.LocalDate
import javax.inject.Inject

class CheckInGoalUseCase @Inject constructor(
    private val goalRepo: GoalRepository,
    private val unlockAchievements: UnlockAchievementsUseCase,
) {
    suspend operator fun invoke(
        goalId: Long,
        achieved: Boolean,
        note: String? = null,
        date: LocalDate = LocalDate.now(),
    ) {
        goalRepo.checkIn(goalId, date.toString(), achieved, note)
        unlockAchievements.checkGoalAchievements()
    }
}
