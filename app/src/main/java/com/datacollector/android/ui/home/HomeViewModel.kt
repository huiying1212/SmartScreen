package com.datacollector.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datacollector.android.data.config.AppPreferences
import com.datacollector.android.data.repository.GoalRepository
import com.datacollector.android.data.repository.JournalRepository
import com.datacollector.android.data.repository.ReflectionRepository
import com.datacollector.android.domain.model.ScorePoint
import com.datacollector.android.domain.usecase.ComputeStreakUseCase
import com.datacollector.android.domain.usecase.GetLatestScoreUseCase
import com.datacollector.android.domain.usecase.GetTodayScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class HomeUiState(
    val score: Int = 0,
    val todayPoints: List<ScorePoint> = emptyList(),
    val latestReminder: String? = null,
    val faceStyle: String = "CLASSIC",
    val journalStreak: Int = 0,
    val goalStreak: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getToday: GetTodayScoreUseCase,
    private val getLatest: GetLatestScoreUseCase,
    private val reflectionRepo: ReflectionRepository,
    private val journalRepo: JournalRepository,
    private val goalRepo: GoalRepository,
    private val computeStreak: ComputeStreakUseCase,
    private val prefs: AppPreferences,
) : ViewModel() {

    // Recompute streaks whenever the underlying journal/goal data changes,
    // so the chips on Home update immediately after writing a journal or
    // checking in a goal — rather than only on first ViewModel construction.
    private val streaksFlow = combine(
        journalRepo.observeCount(),
        goalRepo.observeActive(),
    ) { _, _ -> Unit }
        .flatMapLatest {
            flow {
                val s = computeStreak()
                emit(s.journalDays to s.goalCheckinDays)
            }
        }

    val state = combine(
        getToday(),
        getLatest(),
        reflectionRepo.observeLatestBubble(),
        prefs.faceStyle,
        streaksFlow,
    ) { today, latest, bubble, faceStyle, streaks ->
        HomeUiState(
            score = latest?.score ?: 0,
            todayPoints = today,
            latestReminder = bubble?.content,
            faceStyle = faceStyle,
            journalStreak = streaks.first,
            goalStreak = streaks.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
