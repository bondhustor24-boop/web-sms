// SMS Bridge Web Dashboard - Multi-Device & SMS Table System

let db = null;
let currentSessionId = localStorage.getItem('sms_bridge_web_session_id') || null;
let qrcodeInstance = null;

let devicesListenerUnsub = null;
let sessionMessagesListenerUnsub = null;
let deviceInboxUnsubs = new Map(); // deviceId -> unsub function

let connectedDevices = [];
let allSmsMessages = [];
let activeDeviceFilter = 'all';
let activeTypeFilter = 'all';
let activeSearchQuery = '';

// DOM Elements
const qrcodeElement = document.getElementById('qrcode');
const currentSessionIdDisplay = document.getElementById('currentSessionIdDisplay');
const copySessionIdBtn = document.getElementById('copySessionIdBtn');
const refreshSessionBtn = document.getElementById('refreshSessionBtn');

const connectedDevicesList = document.getElementById('connectedDevicesList');
const deviceCountBadge = document.getElementById('deviceCountBadge');
const syncAllDevicesBtn = document.getElementById('syncAllDevicesBtn');
const globalSyncStatus = document.getElementById('globalSyncStatus');
const globalSyncText = document.getElementById('globalSyncText');

const smsDataTable = document.getElementById('smsDataTable');
const smsTableBody = document.getElementById('smsTableBody');
const smsTotalCountBadge = document.getElementById('smsTotalCountBadge');
const tableShowingCount = document.getElementById('tableShowingCount');
const deviceFilterSelect = document.getElementById('deviceFilterSelect');
const smsTypeFilter = document.getElementById('smsTypeFilter');
const smsSearchInput = document.getElementById('smsSearchInput');
const clearFiltersBtn = document.getElementById('clearFiltersBtn');
const exportCsvBtn = document.getElementById('exportCsvBtn');

// Modals
const sendSmsModal = document.getElementById('sendSmsModal');
const sendSmsModalBtn = document.getElementById('sendSmsModalBtn');
const closeSendSmsBtn = document.getElementById('closeSendSmsBtn');
const cancelSendSmsBtn = document.getElementById('cancelSendSmsBtn');
const composeSmsForm = document.getElementById('composeSmsForm');
const modalDeviceSelect = document.getElementById('modalDeviceSelect');
const modalRecipientNumber = document.getElementById('modalRecipientNumber');
const modalSmsMessage = document.getElementById('modalSmsMessage');
const modalCharCounter = document.getElementById('modalCharCounter');
const submitSendSmsBtn = document.getElementById('submitSendSmsBtn');
const manualAddSmsBtn = document.getElementById('manualAddSmsBtn');

const configModal = document.getElementById('configModal');
const configBtn = document.getElementById('configBtn');
const closeConfigBtn = document.getElementById('closeConfigBtn');
const firebaseConfigInput = document.getElementById('firebaseConfigInput');
const saveConfigBtn = document.getElementById('saveConfigBtn');
const resetConfigBtn = document.getElementById('resetConfigBtn');
const toastContainer = document.getElementById('toastContainer');

// Default Firebase Configuration
const DEFAULT_FIREBASE_CONFIG = {
  apiKey: "AIzaSyDummyKeyReplaceIfUsingAuth",
  authDomain: "sms-bridge-app.firebaseapp.com",
  projectId: "sms-bridge-app",
  storageBucket: "sms-bridge-app.appspot.com",
  messagingSenderId: "469402031854",
  appId: "1:469402031854:web:smsbridgeapp"
};

// Initialize Application
document.addEventListener('DOMContentLoaded', () => {
  initFirebase();
  ensureSessionId();
  generateSessionQrCode();
  setupEventListeners();
  
  if (db && currentSessionId) {
    attachSessionListeners();
  }
});

// Initialize Firebase
function initFirebase() {
  const storedConfig = localStorage.getItem('sms_bridge_firebase_config');
  let config = DEFAULT_FIREBASE_CONFIG;
  if (storedConfig) {
    try {
      config = JSON.parse(storedConfig);
    } catch (e) {
      console.warn('Error parsing stored Firebase config:', e);
    }
  }

  try {
    if (!firebase.apps.length) {
      firebase.initializeApp(config);
    }
    db = firebase.firestore();
    console.log('Firebase initialized with project:', config.projectId);
  } catch (err) {
    console.error('Firebase initialization error:', err);
    showToast('Firebase connection warning. Check configuration.', 'warning');
  }
}

