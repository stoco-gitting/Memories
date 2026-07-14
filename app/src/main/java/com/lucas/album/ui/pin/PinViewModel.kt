package com.lucas.album.ui.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lucas.album.data.auth.PinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PinViewModel(private val pinManager: PinManager) : ViewModel() {

    private val _digits = MutableStateFlow("")
    val digits: StateFlow<String> = _digits.asStateFlow()

    private val _shakeTrigger = MutableStateFlow(0)
    val shakeTrigger: StateFlow<Int> = _shakeTrigger.asStateFlow()

    fun onDigit(digit: Char, onVerified: () -> Unit) {
        if (_digits.value.length >= PinManager.PIN_LENGTH) return
        _digits.value += digit
        if (_digits.value.length == PinManager.PIN_LENGTH) {
            viewModelScope.launch {
                if (pinManager.verify(_digits.value)) {
                    onVerified()
                } else {
                    _shakeTrigger.value += 1
                    _digits.value = ""
                }
            }
        }
    }

    fun onBackspace() {
        _digits.value = _digits.value.dropLast(1)
    }

    companion object {
        fun factory(pinManager: PinManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PinViewModel(pinManager) as T
            }
        }
    }
}
