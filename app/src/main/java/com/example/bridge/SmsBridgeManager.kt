package com.example.bridge

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.example.db.AppDatabase
import com.example.model.CommandLog
import com.example.model.PairingRequest
import com.example.model.SmsCommand
import com.example.model.UiDeviceState
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

class SmsBridgeManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_bridge_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(context)
    private val logDao = db.commandLogDao()

    private var firestore: FirebaseFirestore? = null
    private var deviceListener: ListenerRegistration? = null
    private var commandListener: ListenerRegistration? = null
    private var heartbeatJob: Job? = null

    private val _uiState = MutableStateFlow(UiDeviceState())
    val uiState: StateFlow<UiDeviceState> = _uiState.asStateFlow()

    init {
        initDeviceId()
        initFirebase()
        generateNewPairingToken()
        startHeartbeat()
    }

    private fun initDeviceId() {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) {
            val randomPart = UUID.randomUUID().toString().substring(0, 8).uppercase()
            id = "DEV-$randomPart"
            prefs.edit().putString("device_id", id).apply()
        }
        val isPaired = prefs.getBoolean("is_paired", false)
        val pairedInfo = prefs.getString("paired_client_info", null)

        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        _uiState.value = _uiState.value.copy(
            deviceId = id,
            deviceName = deviceName,
            isPaired = isPaired,
            pairedClientInfo = pairedInfo
        )
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                _uiState.value = _uiState.value.copy(
                    firebaseConfigured = true,
                    statusMessage = "Firebase Connected"
                )
                startFirestoreListeners()
            } else {
                _uiState.value = _uiState.value.copy(
                    firebaseConfigured = false,
                    statusMessage = "Firebase not initialized (Waiting for google-services.json)"
                )
            }
        } catch (e: Exception) {
            Log.e("SmsBridge", "Firebase init error: ${e.message}")
            _uiState.value = _uiState.value.copy(
                firebaseConfigured = false,
                statusMessage = "Firebase ready in standalone mode"
            )
        }
    }

    fun generateNewPairingToken() {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        val token = sb.toString()
        val expiresAt = System.currentTimeMillis() + 5 * 60 * 1000L // 5 minutes validity

        _uiState.value = _uiState.value.copy(
            pairingToken = token,
            tokenExpiresAt = expiresAt
        )

        updateDeviceInFirestore()
    }

    private fun updateDeviceInFirestore() {
        val fs = firestore ?: return
        val state = _uiState.value
        val data = hashMapOf(
            "deviceId" to state.deviceId,
            "deviceName" to state.deviceName,
            "online" to true,
            "lastSeen" to System.currentTimeMillis(),
            "paired" to state.isPaired,
            "pairedClient" to (state.pairedClientInfo ?: ""),
            "pairingToken" to state.pairingToken,
            "tokenExpiresAt" to state.tokenExpiresAt,
            "updatedAt" to System.currentTimeMillis()
        )

        fs.collection("devices").document(state.deviceId)
            .set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w("SmsBridge", "Device sync error: ${e.message}")
            }
    }

    private fun startFirestoreListeners() {
        val fs = firestore ?: return
        val deviceId = _uiState.value.deviceId

        // Listen for pairing requests & device state
        deviceListener?.remove()
        deviceListener = fs.collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val isPaired = snapshot.getBoolean("paired") ?: false
                val pairedClient = snapshot.getString("pairedClient")

                // Check incoming pairing request
                val reqRequester = snapshot.getString("pendingRequesterId")
                val reqToken = snapshot.getString("pendingPairingToken")
                val reqClient = snapshot.getString("pendingClientInfo") ?: "Web Dashboard"
                val reqTime = snapshot.getLong("pendingRequestedAt") ?: System.currentTimeMillis()

                if (!isPaired && !reqRequester.isNullOrBlank() && !reqToken.isNullOrBlank()) {
                    val currentToken = _uiState.value.pairingToken
                    val expiresAt = _uiState.value.tokenExpiresAt
                    val isTokenValid = reqToken == currentToken && System.currentTimeMillis() <= expiresAt

                    if (isTokenValid) {
                        _uiState.value = _uiState.value.copy(
                            pendingPairingRequest = PairingRequest(
                                requesterId = reqRequester,
                                clientInfo = reqClient,
                                requestedAt = reqTime,
                                token = reqToken
                            )
                        )
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isPaired = isPaired,
                    pairedClientInfo = pairedClient,
                    isListening = true
                )
            }

        // Listen for pending commands
        commandListener?.remove()
        commandListener = fs.collection("commands")
            .document(deviceId)
            .collection("messages")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val commandId = doc.id
                    val recipient = doc.getString("recipient") ?: ""
                    val message = doc.getString("message") ?: ""
                    val type = doc.getString("type") ?: "SMS"

                    if (recipient.isNotBlank() && message.isNotBlank()) {
                        processIncomingCommand(commandId, type, recipient, message)
                    }
                }
            }
    }

    private fun processIncomingCommand(commandId: String, type: String, recipient: String, message: String) {
        scope.launch {
            val log = CommandLog(
                commandId = commandId,
                type = type,
                recipient = recipient,
                message = message,
                status = "pending",
                timestamp = System.currentTimeMillis()
            )
            logDao.insertLog(log)

            if (!_uiState.value.hasSmsPermission) {
                updateCommandStatus(commandId, "failed", "SMS permission is not granted on Android device")
                logDao.updateLogStatus(commandId, "failed", "SMS permission not granted")
                return@launch
            }

            try {
                sendSmsInternal(recipient, message)
                updateCommandStatus(commandId, "sent", null)
                logDao.updateLogStatus(commandId, "sent", null)
            } catch (e: Exception) {
                Log.e("SmsBridge", "Failed to send SMS: ${e.message}")
                updateCommandStatus(commandId, "failed", e.localizedMessage ?: "Unknown SMS error")
                logDao.updateLogStatus(commandId, "failed", e.localizedMessage)
            }
        }
    }

    private fun sendSmsInternal(recipient: String, message: String) {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(recipient, null, message, null, null)
        }
    }

    private fun updateCommandStatus(commandId: String, status: String, error: String?) {
        val fs = firestore ?: return
        val deviceId = _uiState.value.deviceId

        val updates = hashMapOf<String, Any>(
            "status" to status,
            "completedAt" to System.currentTimeMillis()
        )
        if (error != null) {
            updates["error"] = error
        }

        fs.collection("commands")
            .document(deviceId)
            .collection("messages")
            .document(commandId)
            .set(updates, SetOptions.merge())
    }

    fun acceptPairingRequest() {
        val req = _uiState.value.pendingPairingRequest ?: return
        val deviceId = _uiState.value.deviceId

        prefs.edit()
            .putBoolean("is_paired", true)
            .putString("paired_client_info", req.clientInfo)
            .apply()

        _uiState.value = _uiState.value.copy(
            isPaired = true,
            pairedClientInfo = req.clientInfo,
            pendingPairingRequest = null,
            pairingToken = "", // Consume token
            tokenExpiresAt = 0L
        )

        firestore?.collection("devices")?.document(deviceId)?.set(
            hashMapOf(
                "paired" to true,
                "pairedClient" to req.clientInfo,
                "pairedAt" to System.currentTimeMillis(),
                "pairingToken" to "",
                "pendingRequesterId" to null,
                "pendingPairingToken" to null,
                "pendingClientInfo" to null
            ),
            SetOptions.merge()
        )

        // Log pairing event
        scope.launch {
            logDao.insertLog(
                CommandLog(
                    commandId = "PAIR-${System.currentTimeMillis()}",
                    type = "PAIRING",
                    recipient = "Web Dashboard",
                    message = "Paired with ${req.clientInfo} successfully",
                    status = "sent",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun declinePairingRequest() {
        val deviceId = _uiState.value.deviceId
        _uiState.value = _uiState.value.copy(pendingPairingRequest = null)

        firestore?.collection("devices")?.document(deviceId)?.set(
            hashMapOf(
                "pendingRequesterId" to null,
                "pendingPairingToken" to null,
                "pendingClientInfo" to null
            ),
            SetOptions.merge()
        )

        // Refresh token for security
        generateNewPairingToken()
    }

    fun disconnect() {
        val deviceId = _uiState.value.deviceId

        prefs.edit()
            .putBoolean("is_paired", false)
            .remove("paired_client_info")
            .apply()

        _uiState.value = _uiState.value.copy(
            isPaired = false,
            pairedClientInfo = null,
            pendingPairingRequest = null
        )

        firestore?.collection("devices")?.document(deviceId)?.set(
            hashMapOf(
                "paired" to false,
                "pairedClient" to "",
                "pairingToken" to "",
                "pendingRequesterId" to null,
                "pendingPairingToken" to null,
                "pendingClientInfo" to null,
                "unpairedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )

        generateNewPairingToken()

        scope.launch {
            logDao.insertLog(
                CommandLog(
                    commandId = "UNPAIR-${System.currentTimeMillis()}",
                    type = "UNPAIR",
                    recipient = "Web Dashboard",
                    message = "Device disconnected & un-paired",
                    status = "sent",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateSmsPermissionStatus(isGranted: Boolean) {
        _uiState.value = _uiState.value.copy(hasSmsPermission = isGranted)
    }

    fun sendTestSms(recipient: String, message: String) {
        val cmdId = "TEST-${System.currentTimeMillis()}"
        processIncomingCommand(cmdId, "SMS", recipient, message)
    }

    fun simulateWebPairingRequest(clientName: String = "Chrome on macOS") {
        val currentToken = _uiState.value.pairingToken
        _uiState.value = _uiState.value.copy(
            pendingPairingRequest = PairingRequest(
                requesterId = "WEB-${UUID.randomUUID().toString().take(6)}",
                clientInfo = clientName,
                requestedAt = System.currentTimeMillis(),
                token = currentToken
            )
        )
    }

    fun clearLogs() {
        scope.launch {
            logDao.clearAllLogs()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(20000L) // every 20 seconds
                if (firestore != null) {
                    val deviceId = _uiState.value.deviceId
                    firestore?.collection("devices")?.document(deviceId)?.update(
                        "online", true,
                        "lastSeen", System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun getQrPayloadJson(): String {
        val state = _uiState.value
        val obj = JSONObject().apply {
            put("deviceId", state.deviceId)
            put("deviceName", state.deviceName)
            put("token", state.pairingToken)
            put("expiresAt", state.tokenExpiresAt)
            put("app", "SMS Bridge")
            put("version", "1.0")
        }
        return obj.toString()
    }

    fun cleanup() {
        deviceListener?.remove()
        commandListener?.remove()
        heartbeatJob?.cancel()
    }
}
