package com.cronoszip.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cronoszip.app.data.ArchiveConfig
import com.cronoszip.app.data.ArchiveRepository
import com.cronoszip.app.data.BrowserMode
import com.cronoszip.app.data.BrowserState
import com.cronoszip.app.data.CheckConfig
import com.cronoszip.app.data.CheckData
import com.cronoszip.app.data.CronosSettings
import com.cronoszip.app.data.DEFAULT_KEEP_FREE
import com.cronoszip.app.data.DetailsState
import com.cronoszip.app.data.EngineException
import com.cronoszip.app.data.ExtractConfig
import com.cronoszip.app.data.ExtractSummary
import com.cronoszip.app.data.ExtractionState
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.FileListing
import com.cronoszip.app.data.HistoryEntry
import com.cronoszip.app.data.HistoryStore
import com.cronoszip.app.data.NativeEvent
import com.cronoszip.app.data.PasswordRequest
import com.cronoszip.app.data.PinnedLocation
import com.cronoszip.app.data.PinnedStorage
import com.cronoszip.app.data.RestoreConfig
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.data.SettingsStore
import com.cronoszip.app.data.SetupOptions
import com.cronoszip.app.data.StorageRoot
import com.cronoszip.app.data.TestState
import com.cronoszip.app.data.UiEffect
import com.cronoszip.app.data.UpdateInfo
import com.cronoszip.app.data.UpdateRepository
import com.cronoszip.app.data.UpdateState
import com.cronoszip.app.util.hasStorageAccess
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CronosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ArchiveRepository()
    private val settingsStore = SettingsStore(application)
    private val historyStore = HistoryStore(application)
    private val updateRepository = UpdateRepository(application)

    var settings by mutableStateOf(settingsStore.load())
        private set

    var history by mutableStateOf(historyStore.load())
        private set

    var pinned by mutableStateOf(PinnedStorage.list(application))
        private set

    var isChecking by mutableStateOf(false)
        private set

    var isRefreshingPlan by mutableStateOf(false)
        private set

    var currentCheck by mutableStateOf<CheckData?>(null)
        private set

    var options by mutableStateOf(SetupOptions())
        private set

    val engineAvailable: Boolean
        get() = repository.isEngineAvailable()

    private var lastPassword: String? = null
    private var pendingCheck: CheckConfig? = null
    private var activeExtractConfig: ExtractConfig? = null
    private var lastSampleDone = 0L
    private var lastSampleAt = 0L

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val _browser = MutableStateFlow(BrowserState())
    val browser = _browser.asStateFlow()

    private val _extraction = MutableStateFlow(ExtractionState())
    val extraction = _extraction.asStateFlow()

    private val _test = MutableStateFlow(TestState())
    val test = _test.asStateFlow()

    private val _details = MutableStateFlow(DetailsState())
    val details = _details.asStateFlow()

    private val _update = MutableStateFlow(UpdateState())
    val update = _update.asStateFlow()

    val appVersion: String
        get() = updateRepository.currentVersion().first

    val appVersionCode: Long
        get() = updateRepository.currentVersion().second

    init {
        // Check for a newer release every time the app process opens.
        checkForUpdates()
    }

    fun addPinnedTree(label: String, treeUri: Uri, packageName: String?): String? {
        val items = SafStorage.list(getApplication(), treeUri)
            ?: return "CronosZip could not open that folder. Try another storage app."
        val name = label.trim().ifBlank {
            SafStorage.displayName(getApplication(), treeUri)?.takeIf { it.isNotBlank() }
                ?: items.firstOrNull()?.name
                ?: "Storage"
        }
        PinnedStorage.add(getApplication(), name, treeUri, packageName)
        refreshPinned()
        return null
    }

    fun addPinnedPath(label: String, path: String, packageName: String? = null) {
        val clean = path.trim().trimEnd('/')
        if (clean.isBlank()) return
        val name = label.trim().ifBlank { File(clean).name.ifBlank { clean } }
        PinnedStorage.addRoot(getApplication(), name, clean, packageName)
        refreshPinned()
    }

    fun removePinned(item: PinnedLocation) {
        PinnedStorage.remove(getApplication(), item)
        refreshPinned()
    }

    private fun refreshPinned() {
        pinned = PinnedStorage.list(getApplication())
    }

    fun checkArchive(path: String, password: String? = null) {
        lastPassword = password
        pendingCheck = null
        val config = CheckConfig(
            archive = path,
            output = null,
            keepArchive = settings.keepArchiveByDefault,
            keepFree = settings.keepFreeBytes,
            password = password,
        )
        performCheck(config, navigate = true, full = true, remember = true)
    }

    fun retryPassword(password: String) {
        val config = pendingCheck ?: return
        pendingCheck = null
        performCheck(config.copy(password = password), navigate = true, full = true, remember = true)
    }

    fun cancelPassword() {
        pendingCheck = null
    }

    fun recheckCurrent() {
        val check = currentCheck ?: return
        val config = CheckConfig(
            archive = check.archive,
            output = check.output,
            keepArchive = options.keepArchive,
            keepFree = options.keepFree,
            password = lastPassword,
        )
        performCheck(config, navigate = true, full = true)
    }

    private fun performCheck(
        config: CheckConfig,
        navigate: Boolean,
        full: Boolean,
        remember: Boolean = false,
    ) {
        viewModelScope.launch {
            if (full) isChecking = true else isRefreshingPlan = true
            try {
                val data = withContext(Dispatchers.IO) { repository.check(config) }
                currentCheck = data
                lastPassword = config.password
                pendingCheck = null
                options = SetupOptions(
                    keepArchive = if (data.resuming) data.resumeKeepsArchive else config.keepArchive,
                    replaceMode = if (remember) "none" else options.replaceMode,
                    verifyFirst = if (remember) settings.verifyFirstByDefault else options.verifyFirst,
                    keepFree = config.keepFree,
                )
                if (remember) recordHistory(data, "Checked")
                if (navigate) {
                    if (!data.plan.fits && !data.orphan) {
                        _effects.tryEmit(UiEffect.NoSpace(data))
                    } else {
                        _effects.tryEmit(UiEffect.OpenSetup(data))
                    }
                }
            } catch (error: EngineException) {
                when (error.code) {
                    "PASSWORD_NEEDED" -> {
                        pendingCheck = config.copy(password = null)
                        _effects.tryEmit(UiEffect.PasswordNeeded(wrong = false))
                    }
                    "WRONG_PASSWORD" -> {
                        pendingCheck = config
                        _effects.tryEmit(UiEffect.PasswordNeeded(wrong = true))
                    }
                    else -> {
                        if (navigate) {
                            _effects.tryEmit(UiEffect.Message(error.message))
                        } else {
                            _effects.tryEmit(UiEffect.Message(error.message))
                        }
                    }
                }
            } catch (error: Exception) {
                _effects.tryEmit(
                    UiEffect.Message(error.message ?: "The archive could not be opened."),
                )
            } finally {
                if (full) isChecking = false else isRefreshingPlan = false
            }
        }
    }

    fun updateOptions(newOptions: SetupOptions) {
        options = newOptions
        val check = currentCheck ?: return
        if (check.orphan) return
        val config = CheckConfig(
            archive = check.archive,
            output = check.output,
            keepArchive = newOptions.keepArchive,
            keepFree = newOptions.keepFree,
            password = lastPassword,
        )
        performCheck(config, navigate = false, full = false)
    }

    fun applyOutputFolder(path: String) {
        val check = currentCheck ?: return
        val config = CheckConfig(
            archive = check.archive,
            output = path,
            keepArchive = options.keepArchive,
            keepFree = options.keepFree,
            password = lastPassword,
        )
        performCheck(config, navigate = false, full = false)
    }

    fun startExtraction() {
        val check = currentCheck ?: return
        if (_extraction.value.running) return
        val config = ExtractConfig(
            archive = check.archive,
            output = check.output,
            keepArchive = options.keepArchive,
            replace = options.replaceMode,
            verifyFirst = options.verifyFirst,
            keepFree = options.keepFree,
            password = lastPassword,
        )
        activeExtractConfig = config
        beginExtraction(config)
        _effects.tryEmit(UiEffect.ExtractionStarted)
    }

    fun resumeExtraction() {
        val config = activeExtractConfig ?: return
        if (_extraction.value.running) return
        beginExtraction(config)
        _effects.tryEmit(UiEffect.ExtractionStarted)
    }

    private fun beginExtraction(config: ExtractConfig) {
        val check = currentCheck
        lastSampleDone = 0L
        lastSampleAt = SystemClock.elapsedRealtime()
        _extraction.value = ExtractionState(
            running = true,
            phase = "preparing",
            message = "Opening the archive and checking the space plan",
            total = check?.totalSize ?: 0L,
            committed = check?.committedLen ?: 0L,
            archiveSize = check?.archiveSize ?: 0L,
            startedAt = SystemClock.elapsedRealtime(),
            output = config.output ?: check?.output ?: "",
            keepArchive = config.keepArchive,
            logs = listOf("Preparing extraction. Nothing has been changed yet."),
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = repository.extract(config) { event ->
                    handleExtractionEvent(event)
                }
                withContext(Dispatchers.Main) {
                    if (summary.paused) {
                        _extraction.update {
                            it.copy(
                                running = false,
                                paused = true,
                                cancelling = false,
                                phase = "paused",
                                message = "Paused. Progress is saved.",
                            )
                        }
                    } else {
                        _extraction.update {
                            it.copy(
                                running = false,
                                success = true,
                                paused = false,
                                cancelling = false,
                                phase = "complete",
                                message = if (summary.kept) {
                                    "Extraction complete. The archive was kept."
                                } else {
                                    "Extraction complete. The archive was removed."
                                },
                                done = it.total,
                                summary = summary,
                                output = summary.output,
                            )
                        }
                        recordExtraction(summary, config)
                    }
                }
            } catch (error: EngineException) {
                withContext(Dispatchers.Main) {
                    _extraction.update {
                        it.copy(
                            running = false,
                            cancelling = false,
                            phase = if (error.code == "NO_SPACE") "space" else "error",
                            errorCode = error.code,
                            errorMessage = error.message,
                        )
                    }
                    appendLog(error.message)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    _extraction.update {
                        it.copy(
                            running = false,
                            cancelling = false,
                            phase = "error",
                            errorCode = "FAILED",
                            errorMessage = error.message ?: "The extraction failed.",
                        )
                    }
                }
            }
        }
    }

    private fun handleExtractionEvent(event: NativeEvent) {
        when (event.type) {
            "phase" -> {
                _extraction.update {
                    it.copy(
                        phase = event.phase ?: it.phase,
                        message = event.message ?: it.message,
                    )
                }
                event.message?.let { message ->
                    if (event.phase != "extracting") appendLog(message)
                }
            }
            "log" -> event.message?.let { appendLog(it) }
            "file" -> event.current?.let { file ->
                _extraction.update { it.copy(currentFile = file) }
            }
            "commit" -> {
                _extraction.update {
                    it.copy(
                        committed = event.committed ?: it.committed,
                        archiveSize = event.archiveSize ?: it.archiveSize,
                    )
                }
            }
            "progress" -> {
                val now = SystemClock.elapsedRealtime()
                _extraction.update { state ->
                    val done = event.done ?: state.done
                    val total = event.total ?: state.total
                    var speed = state.speed
                    val delta = done - lastSampleDone
                    val elapsed = now - lastSampleAt
                    if (delta >= 0 && elapsed >= 250) {
                        val instant = delta * 1000.0 / elapsed.toDouble()
                        speed = if (state.speed <= 0.0) instant else state.speed * 0.72 + instant * 0.28
                        lastSampleDone = done
                        lastSampleAt = now
                    }
                    state.copy(
                        done = done,
                        total = total,
                        speed = speed,
                        phase = event.phase ?: state.phase,
                    )
                }
            }
        }
    }

    private fun appendLog(message: String) {
        _extraction.update { state ->
            state.copy(logs = (state.logs + message).takeLast(140))
        }
    }

    fun cancelExtraction() {
        val state = _extraction.value
        if (!state.running || state.cancelling) return
        _extraction.update {
            it.copy(
                cancelling = true,
                phase = "pausing",
                message = "Finishing the current batch before pausing",
            )
        }
        appendLog("Pause requested. The current batch is finished safely, then progress is saved.")
        repository.cancel()
    }

    fun restoreArchive() {
        val check = currentCheck ?: return
        val config = RestoreConfig(
            archive = check.archive,
            output = check.output.takeIf { value -> value.isNotBlank() },
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.restore(config) }
                currentCheck = null
                _effects.tryEmit(
                    UiEffect.RestoreCompleted(
                        "Archive restored. ${result.entries} entries remain in a standard archive.",
                    ),
                )
            } catch (error: EngineException) {
                _effects.tryEmit(UiEffect.Message(error.message))
            }
        }
    }

    fun openDetails() {
        val check = currentCheck ?: return
        _details.value = DetailsState(loading = true)
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    repository.list(ArchiveConfig(check.archive, lastPassword))
                }
                _details.value = DetailsState(list = list)
            } catch (error: EngineException) {
                _details.value = DetailsState(error = error.message)
            }
        }
    }

    fun refreshDetails() {
        openDetails()
    }

    fun testArchive() {
        val check = currentCheck ?: return
        if (_test.value.running) return
        _test.value = TestState(running = true, total = check.archiveSize)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = repository.test(
                    ArchiveConfig(check.archive, lastPassword),
                ) { event ->
                    when (event.type) {
                        "progress" -> _test.update {
                            it.copy(
                                done = event.done ?: it.done,
                                total = event.total ?: it.total,
                            )
                        }
                        "phase" -> event.message?.let { message ->
                            _test.update { it.copy(message = message) }
                        }
                        "log" -> event.message?.let { message ->
                            _test.update { it.copy(message = message) }
                        }
                    }
                }
                _test.value = TestState(
                    running = false,
                    done = summary.bytes,
                    total = summary.bytes,
                    passed = true,
                    message = "Every entry passed its checksum.",
                )
            } catch (error: EngineException) {
                _test.value = TestState(
                    running = false,
                    passed = false,
                    error = error.message,
                )
            } catch (error: Exception) {
                _test.value = TestState(
                    running = false,
                    passed = false,
                    error = error.message ?: "The test failed.",
                )
            }
        }
    }

    fun clearTest() {
        _test.value = TestState()
    }

    fun enterBrowser(mode: BrowserMode, start: String? = null) {
        val roots = storageRoots()
        val initial = start
            ?: when (mode) {
                BrowserMode.Archive -> currentCheck?.archive
                    ?.let { File(it).parent }
                    ?: roots.firstOrNull()?.path
                BrowserMode.Folder -> currentCheck?.output
                    ?: roots.firstOrNull()?.path
            }
            ?: ""
        _browser.value = _browser.value.copy(mode = mode, roots = roots)
        val directory = if (File(initial).isDirectory) initial else roots.firstOrNull()?.path ?: ""
        loadDirectory(directory)
    }

    fun navigateBrowser(path: String) {
        loadDirectory(path)
    }

    fun browserUp() {
        _browser.value.parent?.let { loadDirectory(it) }
    }

    private fun loadDirectory(path: String) {
        if (path.isBlank()) return
        val mode = _browser.value.mode
        viewModelScope.launch {
            _browser.update { it.copy(current = path, loading = true, error = null) }
            val items = withContext(Dispatchers.IO) { listFiles(path, mode) }
            _browser.update {
                it.copy(
                    loading = false,
                    items = items ?: emptyList(),
                    error = if (items == null) {
                        "This folder cannot be read. Grant storage access or choose another folder."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * The browser shows folders only in Folder mode, and folders plus archives otherwise.
     * Listing itself is shared with the Files screen through [FileListing].
     */
    private fun listFiles(path: String, mode: BrowserMode): List<FileItem>? {
        return FileListing.listLocal(path)?.filter { child ->
            if (mode == BrowserMode.Folder) {
                child.directory
            } else {
                child.directory || child.archive
            }
        }
    }

    private fun storageRoots(): List<StorageRoot> {
        val context = getApplication<Application>()
        val roots = mutableListOf<StorageRoot>()
        val external = Environment.getExternalStorageDirectory()
        external?.let { roots.add(StorageRoot("Internal", it.absolutePath)) }
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        if (downloads != null && downloads.absolutePath != external?.absolutePath) {
            roots.add(StorageRoot("Downloads", downloads.absolutePath))
        }
        context.getExternalFilesDir(null)?.let {
            roots.add(StorageRoot("App", it.absolutePath))
        }
        return roots.distinctBy { it.path }
    }

    fun refreshBrowserAfterPermission() {
        if (!hasStorageAccess(getApplication())) return
        val state = _browser.value
        if (state.items.isEmpty() || state.error != null) {
            val path = state.current.ifBlank {
                state.roots.firstOrNull()?.path ?: ""
            }
            if (path.isNotBlank()) loadDirectory(path)
        }
    }

    fun checkForUpdates() {
        if (_update.value.checking || _update.value.downloading) return
        _update.value = _update.value.copy(checking = true, error = null, message = "Checking latest release...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = updateRepository.fetchLatest()
                withContext(Dispatchers.Main) {
                    if (info == null) {
                        _update.value = _update.value.copy(checking = false, error = "Could not check for updates.")
                    } else {
                        val currentCode = updateRepository.currentVersion().second
                        val hasUpdate = info.tagName.isNotBlank() && info.apkUrl.isNotBlank() &&
                            (if (info.versionCode > 0) {
                                info.versionCode > currentCode
                            } else {
                                isNewerVersion(info.versionName, appVersion)
                            })
                        _update.value = _update.value.copy(
                            checking = false,
                            update = info,
                            installable = false,
                            apkPath = null,
                            message = if (hasUpdate) {
                                "Latest: ${info.tagName}. Download to verify and install."
                            } else {
                                "You are on the latest version (${appVersion})."
                            },
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _update.value = _update.value.copy(checking = false, error = e.message ?: "Update check failed.")
                }
            }
        }
    }

    fun downloadUpdate() {
        val info = _update.value.update ?: return
        if (_update.value.downloading) return
        if (info.apkUrl.isBlank()) {
            _update.value = _update.value.copy(error = "The latest release has no APK asset.")
            return
        }
        if (info.sha256.isBlank()) {
            _update.value = _update.value.copy(error = "The latest release has no .sha256 asset. Refusing an unverified install.")
            return
        }
        _update.value = _update.value.copy(downloading = true, progress = 0, total = 0, error = null, message = "Downloading APK and verifying SHA-256...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = updateRepository.downloadApk(info) { done, total ->
                    _update.value = _update.value.copy(progress = done, total = total)
                }
                withContext(Dispatchers.Main) {
                    if (file == null) {
                        _update.value = _update.value.copy(downloading = false, error = "Download or SHA-256 verification failed.")
                    } else {
                        _update.value = _update.value.copy(
                            downloading = false,
                            installable = true,
                            apkPath = file.absolutePath,
                            message = "Verified. Ready to install.",
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _update.value = _update.value.copy(downloading = false, error = e.message ?: "Download failed.")
                }
            }
        }
    }

    fun installUpdate() {
        val path = _update.value.apkPath ?: return
        val context = getApplication<Application>()
        val file = File(path)
        if (!file.exists()) {
            _update.value = _update.value.copy(error = "APK missing. Download again.")
            return
        }
        val installedFingerprint = signingFingerprint(packageInfo(null))
        val candidateFingerprint = signingFingerprint(packageInfo(file.absolutePath))
        if (installedFingerprint.isNotEmpty() && candidateFingerprint.isNotEmpty() &&
            installedFingerprint != candidateFingerprint
        ) {
            _update.value = _update.value.copy(
                error = "This APK is signed with a different key than the installed app, so Android cannot update it in place. " +
                    "Uninstall CronosZip once and install this APK; all later updates will install in place.",
            )
            return
        }
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Install CronosZip update").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun packageInfo(path: String?): PackageInfo? {
        val context = getApplication<Application>()
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                )
                if (path == null) pm.getPackageInfo(context.packageName, flags)
                else pm.getPackageArchiveInfo(path, flags)
            } else {
                @Suppress("DEPRECATION")
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
                if (path == null) pm.getPackageInfo(context.packageName, flag)
                else pm.getPackageArchiveInfo(path, flag)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun signingFingerprint(info: PackageInfo?): String {
        if (info == null) return ""
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return ""
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.joinToString("|") { signature ->
            digest.digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        value.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun clearUpdate() {
        _update.value = UpdateState()
    }

    fun updateSettings(newSettings: CronosSettings) {
        settings = newSettings
        settingsStore.save(newSettings)
    }

    fun clearHistory() {
        historyStore.clear()
        history = emptyList()
    }

    private fun recordHistory(data: CheckData, status: String) {
        val entry = HistoryEntry(
            path = data.archive,
            name = data.displayName,
            format = data.format,
            timestamp = System.currentTimeMillis(),
            status = status,
        )
        history = historyStore.add(entry)
    }

    private fun recordExtraction(summary: ExtractSummary, config: ExtractConfig) {
        val check = currentCheck
        val entry = HistoryEntry(
            path = summary.archive.ifBlank { check?.archive ?: config.archive },
            name = check?.displayName ?: File(summary.archive.ifBlank { config.archive }).name,
            format = check?.format ?: "",
            timestamp = System.currentTimeMillis(),
            status = if (summary.kept) "Extracted (kept)" else "Extracted",
        )
        history = historyStore.add(entry)
    }

    fun resetExtraction() {
        activeExtractConfig = null
        _extraction.value = ExtractionState()
    }

    fun dismissNoSpace() {
        // The dialog is owned by the UI; this exists so state changes have one place.
    }

    fun defaultKeepFree(): Long = settings.keepFreeBytes.takeIf { it > 0 } ?: DEFAULT_KEEP_FREE

    companion object {
        fun passwordRequest(wrong: Boolean) = PasswordRequest(wrong)
    }
}
