package com.dualboot.dsu.ui

import android.app.Application
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
)

class DualBootViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DualBootUiState())
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

        val job = Job()
        installJob = job
        val userdataBytes = state.userdataGb.toLong() * 1024L * 1024L * 1024L

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

    fun cancelInstallation() {
        installJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                PrivilegedProvider.getService().abort()
                PrivilegedProvider.getService().remove()
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
