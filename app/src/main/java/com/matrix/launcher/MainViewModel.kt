package com.matrix.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.launcher.core.AppMatcher
import com.matrix.launcher.core.AppRef
import com.matrix.launcher.core.CommandParser
import com.matrix.launcher.core.LocalAssistant
import com.matrix.launcher.core.PinHasher
import com.matrix.launcher.core.MatchResult
import com.matrix.launcher.core.NetRequest
import com.matrix.launcher.core.TerminalAction
import com.matrix.launcher.core.TerminalEnv
import com.matrix.launcher.core.VoiceCommandInterpreter
import com.matrix.launcher.core.VoiceIntent
import com.matrix.launcher.data.AppInfo
import com.matrix.launcher.data.AppRepository
import com.matrix.launcher.data.AppShortcut
import com.matrix.launcher.data.DeviceStatus
import com.matrix.launcher.data.DockEntry
import com.matrix.launcher.data.LauncherSettings
import com.matrix.launcher.data.PinState
import com.matrix.launcher.system.RemoteAi
import android.os.SystemClock
import com.matrix.launcher.data.SettingsRepository
import com.matrix.launcher.net.NetToolkit
import com.matrix.launcher.system.SystemStatusReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Overlay { NONE, DRAWER, TERMINAL, RADAR, DIAGNOSTICS, SETTINGS, WIDGETS, SHOW, PRIVATE, ASSISTANT, NETWORK }
enum class VoiceState { IDLE, LISTENING }

