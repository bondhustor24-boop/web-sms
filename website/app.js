// SMS Bridge Web Dashboard Client Logic

let db = null;
let currentDeviceId = localStorage.getItem('sms_bridge_device_id') || null;
let deviceListenerUnsubscribe = null;
let commandsListenerUnsubscribe = null;
let html5QrCodeScanner = null;

// DOM Elements
const unpairedView = document.getElementById('unpairedView');
const pairedView = document.getElementById('pairedView');
const connectionBadge = document.getElementById('connectionBadge');
const startScanBtn = document.getElementById('startScanBtn');
const manualPairBtn = document.getElementById('manualPairBtn');
const manualDeviceId = document.getElementById('manualDeviceId');
const manualToken = document.getElementById('manualToken');
const disconnectBtn = document.getElementById('disconnectBtn');
const connectedDeviceName = document.getElementById('connectedDeviceName');
const connectedDeviceId = document.getElementById('connectedDeviceId');
const deviceLiveStatus = document.getElementById('deviceLiveStatus');
const deviceLastSeen = document.getElementById('deviceLastSeen');
const smsForm = document.getElementById('smsForm');
const recipientNumber = document.getElementById('recipientNumber');
const smsMessage = document.getElementById('smsMessage');
const charCounter = document.getElementById('charCounter');
const sendSmsBtn = document.getElementById('sendSmsBtn');
const commandList = document.getElementById('commandList');
const clearWebLogs = document.getElementById('clearWebLogs');

// Modals
const scannerModal = document.getElementById('scannerModal');
const closeScannerBtn = document.getElementById('closeScannerBtn');
const configModal = document.getElementById('configModal');
const configBtn = document.getElementById('configBtn');
const closeConfigBtn = document.getElementById('closeConfigBtn');
const firebaseConfigInput = document.getElementById('firebaseConfigInput');
const saveConfigBtn = document.getElementById('saveConfigBtn');

// Initialize App
document.addEventListener('DOMContentLoaded', () => {
  initFirebase();
  setupEventListeners();
  updateCharCounter();
  
  if (currentDeviceId) {
    attachDeviceListeners(currentDeviceId);
  }
});

// Initialize Firebase from stored config or fallback
function initFirebase() {
  const storedConfig = localStorage.getItem('sms_bridge_firebase_config');
  if (storedConfig) {
    try {
      const config = JSON.parse(storedConfig);
      if (!firebase.apps.length) {
        firebase.initializeApp(config);
      }
      db = firebase.firestore();
      console.log('Firebase initialized successfully.');
    } catch (e) {
      console.error('Error parsing stored Firebase config:', e);
    }
  } else {
    // Show setup reminder or load existing if window config exists
    console.log('No Firebase config found in localStorage. Please configure Firebase.');
  }
}

function setupEventListeners() {
  // Config Modal
  configBtn.addEventListener('click', () => {
    const stored = localStorage.getItem('sms_bridge_firebase_config');
    if (stored) {
      firebaseConfigInput.value = stored;
    }
    configModal.classList.add('active');
  });

  closeConfigBtn.addEventListener('click', () => {
    configModal.classList.remove('active');
  });

  saveConfigBtn.addEventListener('click', () => {
    const rawVal = firebaseConfigInput.value.trim();
    try {
      const parsed = JSON.parse(rawVal);
      localStorage.setItem('sms_bridge_firebase_config', JSON.stringify(parsed, null, 2));
      alert('Firebase config saved! Reloading application...');
      location.reload();
    } catch (e) {
      alert('Invalid JSON configuration. Please ensure you paste valid JSON.');
    }
  });

  // QR Scanner Modal
  startScanBtn.addEventListener('click', () => {
    if (!checkFirebaseReady()) return;
    scannerModal.classList.add('active');
    startQrScanner();
  });

  closeScannerBtn.addEventListener('click', () => {
    stopQrScanner();
    scannerModal.classList.remove('active');
  });

  // Manual Pairing
  manualPairBtn.addEventListener('click', () => {
    if (!checkFirebaseReady()) return;
    const deviceId = manualDeviceId.value.trim().toUpperCase();
    const token = manualToken.value.trim().toUpperCase();

    if (!deviceId || !token) {
      alert('Please enter both Device ID and 6-digit Pairing Token.');
      return;
    }

    initiatePairingHandshake(deviceId, token);
  });

  // Disconnect
  disconnectBtn.addEventListener('click', () => {
    if (confirm('Are you sure you want to disconnect this device?')) {
      handleDisconnect();
    }
  });

  // SMS Form
  smsMessage.addEventListener('input', updateCharCounter);

  smsForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const recipient = recipientNumber.value.trim();
    const message = smsMessage.value.trim();

    if (!recipient || !message) return;
    if (!currentDeviceId) {
      alert('Please pair an Android device first.');
      return;
    }

    sendSmsCommand(recipient, message);
  });

  clearWebLogs.addEventListener('click', () => {
    commandList.innerHTML = `
      <div class="empty-state">
        <span class="material-symbols-outlined">chat_bubble_outline</span>
        <p>No SMS commands in current session.</p>
      </div>`;
  });
}

