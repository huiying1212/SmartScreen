package com.datacollector.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datacollector.android.data.config.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val privacyAccepted: Boolean = false,
    val personalGoal: String = "",
    val faceStyle: String = "CLASSIC",
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun setPrivacyAccepted(value: Boolean) {
        _state.value = _state.value.copy(privacyAccepted = value)
    }

    fun setPersonalGoal(value: String) {
        _state.value = _state.value.copy(personalGoal = value)
    }

    fun setFaceStyle(value: String) {
        _state.value = _state.value.copy(faceStyle = value)
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setPrivacyAccepted(_state.value.privacyAccepted)
            prefs.setPersonalGoal(_state.value.personalGoal)
            prefs.setFaceStyle(_state.value.faceStyle)
            prefs.setOnboardingCompleted(true)
            onDone()
        }
    }
}
