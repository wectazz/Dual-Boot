package com.dualboot.dsu.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualboot.dsu.installer.root.DSUInstaller
import com.dualboot.dsu.model.InstallationStep
import com.dualboot.dsu.service.PrivilegedProvider
import com.dualboot.dsu.util.DevicePropUtils
import com.dualboot.dsu.util.FilenameUtils
import com.dualboot.dsu.util.OsEntryInfo
import com.dualboot.dsu.util.StorageUtils
import com.dualboot.dsu.util.SystemInfoUtils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OsSlot {
    PRIMARY,
    SECONDARY,
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light"),
}

enum class ActiveModal {
    NONE,
    INSTALL_CONFIRM,
    INSTALLED_SYSTEMS,
    MANAGE_STORAGE,
    DEVICE_STATUS,
    REBOOT_CONFIRM,
}

data class DualBootUiState(
    val isRootGranted: Boolean = false,
    val isPrivilegedConnected: Boolean = false,
    val hasDynamicPartitions: Boolean = true,
    val primaryOs: OsEntryInfo = SystemInfoUtils.getPrimaryOsInfo(),
    val secondaryOs: OsEntryInfo = SystemInfoUtils.getSecondaryOsLabel(),
    val isDsuInstalled: Boolean = false,
    val isDsuInUse: Boolean = false,
    val isDsuEnabled: Boolean = false,
    val selectedSlot: OsSlot = OsSlot.PRIMARY,
    val installationStep: InstallationStep = InstallationStep.IDLE,
    val installProgress: Float = 0f,
    val installDetail: String = "",
    val errorMessage: String? = null,
    val userdataGb: Int = 8,
    val dsuUsedBytes: Long = 0L,
    val storageInfo: StorageUtils.StorageInfo = StorageUtils.getStorageInfo(
        DevicePropUtils.getGsidBinaryAllowedPerc()
    ),
    val activeModal: ActiveModal = ActiveModal.NONE,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "",
    val selectedFileSize: Long = -1L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useBuiltinInstaller: Boolean = true,
)

class DualBootViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("dualboot_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        DualBootUiState(
            useBuiltinInstaller = prefs.getBoolean("use_builtin_installer", true),
        )
    )
    val uiState: StateFlow<DualBootUiState> = _uiState.asStateFlow()

    private var installJob: Job? = null

    fun refreshStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = Shell.getShell().isRoot
            val hasDyn = DevicePropUtils.hasDynamicPartitions()
            val storage = StorageUtils.getStorageInfo(DevicePropUtils.getGsidBinaryAllowedPerc())
            val secondary = SystemInfoUtils.getSecondaryOsLabel()
            val dsuBytes = if (isRoot) SystemInfoUtils.getDsuUsedBytes() else 0L

            _uiState.update {
                it.copy(
                    isRootGranted = isRoot,
                    hasDynamicPartitions = hasDyn,
                    storageInfo = storage,
                    secondaryOs = secondary,
                    dsuUsedBytes = dsuBytes,
                )
            }

            if (isRoot) {
                PrivilegedProvider.run(
                    onFail = {
                        _uiState.update { it.copy(isPrivilegedConnected = false) }
                    },
                    onConnected = {
                        val inUse = runCatching { isInUse }.getOrDefault(false)
                        val installed = runCatching { isInstalled }.getOrDefault(false)
                        val enabled = runCatching { isEnabled }.getOrDefault(false)
                        _uiState.update {
                            it.copy(
                                isPrivilegedConnected = true,
                                isDsuInUse = inUse,
                                isDsuInstalled = installed,
                                isDsuEnabled = enabled,
                                selectedSlot = if (inUse || (installed && enabled)) OsSlot.SECONDARY else OsSlot.PRIMARY,
                            )
                        }
                    }
                )
            }
        }
    }

    fun selectSlot(slot: OsSlot) {
        _uiState.update { it.copy(selectedSlot = slot) }
    }

    fun openModal(modal: ActiveModal) {
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun dismissModal() {
        _uiState.update { it.copy(activeModal = ActiveModal.NONE) }
    }

    fun setUserdataGb(gb: Int) {
        _uiState.update { state ->
            val maxGb = state.storageInfo.maxAllocationGb.coerceAtLeast(2)
            state.copy(userdataGb = gb.coerceIn(2, maxGb))
        }
    }

    fun setUseBuiltinInstaller(enabled: Boolean) {
        prefs.edit().putBoolean("use_builtin_installer", enabled).apply()
        _uiState.update { it.copy(useBuiltinInstaller = enabled) }
    }

    fun cycleThemeMode() {
        _uiState.update { state ->
            val next = when (state.themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.SYSTEM
            }
            state.copy(themeMode = next)
        }
    }

    fun onFilePicked(uri: Uri) {
        val ctx = getApplication<Application>()
        val name = FilenameUtils.queryName(ctx.contentResolver, uri)
        val size = FilenameUtils.querySize(ctx, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("img", "xz", "gz", "gzip", "zip")) {
            _uiState.update {
                it.copy(
                    installationStep = InstallationStep.ERROR,
                    errorMessage = "Unsupported file format: .$ext. Use .img, .xz, .gz, or .zip.",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = name,
                selectedFileSize = size,
                activeModal = ActiveModal.INSTALL_CONFIRM,
                errorMessage = null,
            )
        }
    }

    fun startInstallation() {
        val state = _uiState.value
        val uri = state.selectedFileUri ?: return
        dismissModal()

        val userdataBytes = state.userdataGb.toLong() * 1024L * 1024L * 1024L
        if (!state.useBuiltinInstaller) {
            startSystemDsuInstaller(uri, state.selectedFileSize, userdataBytes)
            return
        }

        val job = Job()
        installJob = job

        _uiState.update {
            it.copy(
                installationStep = InstallationStep.DECOMPRESSING,
                installProgress = 0f,
                installDetail = state.selectedFileName,
                errorMessage = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO + job) {
            DSUInstaller(
                application = getApplication(),
                userdataSize = userdataBytes,
                sourceUri = uri,
                fileName = state.selectedFileName,
                fileSize = state.selectedFileSize,
                installationJob = job,
                onError = { msg ->
                    _uiState.update {
                        it.copy(
                            installationStep = InstallationStep.ERROR,
                            errorMessage = msg,
                        )
                    }
                    refreshStatus()
                },
                onProgressUpdate = { progress, partition ->
                    _uiState.update {
                        it.copy(
                            installProgress = progress,
                            installDetail = partition,
                        )
                    }
                },
                onStepUpdate = { step, detail ->
                    _uiState.update {
                        it.copy(
                            installationStep = step,
                            installDetail = detail,
                        )
                    }
                },
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            installationStep = InstallationStep.SUCCESS,
                            installProgress = 1f,
                            isDsuInstalled = true,
                            selectedSlot = OsSlot.SECONDARY,
                        )
                    }
                    refreshStatus()
                },
            ).execute()
        }
    }

    private fun startSystemDsuInstaller(uri: Uri, fileSize: Long, userdataBytes: Long) {
        val job = Job()
        installJob = job
        viewModelScope.launch(Dispatchers.IO + job) {
            runCatching {
                val ctx = getApplication<Application>()
                ctx.grantUriPermission(
                    "com.android.dynsystem",
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                val service = PrivilegedProvider.getService()
                val wasInstalled = runCatching { service.isInstalled }.getOrDefault(false)
                service.setDynProp()
                val intent = Intent("android.os.image.action.START_INSTALL").apply {
                    setClassName("com.android.dynsystem", "com.android.dynsystem.VerificationActivity")
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("KEY_USERDATA_SIZE", userdataBytes)
                    if (fileSize > 0L) {
                        putExtra("KEY_SYSTEM_SIZE", fileSize)
                    }
                    putExtra("KEY_ENABLE_WHEN_COMPLETED", false)
                }
                service.startActivity(intent)

                _uiState.update {
                    it.copy(
                        installationStep = InstallationStep.INSTALLING,
                        installProgress = 0f,
                        installDetail = "system DSU",
                        errorMessage = null,
                    )
                }

                var sawWorking = false
                var idleTicks = 0
                var postWorkIdleTicks = 0
                while (isActive) {
                    delay(400)
                    val installed = runCatching { service.isInstalled }.getOrDefault(false)
                    if (installed && (!wasInstalled || sawWorking)) {
                        _uiState.update {
                            it.copy(
                                installationStep = InstallationStep.SUCCESS,
                                installProgress = 1f,
                                isDsuInstalled = true,
                                selectedSlot = OsSlot.SECONDARY,
                            )
                        }
                        refreshStatus()
                        break
                    }

                    val notif = readSystemDsuNotificationProgress()
                    if (notif != null) {
                        sawWorking = true
                        idleTicks = 0
                        postWorkIdleTicks = 0
                        _uiState.update {
                            it.copy(
                                installationStep = InstallationStep.INSTALLING,
                                installProgress = notif.fraction,
                                installDetail = notif.detail,
                            )
                        }
                        continue
                    }

                    val prog = runCatching { service.installationProgress }.getOrNull()
                    val status = prog?.status ?: 0

                    when {
                        status == 1 -> {
                            sawWorking = true
                            idleTicks = 0
                            postWorkIdleTicks = 0
                            val fraction = if (prog != null && prog.total_bytes > 0L) {
                                (prog.bytes_processed.toFloat() / prog.total_bytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val detail = prog?.step?.takeIf { it.isNotBlank() } ?: "system DSU"
                            _uiState.update {
                                it.copy(
                                    installationStep = InstallationStep.INSTALLING,
                                    installProgress = fraction,
                                    installDetail = detail,
                                )
                            }
                        }
                        status == 2 -> {
                            // Partition step completed; wait for next partition or isInstalled == true
                            sawWorking = true
                            idleTicks = 0
                            postWorkIdleTicks = 0
                        }
                        sawWorking && status == 0 -> {
                            val serviceAlive = isSystemDsuServiceAlive()
                            if (serviceAlive) {
                                postWorkIdleTicks = 0
                            } else {
                                postWorkIdleTicks++
                                if (postWorkIdleTicks >= 8) {
                                    _uiState.update {
                                        it.copy(
                                            installationStep = InstallationStep.ERROR,
                                            errorMessage = "System DSU installation cancelled or failed",
                                        )
                                    }
                                    refreshStatus()
                                    break
                                }
                            }
                        }
                        else -> {
                            val serviceAlive = isSystemDsuServiceAlive()
                            if (serviceAlive) {
                                idleTicks = 0
                            } else {
                                idleTicks++
                                if (idleTicks >= 150) {
                                    _uiState.update {
                                        it.copy(
                                            installationStep = InstallationStep.IDLE,
                                            installProgress = 0f,
                                            installDetail = "",
                                        )
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        installationStep = InstallationStep.ERROR,
                        errorMessage = e.message ?: "Failed to launch system DSU installer",
                    )
                }
            }
        }
    }

    private data class NotifProgress(
        val fraction: Float,
        val detail: String,
    )

    private fun isSystemDsuServiceAlive(): Boolean {
        return Shell.cmd("dumpsys activity services com.android.dynsystem")
            .exec().out.any { "DynamicSystemInstallationService" in it }
    }

    private fun readSystemDsuNotificationProgress(): NotifProgress? {
        val res = Shell.cmd("dumpsys notification --noredact").exec()
        if (!res.isSuccess) return null
        var inDsuRecord = false
        var progress: Int? = null
        var progressMax: Int? = null
        var text: String? = null

        for (rawLine in res.out) {
            val line = rawLine.trim()
            if (line.startsWith("NotificationRecord(")) {
                if (inDsuRecord) break
                inDsuRecord = "pkg=com.android.dynsystem" in line
                continue
            }
            if (!inDsuRecord) continue
            if (line.startsWith("android.progress=")) {
                progress = Regex("""(\d+)""").find(line.substringAfter('='))?.value?.toIntOrNull()
            } else if (line.startsWith("android.progressMax=")) {
                progressMax = Regex("""(\d+)""").find(line.substringAfter('='))?.value?.toIntOrNull()
            } else if (line.startsWith("android.text=")) {
                text = line.substringAfter('=')
                    .removePrefix("String (")
                    .removeSuffix(")")
                    .trim()
            }
        }
        val max = progressMax ?: return null
        val cur = progress ?: return null
        if (max <= 0) return null
        val fraction = (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        val detail = text?.takeIf { it.isNotBlank() } ?: "system DSU"
        return NotifProgress(fraction, detail)
    }

    fun cancelInstallation() {
        installJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val service = PrivilegedProvider.getService()
                service.abort()
                service.remove()
                service.forceStopPackage("com.android.dynsystem")
            }
            _uiState.update {
                it.copy(
                    installationStep = InstallationStep.IDLE,
                    installProgress = 0f,
                    installDetail = "",
                )
            }
            refreshStatus()
        }
    }

    fun dismissStatusCard() {
        _uiState.update {
            it.copy(
                installationStep = InstallationStep.IDLE,
                errorMessage = null,
                installProgress = 0f,
            )
        }
    }

    fun discardDsu() {
        dismissModal()
        viewModelScope.launch(Dispatchers.IO) {
            PrivilegedProvider.run {
                runCatching {
                    remove()
                    forceStopPackage("com.android.dynsystem")
                }
                _uiState.update {
                    it.copy(
                        isDsuInstalled = false,
                        isDsuEnabled = false,
                        selectedSlot = OsSlot.PRIMARY,
                        installationStep = InstallationStep.IDLE,
                        installProgress = 0f,
                        installDetail = "",
                    )
                }
                refreshStatus()
            }
        }
    }

    fun rebootIntoSelected() {
        dismissModal()
        val state = _uiState.value
        if (!state.isRootGranted) return
        viewModelScope.launch(Dispatchers.IO) {
            PrivilegedProvider.run {
                when (state.selectedSlot) {
                    OsSlot.SECONDARY -> {
                        if (state.isDsuInstalled) {
                            setEnable(true, true)
                            Shell.cmd("reboot").exec()
                        }
                    }
                    OsSlot.PRIMARY -> {
                        if (state.isDsuInstalled && state.isDsuEnabled) {
                            setEnable(false, false)
                        }
                        Shell.cmd("reboot").exec()
                    }
                }
            }
        }
    }
}