function updateCharCounter() {
  const len = smsMessage.value.length;
  const segments = Math.ceil(len / 160) || 1;
  charCounter.textContent = `${len} / 160 chars (${segments} SMS)`;
}

function checkFirebaseReady() {
  if (!db) {
    alert('Please configure your Firebase credentials first by clicking "Firebase Setup" at top right.');
    configModal.classList.add('active');
    return false;
  }
  return true;
}

// QR Code Scanner Handlers
function startQrScanner() {
  html5QrCodeScanner = new Html5Qrcode('qr-reader');
  const config = { fps: 10, qrbox: { width: 250, height: 250 } };

  html5QrCodeScanner.start(
    { facingMode: 'environment' },
    config,
    (decodedText) => {
      // Handle QR Decoded
      console.log('QR Decoded:', decodedText);
      handleQrDecoded(decodedText);
      stopQrScanner();
      scannerModal.classList.remove('active');
    },
    (errorMessage) => {
      // scanning error/progress, ignore
    }
  ).catch(err => {
    console.error('Camera QR start error:', err);
    alert('Could not start camera scanner. Please check camera permissions or use Manual Pairing.');
    scannerModal.classList.remove('active');
  });
}

function stopQrScanner() {
  if (html5QrCodeScanner) {
    html5QrCodeScanner.stop().catch(() => {}).then(() => {
      html5QrCodeScanner.clear();
      html5QrCodeScanner = null;
    });
  }
}

function handleQrDecoded(qrString) {
  try {
    const payload = JSON.parse(qrString);
    if (!payload.deviceId || !payload.token) {
      throw new Error('Invalid QR payload format');
    }
    
    // Check expiration
    if (payload.expiresAt && Date.now() > payload.expiresAt) {
      alert('This pairing QR code has expired. Please refresh the QR code on your phone and try again.');
      return;
    }

    initiatePairingHandshake(payload.deviceId, payload.token, payload.deviceName);
  } catch (e) {
    alert('Unrecognized QR code format. Please scan a valid QR Code generated by SMS Bridge Android app.');
  }
}

// Send Pairing Handshake Request to Firestore
async function initiatePairingHandshake(deviceId, token, deviceName) {
  try {
    const requesterId = 'WEB-' + Math.random().toString(36).substring(2, 8).toUpperCase();
    const browserInfo = `${navigator.userAgentData?.platform || navigator.platform} (${navigator.userAgent.includes('Chrome') ? 'Chrome' : 'Browser'})`;

    // Write handshake request to device doc
    await db.collection('devices').document(deviceId).set({
      pendingRequesterId: requesterId,
      pendingPairingToken: token,
      pendingClientInfo: `Web Dashboard on ${browserInfo}`,
      pendingRequestedAt: Date.now()
    }, { merge: true });

    alert(`Pairing request sent! Please check your phone screen and tap "Allow Connection".`);

    // Listen for device pairing approval
    listenForPairingApproval(deviceId, requesterId, deviceName);
  } catch (e) {
    console.error('Handshake error:', e);
    alert('Failed to send pairing handshake: ' + e.message);
  }
}

function listenForPairingApproval(deviceId, requesterId, fallbackName) {
  const unsubscribe = db.collection('devices').document(deviceId)
    .onSnapshot(doc => {
      if (!doc.exists) return;
      const data = doc.data();

      if (data.paired === true) {
        // Paired confirmed!
        unsubscribe();
        currentDeviceId = deviceId;
        localStorage.setItem('sms_bridge_device_id', deviceId);
        
        attachDeviceListeners(deviceId);
        alert('Pairing successful! Your phone is now connected.');
      }
    }, err => {
      console.error('Listen error:', err);
    });

  // Timeout listener after 2 minutes
  setTimeout(() => unsubscribe(), 120000);
}

// Attach Live Firestore Listeners
function attachDeviceListeners(deviceId) {
  if (!db) return;

  // Cleanup old listeners
  if (deviceListenerUnsubscribe) deviceListenerUnsubscribe();
  if (commandsListenerUnsubscribe) commandsListenerUnsubscribe();

  // Listen to device presence & paired state
  deviceListenerUnsubscribe = db.collection('devices').document(deviceId)
    .onSnapshot(doc => {
      if (!doc.exists) {
        handleDisconnect();
        return;
      }

      const data = doc.data();
      if (!data.paired) {
        handleDisconnect();
        return;
      }

      renderPairedView(deviceId, data);
    }, err => {
      console.error('Device listener error:', err);
    });

  // Listen to command changes
  commandsListenerUnsubscribe = db.collection('commands')
    .document(deviceId)
    .collection('messages')
    .orderBy('createdAt', 'desc')
    .limit(20)
    .onSnapshot(snapshot => {
      renderCommandList(snapshot);
    }, err => {
      console.error('Commands listener error:', err);
    });
}