// Ensure Web Session ID
function ensureSessionId() {
  if (!currentSessionId) {
    currentSessionId = generateRandomSessionId();
    localStorage.setItem('sms_bridge_web_session_id', currentSessionId);
  }
  if (currentSessionIdDisplay) {
    currentSessionIdDisplay.textContent = currentSessionId;
  }
}

function generateRandomSessionId() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let rand = '';
  for (let i = 0; i < 6; i++) {
    rand += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return `SES-${rand}`;
}

// Generate QR Code containing the Web Session ID
function generateSessionQrCode() {
  if (!qrcodeElement) return;

  const payload = JSON.stringify({
    sessionId: currentSessionId,
    type: "web_session",
    client: "SMS Bridge Web Dashboard",
    createdAt: Date.now()
  });

  qrcodeElement.innerHTML = '';
  try {
    qrcodeInstance = new QRCode(qrcodeElement, {
      text: payload,
      width: 186,
      height: 186,
      colorDark: "#1c1b1f",
      colorLight: "#ffffff",
      correctLevel: QRCode.CorrectLevel.M
    });
  } catch (e) {
    console.error('QR code generation error:', e);
  }
}

// Setup Event Listeners
function setupEventListeners() {
  // Session Refresh
  refreshSessionBtn.addEventListener('click', () => {
    if (confirm('Generate a new Web Session ID? Any previously linked devices will need to scan the new QR code.')) {
      currentSessionId = generateRandomSessionId();
      localStorage.setItem('sms_bridge_web_session_id', currentSessionId);
      currentSessionIdDisplay.textContent = currentSessionId;
      generateSessionQrCode();
      
      // Reattach listeners
      connectedDevices = [];
      allSmsMessages = [];
      renderDevicesList();
      renderSmsTable();
      attachSessionListeners();
      showToast('New Session QR Code generated!', 'success');
    }
  });

  // Copy Session ID
  copySessionIdBtn.addEventListener('click', () => {
    navigator.clipboard.writeText(currentSessionId).then(() => {
      showToast(`Session ID ${currentSessionId} copied to clipboard!`, 'info');
    });
  });

  // Sync All Devices
  syncAllDevicesBtn.addEventListener('click', async () => {
    if (connectedDevices.length === 0) {
      showToast('No Android phones connected to sync.', 'warning');
      return;
    }
    syncAllConnectedDevices();
  });

  // Filters & Search
  deviceFilterSelect.addEventListener('change', (e) => {
    activeDeviceFilter = e.target.value;
    renderSmsTable();
  });

  smsTypeFilter.addEventListener('change', (e) => {
    activeTypeFilter = e.target.value;
    renderSmsTable();
  });

  smsSearchInput.addEventListener('input', (e) => {
    activeSearchQuery = e.target.value.trim().toLowerCase();
    renderSmsTable();
  });

  clearFiltersBtn.addEventListener('click', () => {
    smsSearchInput.value = '';
    smsTypeFilter.value = 'all';
    deviceFilterSelect.value = 'all';
    activeSearchQuery = '';
    activeTypeFilter = 'all';
    activeDeviceFilter = 'all';
    renderSmsTable();
  });

  exportCsvBtn.addEventListener('click', exportTableToCsv);

  // Send SMS Modal
  sendSmsModalBtn.addEventListener('click', () => {
    updateModalDeviceOptions();
    sendSmsModal.classList.add('active');
    modalRecipientNumber.focus();
  });

  closeSendSmsBtn.addEventListener('click', () => {
    sendSmsModal.classList.remove('active');
  });

  cancelSendSmsBtn.addEventListener('click', () => {
    sendSmsModal.classList.remove('active');
  });

  modalSmsMessage.addEventListener('input', () => {
    const len = modalSmsMessage.value.length;
    const parts = Math.ceil(len / 160) || 1;
    modalCharCounter.textContent = `${len} / 160 chars (${parts} SMS)`;
  });

  composeSmsForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const deviceId = modalDeviceSelect.value;
    const recipient = modalRecipientNumber.value.trim();
    const message = modalSmsMessage.value.trim();

    if (!deviceId) {
      alert('Please select a connected Android phone to dispatch this SMS.');
      return;
    }
    if (!recipient || !message) return;

    await dispatchSmsFromDevice(deviceId, recipient, message);
  });

  // Manual Add SMS Button (for testing or logging)
  manualAddSmsBtn.addEventListener('click', () => {
    const num = prompt('Enter Contact / Phone Number (e.g. +1234567890):', '+18005550199');
    if (!num) return;
    const body = prompt('Enter SMS message content:', 'Hello from SMS Bridge web dashboard!');
    if (!body) return;

    const dummyMsg = {
      id: 'MANUAL-' + Date.now(),
      smsId: 'MANUAL-' + Date.now(),
      address: num,
      body: body,
      type: 'sent',
      timestamp: Date.now(),
      deviceName: connectedDevices[0]?.deviceName || 'Web Dashboard',
      deviceId: connectedDevices[0]?.deviceId || 'MANUAL-DEV',
      read: true
    };

    allSmsMessages.unshift(dummyMsg);
    renderSmsTable();
    showToast('Manual SMS record added to table', 'info');
  });

  // Firebase Config Modal
  configBtn.addEventListener('click', () => {
    const stored = localStorage.getItem('sms_bridge_firebase_config');
    firebaseConfigInput.value = stored || JSON.stringify(DEFAULT_FIREBASE_CONFIG, null, 2);
    configModal.classList.add('active');
  });

  closeConfigBtn.addEventListener('click', () => {
    configModal.classList.remove('active');
  });

  resetConfigBtn.addEventListener('click', () => {
    firebaseConfigInput.value = JSON.stringify(DEFAULT_FIREBASE_CONFIG, null, 2);
  });

  saveConfigBtn.addEventListener('click', () => {
    try {
      const parsed = JSON.parse(firebaseConfigInput.value.trim());
      localStorage.setItem('sms_bridge_firebase_config', JSON.stringify(parsed, null, 2));
      showToast('Firebase settings updated. Reloading...', 'success');
      setTimeout(() => location.reload(), 1000);
    } catch (err) {
      alert('Invalid JSON format. Please paste valid configuration JSON.');
    }
  });
}

