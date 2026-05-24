package com.datacollector.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datacollector.android.data.config.AppPreferences
import com.datacollector.android.data.repository.ScoreRepository
import com.datacollector.android.domain.model.ScorePoint
import com.datacollector.android.domain.usecase.GetTodayScoreUseCase
import com.datacollector.android.domain.usecase.TimeWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One past day's final mood, used to render an emoji in the history grid. */
data class DailyMood(
    val date: LocalDate,
    val score: Int?,
)

data class HistoryUiState(
    val faceStyle: String = "CLASSIC",
    val days: List<DailyMood> = emptyList(),
    val todayPoints: List<ScorePoint> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val scoreRepo: ScoreRepository,
    prefs: AppPreferences,
    getTodayScore: GetTodayScoreUseCase,
) : ViewModel() {

    private val _days = MutableStateFlow<List<DailyMood>>(emptyList())

    val state = combine(prefs.faceStyle, _days, getTodayScore()) { style, days, todayPoints ->
        HistoryUiState(faceStyle = style, days = days, todayPoints = todayPoints)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init { loadRecentDays(DAYS) }

    private fun loadRecentDays(count: Int) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val start = TimeWindow.startOfDay(today.minusDays((count - 1).toLong()))
            val end = TimeWindow.endOfDay(today)
            val points = scoreRepo.rangeBetween(start, end)

            // Last score per local date.
            val byDay = points
                .groupBy { p ->
                    java.time.Instant.ofEpochMilli(p.ts).atZone(zone).toLocalDate()
                }
                .mapValues { (_, list) -> list.maxByOrNull { it.ts }?.score }

            _days.value = (0 until count).map { i ->
                val d = today.minusDays(i.toLong())
                DailyMood(date = d, score = byDay[d])
            }
        }
    }

    companion object { private const val DAYS = 30 }
}