function renderPairedView(deviceId, data) {
  unpairedView.classList.remove('active');
  pairedView.classList.add('active');
  sendSmsBtn.disabled = false;

  connectedDeviceId.textContent = deviceId;
  connectedDeviceName.textContent = data.deviceName || 'Android Device';

  const isOnline = data.online === true && (Date.now() - (data.lastSeen || 0) < 60000);
  
  if (isOnline) {
    connectionBadge.className = 'badge badge-online';
    connectionBadge.textContent = '🟢 Connected';
    deviceLiveStatus.className = 'status-val text-success';
    deviceLiveStatus.textContent = '🟢 Online';
  } else {
    connectionBadge.className = 'badge badge-offline';
    connectionBadge.textContent = '🔴 Offline';
    deviceLiveStatus.className = 'status-val text-danger';
    deviceLiveStatus.textContent = '🔴 Offline (No Heartbeat)';
  }

  if (data.lastSeen) {
    const diffSec = Math.floor((Date.now() - data.lastSeen) / 1000);
    deviceLastSeen.textContent = diffSec < 60 ? 'Just now' : `${Math.floor(diffSec / 60)}m ago`;
  }
}

async function handleDisconnect() {
  if (currentDeviceId && db) {
    try {
      await db.collection('devices').document(currentDeviceId).set({
        paired: false,
        pairedClient: ''
      }, { merge: true });
    } catch (e) {
      console.warn('Disconnect error:', e);
    }
  }

  if (deviceListenerUnsubscribe) deviceListenerUnsubscribe();
  if (commandsListenerUnsubscribe) commandsListenerUnsubscribe();

  currentDeviceId = null;
  localStorage.removeItem('sms_bridge_device_id');

  pairedView.classList.remove('active');
  unpairedView.classList.add('active');
  connectionBadge.className = 'badge badge-offline';
  connectionBadge.textContent = '🔴 Disconnected';
  sendSmsBtn.disabled = true;
}

// Send SMS Command via Firestore Queue
async function sendSmsCommand(recipient, message) {
  if (!currentDeviceId || !db) return;

  sendSmsBtn.disabled = true;
  sendSmsBtn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span> Sending...`;

  try {
    const commandId = 'CMD-' + Date.now() + '-' + Math.random().toString(36).substring(2, 6);
    
    await db.collection('commands')
      .document(currentDeviceId)
      .collection('messages')
      .document(commandId)
      .set({
        commandId: commandId,
        type: 'SMS',
        recipient: recipient,
        message: message,
        status: 'pending',
        createdAt: Date.now()
      });

    // Reset inputs
    recipientNumber.value = '';
    smsMessage.value = '';
    updateCharCounter();
  } catch (e) {
    console.error('Error sending SMS command:', e);
    alert('Failed to dispatch SMS command: ' + e.message);
  } finally {
    sendSmsBtn.disabled = false;
    sendSmsBtn.innerHTML = `<span class="material-symbols-outlined">send</span> Send SMS via Phone`;
  }
}

// Render Command History & Live Badges
function renderCommandList(snapshot) {
  if (snapshot.empty) {
    commandList.innerHTML = `
      <div class="empty-state">
        <span class="material-symbols-outlined">chat_bubble_outline</span>
        <p>No SMS commands in activity log.</p>
      </div>`;
    return;
  }

  let html = '';
  snapshot.forEach(doc => {
    const cmd = doc.data();
    const status = (cmd.status || 'pending').toLowerCase();
    
    let badgeClass = 'badge-pending';
    let statusLabel = '⏳ PENDING';
    if (status === 'sent') {
      badgeClass = 'badge-sent';
      statusLabel = '✅ SENT';
    } else if (status === 'failed') {
      badgeClass = 'badge-failed';
      statusLabel = '❌ FAILED';
    }

    const timeStr = new Date(cmd.createdAt || Date.now()).toLocaleTimeString();

    html += `
      <div class="command-card">
        <div class="command-top">
          <span class="command-recipient">To: ${escapeHtml(cmd.recipient)}</span>
          <span class="command-badge ${badgeClass}">${statusLabel}</span>
        </div>
        <div class="command-msg">${escapeHtml(cmd.message)}</div>
        ${cmd.error ? `<div style="color:var(--danger); font-size:11px;">Error: ${escapeHtml(cmd.error)}</div>` : ''}
        <div class="command-meta">
          <span>ID: ${escapeHtml(cmd.commandId || doc.id)}</span>
          <span>${timeStr}</span>
        </div>
      </div>
    `;
  });

  commandList.innerHTML = html;
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
