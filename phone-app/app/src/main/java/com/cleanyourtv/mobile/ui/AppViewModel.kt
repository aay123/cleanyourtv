package com.cleanyourtv.mobile.ui

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cleanyourtv.mobile.R
import com.cleanyourtv.mobile.adb.AdbSession
import com.cleanyourtv.mobile.adb.DiscoveredEndpoint
import com.cleanyourtv.mobile.adb.MdnsDiscovery
import com.cleanyourtv.mobile.data.Analysis
import com.cleanyourtv.mobile.data.BackupRecord
import com.cleanyourtv.mobile.data.BackupStore
import com.cleanyourtv.mobile.data.DeviceCatalog
import com.cleanyourtv.mobile.data.DeviceInfo
import com.cleanyourtv.mobile.data.RestoreSummary
import com.cleanyourtv.mobile.data.TvList
import com.cleanyourtv.mobile.flow.CleanFlow
import com.cleanyourtv.mobile.flow.FlowReporter
import com.cleanyourtv.mobile.flow.PlanBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

enum class Screen { CONNECT, ANALYZE, RUN, DONE }
enum class RunMode { CLEAN, RESTORE }

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val host: String = "",
    val port: String = "5555",
    val busy: Boolean = false,
    val busyText: String = "",
    val toast: String? = null,
    val error: String? = null,
    val device: DeviceInfo? = null,
    val connected: Boolean = false,
    val lists: List<TvList> = emptyList(),
    val suggestions: List<TvList> = emptyList(),
    val search: String = "",
    val selectedList: TvList? = null,
    val analysis: Analysis? = null,
    val launcherPath: String? = null,
    val launcherName: String? = null,
    val launcherPackage: String? = null,
    val applyDns: Boolean = false,
    val runMode: RunMode = RunMode.CLEAN,
    val runStep: String = "",
    val runProgress: Int = 0,
    val runTotal: Int = 0,
    val logs: List<String> = emptyList(),
    val record: BackupRecord? = null,
    val restoreSummary: RestoreSummary? = null,
    val backups: List<BackupRecord> = emptyList(),
    val discovered: List<DiscoveredEndpoint> = emptyList(),
    val discoveredPairing: List<DiscoveredEndpoint> = emptyList(),
    val mdnsRunning: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val catalog = DeviceCatalog(application)
    private val backups = BackupStore(application)

    private val _state = MutableStateFlow(
        UiState(lists = catalog.lists, backups = backups.list()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var session: AdbSession? = null
    private var lastHost = ""
    private var lastPort = 5555
    private var selectedPackages = emptyList<com.cleanyourtv.mobile.data.ListPackage>()
    private var mdns: MdnsDiscovery? = null
    private var mdnsJob: Job? = null

    fun connect(host: String, portText: String) {
        val cleanHost = host.trim()
        val port = portText.trim().toIntOrNull() ?: 5555
        if (cleanHost.isEmpty()) {
            toast(string(R.string.toast_enter_ip))
            return
        }
        _state.update {
            it.copy(busy = true, busyText = string(R.string.busy_connecting), error = null)
        }
        launchIo {
            val newSession = AdbSession(cleanHost, port)
            try {
                val device = newSession.connect()
                session?.close()
                session = newSession
                lastHost = cleanHost
                lastPort = port
                _state.update {
                    it.copy(
                        busy = false,
                        busyText = "",
                        connected = true,
                        device = device,
                        host = cleanHost,
                        port = port.toString(),
                        suggestions = catalog.suggest(device),
                        screen = Screen.ANALYZE,
                    )
                }
            } catch (error: Throwable) {
                newSession.close()
                throw error
            }
        }
    }

    fun pair(host: String, pairingPortText: String, code: String, connectPortText: String) {
        val cleanHost = host.trim()
        val pairingPort = pairingPortText.trim().toIntOrNull()
        val connectPort = connectPortText.trim().toIntOrNull() ?: 5555
        if (cleanHost.isEmpty() || pairingPort == null || code.trim().length < 6) {
            toast(string(R.string.toast_pair_fields))
            return
        }
        _state.update {
            it.copy(busy = true, busyText = string(R.string.busy_pairing), error = null)
        }
        launchIo {
            AdbSession.pair(cleanHost, pairingPort, code)
            val newSession = AdbSession(cleanHost, connectPort)
            try {
                val device = newSession.connect()
                session?.close()
                session = newSession
                lastHost = cleanHost
                lastPort = connectPort
                _state.update {
                    it.copy(
                        busy = false,
                        busyText = "",
                        connected = true,
                        device = device,
                        host = cleanHost,
                        port = connectPort.toString(),
                        suggestions = catalog.suggest(device),
                        screen = Screen.ANALYZE,
                    )
                }
            } catch (error: Throwable) {
                newSession.close()
                throw error
            }
        }
    }

    fun discover() {
        val app = getApplication<Application>()
        val discovery = mdns ?: MdnsDiscovery(app).also { mdns = it }
        if (_state.value.mdnsRunning) {
            discovery.stop()
            _state.update { it.copy(mdnsRunning = false) }
            return
        }
        _state.update { it.copy(mdnsRunning = true, discovered = emptyList(), discoveredPairing = emptyList()) }
        discovery.start()
        mdnsJob?.cancel()
        mdnsJob = viewModelScope.launch {
            discovery.state.collect { mdnsState ->
                _state.update {
                    it.copy(
                        discovered = mdnsState.connectDevices,
                        discoveredPairing = mdnsState.pairDevices,
                        mdnsRunning = mdnsState.running,
                    )
                }
            }
        }
    }

    fun selectList(id: String) {
        val device = _state.value.device
        val list = catalog.find(id)
        val active = session
        if (device == null || list == null || active == null) {
            toast(string(R.string.toast_connect_first))
            return
        }
        val packages = runCatching { catalog.loadPackages(list) }.getOrElse { error ->
            toast(error.message ?: string(R.string.toast_cannot_read_list))
            return
        }
        selectedPackages = packages
        _state.update {
            it.copy(
                busy = true,
                busyText = string(R.string.busy_checking, list.title),
                selectedList = list,
                analysis = null,
                error = null,
            )
        }
        launchIo {
            val analysis = PlanBuilder(active).analyze(device, list, packages)
            _state.update { it.copy(busy = false, busyText = "", analysis = analysis) }
        }
    }

    fun setSearch(query: String) {
        val device = _state.value.device
        _state.update {
            it.copy(
                search = query,
                suggestions = if (device != null) catalog.suggest(device, query) else emptyList(),
            )
        }
    }

    fun setLauncherApk(uri: Uri) {
        _state.update { it.copy(busy = true, busyText = string(R.string.busy_reading_apk), error = null) }
        launchIo {
            val app = getApplication<Application>()
            val name = displayName(uri) ?: "launcher.apk"
            val target = File(app.cacheDir, "launcher-${System.currentTimeMillis()}.apk")
            app.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException(string(R.string.error_cannot_read_file))
            val packageName = apkPackage(target)
            if (packageName == null) {
                throw IOException(string(R.string.error_not_apk))
            }
            _state.update {
                it.copy(
                    busy = false,
                    busyText = "",
                    launcherPath = target.absolutePath,
                    launcherName = name,
                    launcherPackage = packageName,
                )
            }
        }
    }

    fun clearLauncherApk() {
        _state.update { it.copy(launcherPath = null, launcherName = null, launcherPackage = null) }
    }

    fun setApplyDns(value: Boolean) {
        _state.update { it.copy(applyDns = value) }
    }

    fun startClean() {
        val current = _state.value
        val analysis = current.analysis ?: return
        val active = session ?: run {
            toast(string(R.string.toast_connect_first))
            return
        }
        val launcherFile = current.launcherPath?.let(::File)?.takeIf { it.isFile }
        _state.update {
            it.copy(
                screen = Screen.RUN,
                runMode = RunMode.CLEAN,
                runStep = "Starting",
                runProgress = 0,
                runTotal = 0,
                logs = emptyList(),
                record = null,
                restoreSummary = null,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = CleanFlow(active, backups) { file -> active.install(file) }
                val record = flow.run(
                    analysis = analysis,
                    host = lastHost,
                    port = lastPort,
                    launcherApk = launcherFile,
                    launcherPackage = current.launcherPackage,
                    applyDns = current.applyDns,
                    reporter = reporter(),
                )
                _state.update {
                    it.copy(
                        screen = Screen.DONE,
                        record = record,
                        backups = backups.list(),
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val latest = backups.list().firstOrNull { it.host == lastHost }
                _state.update {
                    it.copy(
                        screen = Screen.DONE,
                        record = latest,
                        backups = backups.list(),
                        error = humanError(error),
                    )
                }
            }
        }
    }

    fun restore(recordId: String) {
        val record = _state.value.backups.firstOrNull { it.id == recordId } ?: backups.load(recordId)
        val active = session
        if (record == null) {
            toast(string(R.string.toast_backup_unreadable))
            return
        }
        if (active == null) {
            toast(string(R.string.toast_connect_same_tv))
            return
        }
        _state.update {
            it.copy(
                screen = Screen.RUN,
                runMode = RunMode.RESTORE,
                runStep = "Restoring",
                runProgress = 0,
                runTotal = 0,
                logs = emptyList(),
                record = record,
                restoreSummary = null,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = CleanFlow(active, backups) { file -> active.install(file) }
                val summary = flow.restore(record, reporter())
                _state.update {
                    it.copy(screen = Screen.DONE, restoreSummary = summary, record = record)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(screen = Screen.DONE, error = humanError(error))
                }
            }
        }
    }

    fun exportBackup(uri: Uri) {
        val record = _state.value.record
        if (record == null) {
            toast(string(R.string.toast_no_run_export))
            return
        }
        _state.update { it.copy(busy = true, busyText = string(R.string.busy_writing_backup), error = null) }
        launchIo {
            val listText = runCatching { catalog.listText(record.listId) }.getOrDefault("")
            backups.export(uri, record, listText, record.installed)
            _state.update { it.copy(busy = false, busyText = "", toast = string(R.string.toast_backup_exported)) }
        }
    }

    fun rebootTv() {
        val active = session ?: run {
            toast(string(R.string.toast_connect_first))
            return
        }
        _state.update { it.copy(busy = true, busyText = string(R.string.busy_rebooting), error = null) }
        launchIo {
            runCatching { active.exec("reboot") }
            active.close()
            session = null
            _state.update {
                it.copy(
                    busy = false,
                    busyText = "",
                    screen = Screen.CONNECT,
                    connected = false,
                    device = null,
                    analysis = null,
                    selectedList = null,
                    toast = string(R.string.toast_reboot_sent),
                )
            }
        }
    }

    fun backToConnect() {
        session?.close()
        session = null
        _state.update {
            it.copy(
                screen = Screen.CONNECT,
                connected = false,
                device = null,
                analysis = null,
                selectedList = null,
                suggestions = emptyList(),
                search = "",
                launcherPath = null,
                launcherName = null,
                launcherPackage = null,
                applyDns = false,
                record = null,
                restoreSummary = null,
                backups = backups.list(),
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    override fun onCleared() {
        mdnsJob?.cancel()
        runCatching { mdns?.close() }
        session?.close()
        super.onCleared()
    }

    private fun reporter(): FlowReporter = object : FlowReporter {
        override suspend fun step(key: String, text: String) {
            _state.update { it.copy(runStep = text) }
            log(text)
        }

        override suspend fun log(text: String) {
            _state.update { it.copy(logs = (it.logs + text).takeLast(300)) }
        }

        override suspend fun progress(done: Int, total: Int) {
            _state.update { it.copy(runProgress = done, runTotal = total) }
        }
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { it.copy(busy = false, busyText = "", error = humanError(error)) }
            }
        }
    }

    private fun string(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun toast(message: String) {
        _state.update { it.copy(toast = message) }
    }

    private fun humanError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("timed out", true) || message.contains("timeout", true) ->
                string(R.string.error_timeout)
            message.contains("refused", true) ->
                string(R.string.error_refused)
            message.contains("auth", true) || message.contains("unauthorized", true) ->
                string(R.string.error_auth)
            message.contains("pair", true) ->
                string(R.string.error_pair)
            message.contains("no packages", true) -> message
            message.isBlank() -> error.javaClass.simpleName
            else -> message
        }
    }

    private fun displayName(uri: Uri): String? {
        val app = getApplication<Application>()
        return app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun apkPackage(file: File): String? {
        val manager = getApplication<Application>().packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            manager.getPackageArchiveInfo(file.absolutePath, 0)
        }
        return info?.packageName
    }
}