// Attach Live Firestore Listeners for this Web Session
function attachSessionListeners() {
  if (!db || !currentSessionId) return;

  // Cleanup old listeners
  if (devicesListenerUnsub) devicesListenerUnsub();
  if (sessionMessagesListenerUnsub) sessionMessagesListenerUnsub();
  deviceInboxUnsubs.forEach(unsub => unsub());
  deviceInboxUnsubs.clear();

  // 1. Listen to Connected Devices subcollection: sessions/{sessionId}/devices
  devicesListenerUnsub = db.collection('sessions')
    .doc(currentSessionId)
    .collection('devices')
    .onSnapshot(snapshot => {
      connectedDevices = [];
      snapshot.forEach(doc => {
        connectedDevices.push({
          deviceId: doc.id,
          ...doc.data()
        });
      });

      renderDevicesList();
      updateDeviceFilterOptions();
      updateModalDeviceOptions();
      updateGlobalSyncState();

      // Listen to inbox for any newly discovered device
      connectedDevices.forEach(dev => {
        if (!deviceInboxUnsubs.has(dev.deviceId)) {
          attachDeviceInboxListener(dev.deviceId, dev.deviceName);
        }
      });
    }, err => {
      console.warn('Devices subcollection listener error:', err.message);
    });

  // 2. Listen to Unified Session Messages: sessions/{sessionId}/messages
  sessionMessagesListenerUnsub = db.collection('sessions')
    .doc(currentSessionId)
    .collection('messages')
    .orderBy('timestamp', 'desc')
    .limit(300)
    .onSnapshot(snapshot => {
      snapshot.docChanges().forEach(change => {
        const data = change.doc.data();
        const msg = {
          id: change.doc.id,
          ...data
        };

        if (change.type === 'added') {
          upsertMessage(msg);
        } else if (change.type === 'modified') {
          upsertMessage(msg);
        } else if (change.type === 'removed') {
          allSmsMessages = allSmsMessages.filter(m => m.id !== change.doc.id && m.smsId !== data.smsId);
        }
      });

      renderSmsTable();
    }, err => {
      console.warn('Session messages listener error:', err.message);
    });
}

