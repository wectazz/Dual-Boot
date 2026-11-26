package com.dualboot.dsu

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dualboot.dsu.service.PrivilegedProvider
import com.dualboot.dsu.service.PrivilegedRootService
import com.dualboot.dsu.ui.ActiveModal
import com.dualboot.dsu.ui.DualBootScreen
import com.dualboot.dsu.ui.DualBootViewModel
import com.dualboot.dsu.ui.ThemeMode
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {

    private val viewModel: DualBootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }

        if (Shell.getShell().isRoot) {
            val intent = Intent(this, PrivilegedRootService::class.java)
            RootService.bind(intent, PrivilegedProvider.connection)
        }
        viewModel.refreshStatus()

        setContent {
            val systemDark = isSystemInDarkTheme()
            val state by viewModel.uiState.collectAsState()
            val useDark = when (state.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            val filePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    viewModel.onFilePicked(uri)
                }
            }

            MiuixTheme(
                colors = if (useDark) darkColorScheme() else lightColorScheme(),
            ) {
                DualBootScreen(
                    state = state,
                    onSelectSlot = viewModel::selectSlot,
                    onRebootClick = { viewModel.openModal(ActiveModal.REBOOT_CONFIRM) },
                    onPickFileClick = { filePicker.launch(arrayOf("*/*")) },
                    onOpenModal = viewModel::openModal,
                    onDismissModal = viewModel::dismissModal,
                    onConfirmInstall = viewModel::startInstallation,
                    onCancelInstall = viewModel::cancelInstallation,
                    onDismissStatusCard = viewModel::dismissStatusCard,
                    onDiscardDsu = viewModel::discardDsu,
                    onSetUserdataGb = viewModel::setUserdataGb,
                    onConfirmReboot = viewModel::rebootIntoSelected,
                    onRefreshStatus = viewModel::refreshStatus,
                    onCycleTheme = viewModel::cycleThemeMode,
                )
            }
        }
    }
}
