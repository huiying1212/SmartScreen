package com.datacollector.android.ui.reflect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datacollector.android.data.repository.AchievementRepository
import com.datacollector.android.data.repository.GoalRepository
import com.datacollector.android.data.repository.JournalRepository
import com.datacollector.android.domain.model.Achievement
import com.datacollector.android.domain.model.Goal
import com.datacollector.android.domain.model.GoalCheckin
import com.datacollector.android.domain.model.GoalTargetType
import com.datacollector.android.domain.model.JournalEntry
import com.datacollector.android.domain.usecase.CheckInGoalUseCase
import com.datacollector.android.domain.usecase.SaveJournalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReflectUiState(
    val journals: List<JournalEntry> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val todayCheckins: Map<Long, GoalCheckin> = emptyMap(),
)

@HiltViewModel
class ReflectViewModel @Inject constructor(
    private val journalRepo: JournalRepository,
    private val goalRepo: GoalRepository,
    private val achievementRepo: AchievementRepository,
    private val saveJournal: SaveJournalUseCase,
    private val checkInGoal: CheckInGoalUseCase,
) : ViewModel() {

    private val _todayCheckins = MutableStateFlow<Map<Long, GoalCheckin>>(emptyMap())

    val state = combine(
        journalRepo.observeAll(),
        goalRepo.observeActive(),
        achievementRepo.observeAll(),
        _todayCheckins,
    ) { journals, goals, achievements, checkins ->
        ReflectUiState(
            journals = journals,
            goals = goals,
            achievements = achievements,
            todayCheckins = checkins,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReflectUiState())

    init {
        viewModelScope.launch { achievementRepo.ensureSeeded() }
        refreshTodayCheckins()
    }

    fun refreshTodayCheckins() {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            _todayCheckins.value =
                goalRepo.checkinsByDate(today).associateBy { it.goalId }
        }
    }

    fun saveJournalEntry(text: String, mood: String?, existingId: Long?) {
        viewModelScope.launch {
            val existing = existingId?.let { journalRepo.byId(it) }
            saveJournal(existing = existing, text = text, mood = mood, relatedScore = null)
        }
    }

    fun deleteJournal(entry: JournalEntry) {
        viewModelScope.launch { journalRepo.delete(entry) }
    }

    fun addGoal(title: String, description: String?, type: GoalTargetType, value: String?) {
        viewModelScope.launch {
            goalRepo.add(title, description, type, value, period = "daily")
        }
    }

    fun archiveGoal(goal: Goal) {
        viewModelScope.launch { goalRepo.archive(goal) }
    }

    fun toggleCheckin(goal: Goal) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val current = goalRepo.checkinsByDate(today).firstOrNull { it.goalId == goal.id }
            checkInGoal(goal.id, !(current?.achieved ?: false))
            refreshTodayCheckins()
        }
    }
}