// Listen to individual device inbox for multi-device sync
function attachDeviceInboxListener(deviceId, fallbackName) {
  if (!db) return;

  const unsub = db.collection('inbox')
    .doc(deviceId)
    .collection('messages')
    .orderBy('timestamp', 'desc')
    .limit(150)
    .onSnapshot(snapshot => {
      snapshot.forEach(doc => {
        const data = doc.data();
        upsertMessage({
          id: doc.id,
          deviceId: deviceId,
          deviceName: data.deviceName || fallbackName || deviceId,
          ...data
        });
      });
      renderSmsTable();
    }, err => {
      console.warn(`Inbox listener for ${deviceId} notice:`, err.message);
    });

  deviceInboxUnsubs.set(deviceId, unsub);
}

// Upsert Message into master list (deduplicated)
function upsertMessage(newMsg) {
  const identifier = newMsg.smsId || newMsg.id || `${newMsg.timestamp}_${newMsg.address}_${newMsg.body}`;
  const existingIdx = allSmsMessages.findIndex(m => 
    (m.smsId && m.smsId === newMsg.smsId) || 
    (m.id && m.id === newMsg.id) ||
    (`${m.timestamp}_${m.address}_${m.body}` === identifier)
  );

  if (existingIdx >= 0) {
    allSmsMessages[existingIdx] = { ...allSmsMessages[existingIdx], ...newMsg };
  } else {
    allSmsMessages.push(newMsg);
  }

  // Sort descending by timestamp
  allSmsMessages.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
}

// Render Connected Devices Grid
function renderDevicesList() {
  deviceCountBadge.textContent = `${connectedDevices.length} Phone${connectedDevices.length === 1 ? '' : 's'}`;

  if (connectedDevices.length === 0) {
    connectedDevicesList.innerHTML = `
      <div class="empty-devices-placeholder">
        <span class="material-symbols-outlined">phonelink_off</span>
        <p>No Android devices linked yet.</p>
        <small>Open the SMS Bridge app on your phone and scan the QR code on the left.</small>
      </div>`;
    return;
  }

  let html = '';
  connectedDevices.forEach(dev => {
    const isOnline = dev.online !== false && (Date.now() - (dev.lastSeen || 0) < 90000);
    const statusClass = isOnline ? 'online' : 'offline';
    const statusText = isOnline ? '🟢 Online' : '🔴 Offline';
    const devName = dev.deviceName || 'Android Phone';
    const devId = dev.deviceId || 'DEV-UNKNOWN';
    const smsCount = allSmsMessages.filter(m => m.deviceId === devId).length;

    html += `
      <div class="device-item-card ${isOnline ? 'is-online' : ''}">
        <div class="device-card-top">
          <div class="device-info-left">
            <div class="device-icon-box">
              <span class="material-symbols-outlined">smartphone</span>
            </div>
            <div>
              <div class="device-name-title">${escapeHtml(devName)}</div>
              <div class="device-sub-id">${escapeHtml(devId)}</div>
            </div>
          </div>
          <span class="device-status-badge ${statusClass}">${statusText}</span>
        </div>

        <div class="device-meta-stats">
          <span>Synced: <strong>${smsCount} SMS</strong></span>
          <span>${dev.lastSeen ? formatRelativeTime(dev.lastSeen) : 'Active'}</span>
        </div>

        <div class="device-actions-row">
          <button onclick="triggerDeviceSync('${devId}')" class="btn btn-sm btn-outline" style="flex:1;" title="Fetch SMS from this phone">
            <span class="material-symbols-outlined" style="font-size:16px;">sync</span>
            <span>Sync SMS</span>
          </button>
          <button onclick="openComposeForDevice('${devId}')" class="btn btn-sm btn-primary" title="Send SMS from this phone">
            <span class="material-symbols-outlined" style="font-size:16px;">send</span>
          </button>
          <button onclick="removeDeviceFromSession('${devId}')" class="btn btn-sm btn-danger" title="Disconnect device">
            <span class="material-symbols-outlined" style="font-size:16px;">link_off</span>
          </button>
        </div>
      </div>
    `;
  });

  connectedDevicesList.innerHTML = html;
}

