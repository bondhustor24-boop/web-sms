package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.SmsBridgeManager
import com.example.db.AppDatabase
import com.example.model.CommandLog
import com.example.model.UiDeviceState
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bridgeManager = SmsBridgeManager(application.applicationContext, viewModelScope)
    val uiState: StateFlow<UiDeviceState> = bridgeManager.uiState

    val logs: StateFlow<List<CommandLog>> = AppDatabase.getInstance(application)
        .commandLogDao()
        .getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _tokenSecondsRemaining = MutableStateFlow(0)
    val tokenSecondsRemaining: StateFlow<Int> = _tokenSecondsRemaining.asStateFlow()

    init {
        // Generate QR code when state changes
        viewModelScope.launch {
            uiState.collect { state ->
                if (!state.isPaired && state.pairingToken.isNotBlank()) {
                    val payload = bridgeManager.getQrPayloadJson()
                    withContext(Dispatchers.Default) {
                        val bmp = QrCodeGenerator.generateQrBitmap(payload, 512)
                        _qrBitmap.value = bmp
                    }
                } else {
                    _qrBitmap.value = null
                }
            }
        }

        // Countdown timer loop for token expiration
        viewModelScope.launch {
            while (isActive) {
                val state = uiState.value
                if (!state.isPaired && state.tokenExpiresAt > 0L) {
                    val diff = (state.tokenExpiresAt - System.currentTimeMillis()) / 1000L
                    if (diff <= 0) {
                        _tokenSecondsRemaining.value = 0
                        // Auto regenerate expired token
                        bridgeManager.generateNewPairingToken()
                    } else {
                        _tokenSecondsRemaining.value = diff.toInt()
                    }
                } else {
                    _tokenSecondsRemaining.value = 0
                }
                delay(1000L)
            }
        }
    }

    fun regenerateToken() {
        bridgeManager.generateNewPairingToken()
    }

    fun acceptPairing() {
        bridgeManager.acceptPairingRequest()
    }

    fun declinePairing() {
        bridgeManager.declinePairingRequest()
    }

    fun disconnect() {
        bridgeManager.disconnect()
    }

    fun updateSmsPermission(isGranted: Boolean) {
        bridgeManager.updateSmsPermissionStatus(isGranted)
    }

    fun sendTestSms(recipient: String, message: String) {
        bridgeManager.sendTestSms(recipient, message)
    }

    fun simulateWebPairing() {
        bridgeManager.simulateWebPairingRequest("Chrome (192.168.1.10)")
    }

    fun clearAllLogs() {
        bridgeManager.clearLogs()
    }

    override fun onCleared() {
        super.onCleared()
        bridgeManager.cleanup()
    }
}
