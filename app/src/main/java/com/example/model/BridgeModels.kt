package com.example.model

data class PairingRequest(
    val requesterId: String = "",
    val clientInfo: String = "Web Dashboard",
    val requestedAt: Long = System.currentTimeMillis(),
    val token: String = ""
)

data class SmsCommand(
    val commandId: String = "",
    val type: String = "SMS",
    val recipient: String = "",
    val message: String = "",
    val status: String = "pending", // "pending", "sent", "failed"
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val error: String? = null
)

data class UiDeviceState(
    val deviceId: String = "",
    val deviceName: String = "",
    val pairingToken: String = "",
    val tokenExpiresAt: Long = 0L,
    val isPaired: Boolean = false,
    val pairedClientInfo: String? = null,
    val isOnline: Boolean = true,
    val isListening: Boolean = false,
    val pendingPairingRequest: PairingRequest? = null,
    val hasSmsPermission: Boolean = false,
    val firebaseConfigured: Boolean = false,
    val statusMessage: String = ""
)