// Update Filter Options
function updateDeviceFilterOptions() {
  const currentVal = deviceFilterSelect.value;
  deviceFilterSelect.innerHTML = `<option value="all">📱 All Devices (Unified)</option>`;
  
  connectedDevices.forEach(dev => {
    const opt = document.createElement('option');
    opt.value = dev.deviceId;
    opt.textContent = `📱 ${dev.deviceName || dev.deviceId}`;
    deviceFilterSelect.appendChild(opt);
  });

  deviceFilterSelect.value = currentVal || 'all';
}

function updateModalDeviceOptions() {
  modalDeviceSelect.innerHTML = `<option value="">-- Choose connected Android phone --</option>`;
  connectedDevices.forEach(dev => {
    const opt = document.createElement('option');
    opt.value = dev.deviceId;
    opt.textContent = `📱 ${dev.deviceName || 'Android Phone'} (${dev.deviceId})`;
    modalDeviceSelect.appendChild(opt);
  });

  if (connectedDevices.length > 0) {
    modalDeviceSelect.selectedIndex = 1;
  }
}

// Render Main SMS Table
function renderSmsTable() {
  if (!smsTableBody) return;

  const filtered = allSmsMessages.filter(msg => {
    // Device filter
    if (activeDeviceFilter !== 'all' && msg.deviceId !== activeDeviceFilter) {
      return false;
    }

    // Type filter
    const isSent = msg.type === 'sent' || msg.type === 2 || msg.type === '2';
    if (activeTypeFilter === 'inbox' && isSent) return false;
    if (activeTypeFilter === 'sent' && !isSent) return false;

    // Search query
    if (activeSearchQuery) {
      const addr = (msg.address || '').toLowerCase();
      const body = (msg.body || '').toLowerCase();
      const dName = (msg.deviceName || '').toLowerCase();
      if (!addr.includes(activeSearchQuery) && !body.includes(activeSearchQuery) && !dName.includes(activeSearchQuery)) {
        return false;
      }
    }

    return true;
  });

  smsTotalCountBadge.textContent = `${allSmsMessages.length} Messages`;
  tableShowingCount.textContent = `Showing ${filtered.length} of ${allSmsMessages.length} messages`;

  if (filtered.length === 0) {
    smsTableBody.innerHTML = `
      <tr class="empty-table-row">
        <td colspan="7">
          <div class="empty-table-state">
            <span class="material-symbols-outlined">mark_email_unread</span>
            <p>${allSmsMessages.length === 0 ? 'No SMS messages synced yet' : 'No messages match filter'}</p>
            <small>${allSmsMessages.length === 0 ? 'Connect your Android phone and click "Sync SMS" to load messages.' : 'Try adjusting the search query or filters above.'}</small>
          </div>
        </td>
      </tr>`;
    return;
  }

  let rowsHtml = '';
  filtered.forEach(msg => {
    const isSent = msg.type === 'sent' || msg.type === 2 || msg.type === '2';
    const isUnread = msg.read === false;
    const dateObj = msg.timestamp ? new Date(msg.timestamp) : new Date();
    const dateFormatted = dateObj.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' + dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const relativeTime = formatRelativeTime(msg.timestamp);
    const address = msg.address || 'Unknown';
    const body = msg.body || '';
    const deviceName = msg.deviceName || msg.deviceId || 'Android Phone';
    const deviceId = msg.deviceId || '';

    rowsHtml += `
      <tr class="${isUnread ? 'is-unread' : ''}">
        <td>
          <div class="table-time-cell">
            <span class="date">${dateFormatted}</span>
            <span class="relative">${relativeTime}</span>
          </div>
        </td>
        <td>
          <span class="direction-badge ${isSent ? 'sent' : 'inbox'}">
            <span class="material-symbols-outlined" style="font-size:14px;">${isSent ? 'arrow_outward' : 'call_received'}</span>
            ${isSent ? 'Sent' : 'Inbox'}
          </span>
        </td>
        <td>
          <div class="contact-cell">
            <span class="material-symbols-outlined" style="font-size:16px; color:var(--text-muted);">person</span>
            <span>${escapeHtml(address)}</span>
          </div>
        </td>
        <td>
          <span class="device-badge-pill" title="${escapeHtml(deviceId)}">
            <span class="material-symbols-outlined" style="font-size:14px;">phone_android</span>
            ${escapeHtml(deviceName)}
          </span>
        </td>
        <td>
          <div class="message-body-cell">${escapeHtml(body)}</div>
        </td>
        <td>
          <span style="font-size:11px; font-weight:600; color:${isUnread ? 'var(--primary)' : 'var(--text-muted)'};">
            ${isUnread ? '🔵 Unread' : 'Read'}
          </span>
        </td>
        <td>
          <div class="actions-cell-group">
            <button onclick="quickReply('${escapeHtml(address)}', '${escapeHtml(deviceId)}')" class="btn-icon" title="Quick Reply">
              <span class="material-symbols-outlined" style="font-size:18px;">reply</span>
            </button>
            <button onclick="copyMessageText('${escapeHtml(body)}')" class="btn-icon" title="Copy SMS Text">
              <span class="material-symbols-outlined" style="font-size:18px;">content_copy</span>
            </button>
            <button onclick="deleteSmsRow('${msg.id}')" class="btn-icon" title="Remove row">
              <span class="material-symbols-outlined" style="font-size:18px;">delete</span>
            </button>
          </div>
        </td>
      </tr>
    `;
  });

  smsTableBody.innerHTML = rowsHtml;
}