private const val PANELS = 3

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val appRepo = AppRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val statusReader = SystemStatusReader(application)

    /** Network discovery / diagnostics / port checks. One operation at a time; everything stays on the device. */
    val net = NetToolkit(application, viewModelScope)
    private var pendingNet: NetRequest? = null

    val settings: StateFlow<LauncherSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())
    val favorites: StateFlow<List<String>> =
        settingsRepo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents: StateFlow<List<String>> =
        settingsRepo.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val widgetIds: StateFlow<List<Int>> =
        settingsRepo.widgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    val dock: StateFlow<List<DockEntry>> =
        _apps.map { appRepo.dock(it) }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _appError = MutableStateFlow<String?>(null)
    val appError: StateFlow<String?> = _appError.asStateFlow()

    private val _status = MutableStateFlow<DeviceStatus?>(null)
    val status: StateFlow<DeviceStatus?> = _status.asStateFlow()

    private val _overlay = MutableStateFlow(Overlay.NONE)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    private val _focusSearch = MutableStateFlow(false)
    val focusSearch: StateFlow<Boolean> = _focusSearch.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _terminal = MutableStateFlow(listOf("MATRIX TERMINAL // TYPE HELP"))
    val terminal: StateFlow<List<String>> = _terminal.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _boot = MutableStateFlow(false)
    val boot: StateFlow<Boolean> = _boot.asStateFlow()

    private val _voice = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voice.asStateFlow()

    private val _voiceExplain = MutableStateFlow(false)
    val voiceExplain: StateFlow<Boolean> = _voiceExplain.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _menuApp = MutableStateFlow<AppInfo?>(null)
    val menuApp: StateFlow<AppInfo?> = _menuApp.asStateFlow()

    // ---------- home panels (0 = reactor, 1 = globe, 2 = radar) ----------
    private val _panel = MutableStateFlow(0)
    val panel: StateFlow<Int> = _panel.asStateFlow()
    fun setPanel(i: Int) { _panel.value = ((i % PANELS) + PANELS) % PANELS }
    fun swipePanel(delta: Int) = setPanel(_panel.value + delta)

    // ---------- vault (launcher-only PIN) ----------
    val pinState: StateFlow<PinState> =
        settingsRepo.pinState.stateIn(viewModelScope, SharingStarted.Eagerly, PinState.Loading)
    val privateApps: StateFlow<List<String>> =
        settingsRepo.privateApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _privateUnlocked = MutableStateFlow(false)
    val privateUnlocked: StateFlow<Boolean> = _privateUnlocked.asStateFlow()
    private var pinAttempts = 0
    private var pinLockedUntil = 0L

    /** Returns null on success, otherwise a message to show. */
    fun unlockPrivate(pin: String): String? {
        val st = pinState.value as? PinState.Set ?: return "NO PIN SET"
        val now = SystemClock.elapsedRealtime()
        if (now < pinLockedUntil) return "TOO MANY ATTEMPTS - WAIT ${(pinLockedUntil - now + 999) / 1000}S"
        return if (PinHasher.verify(pin, st.salt, st.hash)) {
            pinAttempts = 0
            _privateUnlocked.value = true
            null
        } else {
            pinAttempts++
            if (pinAttempts >= 5) {
                pinAttempts = 0
                pinLockedUntil = now + 30_000
                "TOO MANY ATTEMPTS - LOCKED 30S"
            } else "WRONG PIN"
        }
    }

    fun lockPrivate() { _privateUnlocked.value = false }

    /** Allowed only when no PIN exists yet, or while the vault is unlocked (change PIN). */
    fun setPin(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        if (pinState.value is PinState.Loading) return false
        if (pinState.value is PinState.Set && !_privateUnlocked.value) return false
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            settingsRepo.setPin(salt, PinHasher.hash(pin, salt))
            _privateUnlocked.value = true
            _message.value = "VAULT PIN SAVED"
        }
        return true
    }

    fun removePin() {
        if (!_privateUnlocked.value) return
        viewModelScope.launch {
            settingsRepo.clearPin()
            _privateUnlocked.value = false
            _message.value = "VAULT PIN REMOVED"
        }
    }

    fun togglePrivateApp(key: String) {
        if (!_privateUnlocked.value) return
        viewModelScope.launch {
            val cur = privateApps.value
            settingsRepo.setPrivateApps(if (key in cur) cur - key else cur + key)
        }
    }

    // ---------- assistant ----------
    val aiKey: StateFlow<String> = settingsRepo.aiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun saveAiKey(k: String) { viewModelScope.launch { settingsRepo.setAiKey(k) } }

    private val _chat = MutableStateFlow(listOf("NEURAL INTERFACE // LOCAL ASSISTANT", "TYPE A REQUEST OR HELP"))
    val chat: StateFlow<List<String>> = _chat.asStateFlow()
    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private fun aiConfigured(): Boolean {
        val s = settings.value
        return s.aiEnabled && s.aiBaseUrl.isNotBlank() && s.aiModel.isNotBlank() && aiKey.value.isNotBlank()
    }

    fun askAssistant(raw: String) {
        val input = raw.filter { !it.isISOControl() }.trim().take(300)
        if (input.isEmpty() || _aiBusy.value) return
        _chat.value = (_chat.value + "> $input").takeLast(200)
        val res = LocalAssistant.reply(input, buildEnv())
        if (res.handled) {
            if (res.action == TerminalAction.Clear) _chat.value = emptyList()
            else _chat.value = (_chat.value + res.lines).takeLast(200)
            val act = res.action
            if (act is TerminalAction.Net) {
                _chat.value = (_chat.value + "NETWORK TOOLS RUN IN THE TERMINAL (THEY NEED YOUR CONFIRMATION). OPENING IT.").takeLast(200)
                _overlay.value = Overlay.TERMINAL
            }
            performAction(res.action)
            return
        }
        if (!aiConfigured()) {
            _chat.value = (_chat.value + LocalAssistant.unhandledMessage(false)).takeLast(200)
            return
        }
        val s = settings.value
        _aiBusy.value = true
        _chat.value = (_chat.value + "REMOTE AI: SENDING YOUR QUESTION TO ${s.aiBaseUrl.trim().take(60)} ...").takeLast(200)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { RemoteAi.ask(s.aiBaseUrl, aiKey.value, s.aiModel, input) }
            val lines = r.fold(
                onSuccess = { it.lines().filter { l -> l.isNotBlank() }.take(20).map { l -> "AI: $l" } },
                onFailure = { listOf("AI REQUEST FAILED: ${(it.message ?: it.javaClass.simpleName).take(80)}") },
            )
            _chat.value = (_chat.value + lines).takeLast(200)
            _aiBusy.value = false
        }
    }

    init {
        net.onLog = { line -> appendTerminal(listOf(line)) }
        viewModelScope.launch {
            val s = runCatching { settingsRepo.settings.first() }.getOrDefault(LauncherSettings())
            _boot.value = s.bootSequence
            _panel.value = s.defaultPanel.coerceIn(0, PANELS - 1)
        }
        refreshApps()
        refreshStatus()
    }

    // ---------- app index ----------
    fun refreshApps() {
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.Default) { appRepo.loadApps() }
                _apps.value = list
                _appError.value = if (list.isEmpty()) "NO LAUNCHABLE APPS FOUND" else null
            } catch (e: Exception) {
                _appError.value = "APP INDEX FAILED: ${e.javaClass.simpleName}"
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { runCatching { statusReader.read() }.getOrNull() }
            if (s != null) _status.value = s
        }
    }

    fun isDefaultHome(): Boolean = appRepo.isDefaultHome()
    fun versionName(): String = appRepo.versionName()

    // ---------- launching ----------
    fun launch(app: AppInfo) {
        if (appRepo.launch(app)) {
            viewModelScope.launch { settingsRepo.pushRecent(app.key) }
            _overlay.value = Overlay.NONE
            _query.value = ""
        } else {
            _message.value = "APP UNAVAILABLE: ${app.label}"
            refreshApps()
        }
    }

    private fun launchKey(key: String) {
        val app = _apps.value.firstOrNull { it.key == key }
        if (app != null) launch(app) else _message.value = "APP UNAVAILABLE"
    }

    fun openAppInfo(app: AppInfo) {
        if (!appRepo.openAppInfo(app)) _message.value = "CAN'T OPEN APP INFO"
    }

    fun uninstall(app: AppInfo) {
        if (!appRepo.uninstall(app)) _message.value = "CAN'T START UNINSTALL"
    }

    fun openMenuFor(app: AppInfo) { _menuApp.value = app }
    fun closeMenu() { _menuApp.value = null }

    // ---------- navigation ----------
    fun openDrawer(focusSearch: Boolean) {
        _focusSearch.value = focusSearch
        _overlay.value = Overlay.DRAWER
        refreshApps()
    }

    fun openOverlay(o: Overlay) {
        if (o == Overlay.DRAWER) openDrawer(false) else _overlay.value = o
    }

    fun back() {
        if (_overlay.value == Overlay.PRIVATE) _privateUnlocked.value = false
        if (_overlay.value != Overlay.NONE) {
            if (_overlay.value == Overlay.DRAWER) _query.value = ""
            _overlay.value = Overlay.NONE
        }
    }

    fun goHome() {
        _privateUnlocked.value = false
        _query.value = ""
        _overlay.value = Overlay.NONE
        _menuApp.value = null
    }

    fun setQuery(q: String) { _query.value = q.take(60) }
    fun dismissBoot() { _boot.value = false }
    fun postMessage(m: String) { _message.value = m }
    fun clearMessage() { _message.value = null }

    fun startScan() {
        if (_scanning.value) return
        _scanning.value = true
        refreshStatus()
        viewModelScope.launch {
            delay(3500)
            _scanning.value = false
            _message.value = "LOCAL TELEMETRY REFRESHED"
        }
    }

    // ---------- settings ----------
    fun updateSettings(block: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { settingsRepo.update(block) }
    }

    fun resetSettings() {
        updateSettings { LauncherSettings() }
        _message.value = "DEFAULT SETTINGS RESTORED"
    }

    fun addWidget(id: Int) {
        viewModelScope.launch {
            settingsRepo.setWidgets(widgetIds.value.filter { it != id } + id)
            _message.value = "WIDGET ADDED"
        }
    }

    fun removeWidget(id: Int) {
        viewModelScope.launch { settingsRepo.setWidgets(widgetIds.value.filter { it != id }) }
    }

    fun startShortcut(s: AppShortcut) {
        if (appRepo.startShortcut(s)) {
            _overlay.value = Overlay.NONE
            _query.value = ""
        } else {
            _message.value = "SHORTCUT UNAVAILABLE"
        }
    }

    fun moveFavorite(key: String, delta: Int) {
        viewModelScope.launch {
            val cur = favorites.value.toMutableList()
            val i = cur.indexOf(key)
            val j = i + delta
            if (i >= 0 && j in cur.indices) {
                cur.removeAt(i)
                cur.add(j, key)
                settingsRepo.setFavorites(cur)
            }
        }
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch {
            val cur = favorites.value
            settingsRepo.setFavorites(if (key in cur) cur - key else cur + key)
        }
    }

    // ---------- voice ----------
    fun showVoiceExplain() { _voiceExplain.value = true }
    fun dismissVoiceExplain() { _voiceExplain.value = false }
    fun setVoiceState(s: VoiceState) { _voice.value = s }

    fun handleVoice(text: String) {
        if (_overlay.value == Overlay.ASSISTANT) {
            askAssistant(text)
            return
        }
        when (val intent = VoiceCommandInterpreter.interpret(text)) {
            is VoiceIntent.OpenApp -> openByName(intent.name)
            is VoiceIntent.Search -> {
                _query.value = intent.query
                openDrawer(true)
            }
            VoiceIntent.ShowBattery -> _message.value = buildEnv().battery()
            VoiceIntent.OpenTerminal -> _overlay.value = Overlay.TERMINAL
            VoiceIntent.OpenSettings -> _overlay.value = Overlay.SETTINGS
            is VoiceIntent.Unknown -> _message.value = "VOICE: UNRECOGNIZED \"${intent.heard.take(40)}\""
        }
    }

    private fun openByName(name: String) {
        when (val m = AppMatcher.match(name, _apps.value.map { it.ref() })) {
            MatchResult.None -> _message.value = "VOICE: NO APP MATCHES \"$name\""
            is MatchResult.Single -> launchKey(m.app.key)
            is MatchResult.Multiple -> {
                _message.value = "AMBIGUOUS: " + m.apps.take(4).joinToString(", ") { it.label } + " - PICK ONE"
                _query.value = name
                openDrawer(false)
            }
        }
    }

    // ---------- terminal ----------
    private fun buildEnv(): TerminalEnv {
        val s = _status.value ?: runCatching { statusReader.read() }.getOrNull()
        val st = settings.value
        val appList = _apps.value
        val isHome = appRepo.isDefaultHome()
        val version = appRepo.versionName()
        return object : TerminalEnv {
            override val apps: List<AppRef> = appList.map { it.ref() }
            override fun time(): String = LocalTime.now().format(
                DateTimeFormatter.ofPattern(if (st.is24h) "HH:mm:ss" else "hh:mm:ss a", Locale.getDefault())
            )
            override fun date(): String = LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.getDefault())
            )
            override fun battery(): String = "BATTERY: " + (s?.batteryText() ?: "UNAVAILABLE")
            override fun storage(): String = "STORAGE: " + (s?.storageText() ?: "UNAVAILABLE")
            override fun network(): String = "NETWORK: " + (s?.networkText() ?: "UNKNOWN")
            override fun currentTheme(): String = st.theme
            override fun status(): List<String> = listOf(
                "LAUNCHER: MATRIX CYBER LAUNCHER v$version",
                "DEFAULT HOME: " + if (isHome) "YES" else "NO",
                "APPS INDEXED: ${appList.size}",
                "NETWORK: " + (s?.networkText() ?: "UNKNOWN"),
                "BATTERY: " + (s?.batteryText() ?: "UNAVAILABLE"),
                "RAM: " + (s?.memText() ?: "UNKNOWN"),
                "UPTIME: " + (s?.uptimeText() ?: "UNKNOWN"),
            )
        }
    }

    private fun appendTerminal(lines: List<String>) {
        if (lines.isEmpty()) return
        _terminal.value = (_terminal.value + lines).takeLast(400)
    }

    fun submitTerminal(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        if (_history.value.lastOrNull() != input) _history.value = (_history.value + input).takeLast(50)
        val echo = "> " + input.filter { !it.isISOControl() }.take(CommandParser.MAX_INPUT)

        // A network test is waiting for the user's authorization answer.
        val pending = pendingNet
        if (pending != null) {
            pendingNet = null
            appendTerminal(listOf(echo))
            if (input.equals("YES", ignoreCase = true)) {
                runNetRequest(pending)
            } else {
                appendTerminal(listOf("NOT CONFIRMED. NO NETWORK ACTIVITY WAS STARTED."))
            }
            return
        }

        val res = CommandParser.execute(input, buildEnv())
        if (res.action == TerminalAction.Clear) {
            _terminal.value = emptyList()
        } else {
            _terminal.value = (_terminal.value + echo + res.lines).takeLast(400)
        }
        performAction(res.action)
    }

    // ---------- network toolkit (terminal side) ----------

    private fun describeNet(r: NetRequest): String = when (r) {
        is NetRequest.Discover -> "PROBE EVERY ADDRESS IN ${r.range ?: "YOUR CURRENT SUBNET"} (PING + A FEW TCP CONNECTS)"
        NetRequest.Diagnostics -> "RUN DNS, LATENCY AND REACHABILITY TESTS (CONTACTS EXAMPLE.COM AND 1.1.1.1)"
        is NetRequest.PortCheck -> "TRY TCP CONNECTIONS TO ${r.host} ON ${r.ports.size} PORT(S)"
        else -> "RUN A NETWORK TEST"
    }

    private fun handleNet(r: NetRequest) {
        when (r) {
            NetRequest.Status -> appendTerminal(net.statusLines())
            NetRequest.ScanStatus -> appendTerminal(net.scanStatusLines())
            NetRequest.ScanCancel -> appendTerminal(listOf(if (net.cancel()) "CANCEL REQUESTED." else "NOTHING IS RUNNING."))
            NetRequest.Report -> viewModelScope.launch {
                val lines = withContext(Dispatchers.IO) { net.saveReportLines() }
                appendTerminal(lines)
            }
            is NetRequest.Dns -> runNetRequest(r) // plain name lookup, not an active scan
            else -> {
                if (net.activeOp.value != com.matrix.launcher.net.NetOp.NONE) {
                    appendTerminal(listOf("ANOTHER NETWORK TASK IS RUNNING. USE SCAN STATUS OR SCAN CANCEL."))
                    return
                }
                pendingNet = r
                appendTerminal(
                    listOf(
                        "AUTHORIZATION REQUIRED",
                        "THIS WILL: ${describeNet(r)}",
                        "ONLY TEST NETWORKS AND HOSTS YOU OWN OR HAVE PERMISSION TO TEST.",
                        "TYPE YES TO CONFIRM. ANYTHING ELSE CANCELS.",
                    ),
                )
            }
        }
    }

    private fun runNetRequest(r: NetRequest) {
        val error: String? = when (r) {
            is NetRequest.Discover -> {
                val range = r.range ?: net.suggestedRange()?.toString()
                if (range == null) "NO PRIVATE SUBNET FOUND. GIVE ONE: NETWORK DISCOVER 192.168.1.0/24" else net.startDiscovery(range)
            }
            NetRequest.Diagnostics -> {
                appendTerminal(listOf("DIAGNOSTICS STARTED (CONNECTIVITY, INTERNET, DNS, LATENCY, REACHABILITY)."))
                net.startDiagnostics("example.com", null)
            }
            is NetRequest.Dns -> {
                appendTerminal(listOf("DNS LOOKUP: ${r.host} ..."))
                net.startDiagnostics(r.host, com.matrix.launcher.net.DiagKind.DNS)
            }
            is NetRequest.PortCheck -> net.startPortCheck(r.host, r.ports)
            else -> null
        }
        if (error != null) appendTerminal(listOf(error))
    }

    private fun performAction(action: TerminalAction) {
        when (action) {
            is TerminalAction.Launch -> launchKey(action.key)
            TerminalAction.OpenSettings -> _overlay.value = Overlay.SETTINGS
            TerminalAction.OpenRadar -> _overlay.value = Overlay.RADAR
            TerminalAction.Scan -> startScan()
            TerminalAction.OpenShow -> _overlay.value = Overlay.SHOW
            TerminalAction.OpenGlobe -> { _panel.value = 1; _overlay.value = Overlay.NONE }
            is TerminalAction.SetTheme -> updateSettings { it.copy(theme = action.id) }
            is TerminalAction.Net -> handleNet(action.request)
            else -> Unit
        }
    }
}
