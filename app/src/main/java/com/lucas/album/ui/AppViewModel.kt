package com.lucas.album.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucas.album.data.auth.PinManager
import com.lucas.album.data.prefs.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class Screen {
    data object Loading : Screen()
    data object Proposal : Screen()
    data object Pin : Screen()
    data object Canvas : Screen()
}

// `unlockedThisProcess` is deliberately in-memory only (never persisted): it resets to
// false whenever the process dies and restarts, but survives backgrounding/foregrounding
// within the same process — implementing "re-lock only on full restart" with no extra
// lifecycle observer needed.
class AppViewModel(
    private val preferences: AppPreferences,
    private val pinManager: PinManager,
) : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private var unlockedThisProcess = false

    init {
        viewModelScope.launch {
            val hasAnswered = preferences.hasAnsweredProposal()
            _screen.value = when {
                !hasAnswered -> Screen.Proposal
                unlockedThisProcess -> Screen.Canvas
                else -> Screen.Pin
            }
        }
    }

    fun onProposalAnswered() {
        viewModelScope.launch {
            preferences.setHasAnsweredProposal(true)
            pinManager.setUpFixedPin()
            _screen.value = Screen.Pin
        }
    }

    fun onPinVerified() {
        unlockedThisProcess = true
        _screen.value = Screen.Canvas
    }

    companion object {
        fun factory(preferences: AppPreferences, pinManager: PinManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(preferences, pinManager) as T
            }
        }
    }
}