// Quick Reply
window.quickReply = function(address, deviceId) {
  sendSmsModalBtn.click();
  modalRecipientNumber.value = address;
  if (deviceId) {
    modalDeviceSelect.value = deviceId;
  }
  modalSmsMessage.focus();
};

// Copy Message Text
window.copyMessageText = function(text) {
  navigator.clipboard.writeText(text).then(() => {
    showToast('SMS message text copied!', 'info');
  });
};

// Delete SMS row from view
window.deleteSmsRow = function(id) {
  allSmsMessages = allSmsMessages.filter(m => m.id !== id);
  renderSmsTable();
};

// Trigger SMS Sync on a specific device
window.triggerDeviceSync = async function(deviceId) {
  if (!db || !deviceId) return;
  showToast(`Requesting SMS sync from phone ${deviceId}...`, 'info');

  try {
    const cmdId = 'SYNC-' + Date.now();
    await db.collection('commands')
      .doc(deviceId)
      .collection('messages')
      .doc(cmdId)
      .set({
        commandId: cmdId,
        type: 'LOAD_SMS',
        recipient: 'SELF',
        message: 'Sync phone SMS',
        sessionId: currentSessionId,
        status: 'pending',
        createdAt: Date.now()
      });
    showToast('Sync command sent to phone!', 'success');
  } catch (err) {
    console.error('Sync error:', err);
    showToast('Failed to send sync command: ' + err.message, 'error');
  }
};

// Sync All Connected Devices
async function syncAllConnectedDevices() {
  showToast(`Syncing SMS from ${connectedDevices.length} phones...`, 'info');
  for (const dev of connectedDevices) {
    try {
      const cmdId = 'SYNC-' + Date.now() + '-' + Math.random().toString(36).substring(2, 5);
      await db.collection('commands')
        .doc(dev.deviceId)
        .collection('messages')
        .doc(cmdId)
        .set({
          commandId: cmdId,
          type: 'LOAD_SMS',
          recipient: 'SELF',
          message: 'Sync phone SMS',
          sessionId: currentSessionId,
          status: 'pending',
          createdAt: Date.now()
        });
    } catch (e) {
      console.warn('Sync error for device:', dev.deviceId, e);
    }
  }
  showToast('Sync requests dispatched to all connected devices!', 'success');
}

// Remove Device from Session
window.removeDeviceFromSession = async function(deviceId) {
  if (!confirm(`Disconnect and remove device ${deviceId} from this dashboard session?`)) return;

  if (db && currentSessionId) {
    try {
      await db.collection('sessions')
        .doc(currentSessionId)
        .collection('devices')
        .doc(deviceId)
        .delete();
      
      showToast(`Device ${deviceId} removed.`, 'info');
    } catch (e) {
      console.warn('Remove device error:', e);
    }
  }

  connectedDevices = connectedDevices.filter(d => d.deviceId !== deviceId);
  renderDevicesList();
  updateDeviceFilterOptions();
  updateModalDeviceOptions();
};

// Dispatch SMS from Device
async function dispatchSmsFromDevice(deviceId, recipient, message) {
  if (!db || !deviceId) return;

  submitSendSmsBtn.disabled = true;
  submitSendSmsBtn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span> Dispatching...`;

  try {
    const cmdId = 'CMD-' + Date.now() + '-' + Math.random().toString(36).substring(2, 6);
    await db.collection('commands')
      .doc(deviceId)
      .collection('messages')
      .doc(cmdId)
      .set({
        commandId: cmdId,
        type: 'SMS',
        recipient: recipient,
        message: message,
        sessionId: currentSessionId,
        status: 'pending',
        createdAt: Date.now()
      });

    showToast(`SMS command sent to phone for ${recipient}!`, 'success');
    sendSmsModal.classList.remove('active');
    modalRecipientNumber.value = '';
    modalSmsMessage.value = '';
  } catch (err) {
    console.error('Dispatch error:', err);
    showToast('Failed to dispatch SMS: ' + err.message, 'error');
  } finally {
    submitSendSmsBtn.disabled = false;
    submitSendSmsBtn.innerHTML = `<span class="material-symbols-outlined">send</span> Dispatch SMS Now`;
  }
}

// Export Table to CSV
function exportTableToCsv() {
  if (allSmsMessages.length === 0) {
    showToast('No messages to export.', 'warning');
    return;
  }

  let csvContent = 'data:text/csv;charset=utf-8,';
  csvContent += 'Date,Direction,Contact,Device,Message,Status\n';

  allSmsMessages.forEach(m => {
    const date = new Date(m.timestamp || Date.now()).toISOString();
    const dir = (m.type === 'sent' || m.type === 2) ? 'SENT' : 'INBOX';
    const contact = `"${(m.address || '').replace(/"/g, '""')}"`;
    const device = `"${(m.deviceName || m.deviceId || '').replace(/"/g, '""')}"`;
    const body = `"${(m.body || '').replace(/"/g, '""')}"`;
    const status = m.read === false ? 'Unread' : 'Read';

    csvContent += `${date},${dir},${contact},${device},${body},${status}\n`;
  });

  const encodedUri = encodeURI(csvContent);
  const link = document.createElement('a');
  link.setAttribute('href', encodedUri);
  link.setAttribute('download', `SMS_Bridge_Export_${currentSessionId}_${Date.now()}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  showToast('SMS Table exported as CSV!', 'success');
}

// Update Global Sync Indicator
function updateGlobalSyncState() {
  if (connectedDevices.length > 0) {
    globalSyncStatus.style.background = 'var(--success-light)';
    globalSyncStatus.style.color = 'var(--success)';
    globalSyncText.textContent = `${connectedDevices.length} Phone${connectedDevices.length === 1 ? '' : 's'} Active`;
  } else {
    globalSyncStatus.style.background = 'var(--surface-alt)';
    globalSyncStatus.style.color = 'var(--text-muted)';
    globalSyncText.textContent = 'Waiting for Devices';
  }
}

// Helper: Format Relative Time
function formatRelativeTime(ts) {
  if (!ts) return '';
  const diffSec = Math.floor((Date.now() - ts) / 1000);
  if (diffSec < 60) return 'Just now';
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

// Helper: Toast Notifications
function showToast(message, type = 'info') {
  if (!toastContainer) return;
  const toast = document.createElement('div');
  toast.className = 'toast';
  
  let iconName = 'info';
  if (type === 'success') iconName = 'check_circle';
  if (type === 'warning') iconName = 'warning';
  if (type === 'error') iconName = 'error';

  toast.innerHTML = `
    <span class="material-symbols-outlined" style="font-size:18px;">${iconName}</span>
    <span>${escapeHtml(message)}</span>
  `;
  
  toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(12px)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// Helper: Escape HTML
function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
