package com.dualboot.dsu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualboot.dsu.R
import com.dualboot.dsu.model.InstallationStep
import com.dualboot.dsu.util.StorageUtils
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

@Composable
private fun rememberGlassBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun DualBootScreen(
    state: DualBootUiState,
    onSelectSlot: (OsSlot) -> Unit,
    onRebootClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onOpenModal: (ActiveModal) -> Unit,
    onDismissModal: () -> Unit,
    onConfirmInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onDismissStatusCard: () -> Unit,
    onDiscardDsu: () -> Unit,
    onSetUserdataGb: (Int) -> Unit,
    onConfirmReboot: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCycleTheme: () -> Unit,
    onToggleBuiltinInstaller: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val navBackdrop = rememberGlassBackdrop()
    val glassItems = remember {
        listOf(
            GlassNavigationItem(icon = MiuixIcons.Glass.HomeOutline, label = "Dual Boot"),
            GlassNavigationItem(icon = MiuixIcons.Glass.SettingsOutline, label = "Settings"),
        )
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        GlassNavigationBarDefaults.Height + 28.dp

    // Root Scaffold hosts GlassBottomSheet, GlassDialog, and bottomBar
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Box {
                GlassNavigationBar(
                    items = glassItems,
                    selectedIndex = pagerState.targetPage,
                    onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                    backdrop = navBackdrop,
                    selectedColor = MiuixTheme.colorScheme.primary,
                    unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
        },
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (navBackdrop != null) Modifier.layerBackdrop(navBackdrop) else Modifier),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> DualBootHomeTab(
                        state = state,
                        bottomPadding = navBarBottomPadding,
                        onSelectSlot = onSelectSlot,
                        onRebootClick = onRebootClick,
                        onPickFileClick = onPickFileClick,
                        onOpenModal = onOpenModal,
                        onCancelInstall = onCancelInstall,
                        onDismissStatusCard = onDismissStatusCard,
                        onDiscardDsu = onDiscardDsu,
                    )
                    1 -> SettingsTab(
                        state = state,
                        bottomPadding = navBarBottomPadding,
                        onRefreshStatus = onRefreshStatus,
                        onCycleTheme = onCycleTheme,
                        onToggleBuiltinInstaller = onToggleBuiltinInstaller,
                    )
                }
            }
        }

        // HOS 4 GlassBottomSheet & GlassDialog modals
        val accentBlue = MiuixTheme.colorScheme.primary
        val textPrimary = MiuixTheme.colorScheme.onSurface
        val textSecondary = MiuixTheme.colorScheme.onSurfaceVariantSummary

        GlassDialog(
            show = state.activeModal == ActiveModal.INSTALL_CONFIRM,
            title = "Install secondary OS",
            summary = state.selectedFileName,
            backdrop = navBackdrop,
            onDismissRequest = onDismissModal,
        ) {
            Column {
                if (state.selectedFileSize > 0) {
                    Text(
                        text = "Size: ${StorageUtils.formatBytes(state.selectedFileSize)}",
                        fontSize = 13.sp,
                        color = textSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = "Userdata allocation: ${state.userdataGb} GB",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentBlue,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val maxGb = state.storageInfo.maxAllocationGb.coerceAtLeast(4).toFloat()
                Slider(
                    value = state.userdataGb.toFloat(),
                    onValueChange = { onSetUserdataGb(it.roundToInt()) },
                    valueRange = 2f..maxGb,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onDismissModal,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirmInstall,
                        enabled = state.isRootGranted && state.isPrivilegedConnected,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("Install", color = Color.White)
                    }
                }
            }
        }

        GlassDialog(
            show = state.activeModal == ActiveModal.INSTALLED_SYSTEMS,
            title = "Installed systems",
            summary = "Primary & secondary DSU slots",
            backdrop = navBackdrop,
            onDismissRequest = onDismissModal,
        ) {
            Column {
                Text(
                    text = "Primary: ${state.primaryOs.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                )
                Text(
                    text = state.primaryOs.versionLine,
                    fontSize = 13.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Secondary: ${state.secondaryOs.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                )
                Text(
                    text = if (state.isDsuInstalled) {
                        "Installed${if (state.isDsuEnabled) " · Enabled for next boot" else ""}"
                    } else {
                        "Not installed"
                    },
                    fontSize = 13.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isDsuInstalled && !state.isDsuInUse) {
                        Button(
                            onClick = onDiscardDsu,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Discard DSU", color = Color(0xFFFF453A))
                        }
                    }
                    Button(
                        onClick = onDismissModal,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }

        GlassDialog(
            show = state.activeModal == ActiveModal.MANAGE_STORAGE,
            title = "Manage storage",
            summary = "Internal storage & DSU allocation",
            backdrop = navBackdrop,
            onDismissRequest = onDismissModal,
        ) {
            Column {
                Text(
                    text = "Free: ${StorageUtils.formatBytes(state.storageInfo.availableBytes)} / ${StorageUtils.formatBytes(state.storageInfo.totalBytes)}",
                    fontSize = 14.sp,
                    color = textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DSU used: ${StorageUtils.formatBytes(state.dsuUsedBytes)}",
                    fontSize = 13.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Default userdata size: ${state.userdataGb} GB",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentBlue,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val maxGb = state.storageInfo.maxAllocationGb.coerceAtLeast(4).toFloat()
                Slider(
                    value = state.userdataGb.toFloat(),
                    onValueChange = { onSetUserdataGb(it.roundToInt()) },
                    valueRange = 2f..maxGb,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismissModal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("Done", color = Color.White)
                }
            }
        }

        GlassDialog(
            show = state.activeModal == ActiveModal.DEVICE_STATUS,
            title = "Device & root status",
            summary = "Privileged DSU environment diagnostics",
            backdrop = navBackdrop,
            onDismissRequest = onDismissModal,
        ) {
            Column {
                Text(
                    text = "Root access: ${if (state.isRootGranted) "Granted (uid=0)" else "Not granted"}",
                    fontSize = 14.sp,
                    color = textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "IDynamicSystemService: ${if (state.isPrivilegedConnected) "Connected" else "Disconnected"}",
                    fontSize = 14.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dynamic partitions: ${if (state.hasDynamicPartitions) "Supported" else "Not detected"}",
                    fontSize = 14.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onDismissModal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }

        val targetName = if (state.selectedSlot == OsSlot.SECONDARY) {
            state.secondaryOs.name
        } else {
            state.primaryOs.name
        }
        GlassDialog(
            show = state.activeModal == ActiveModal.REBOOT_CONFIRM,
            title = "Reboot device?",
            summary = "Your device will restart into $targetName.",
            backdrop = navBackdrop,
            onDismissRequest = onDismissModal,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onDismissModal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirmReboot,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("Reboot", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DualBootHomeTab(
    state: DualBootUiState,
    bottomPadding: Dp,
    onSelectSlot: (OsSlot) -> Unit,
    onRebootClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onOpenModal: (ActiveModal) -> Unit,
    onCancelInstall: () -> Unit,
    onDismissStatusCard: () -> Unit,
    onDiscardDsu: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val topBarBackdrop = rememberGlassBackdrop()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
    val contentScrolled by remember(listState) {
        computedStateOf { listState.canScrollBackward }
    }

    val accentBlue = MiuixTheme.colorScheme.primary
    val textPrimary = MiuixTheme.colorScheme.onBackground
    val textSecondary = MiuixTheme.colorScheme.onBackgroundVariant

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GlassTopAppBar(
                title = "Dual Boot",
                subtitle = "Manage operating systems installed on your device.",
                isContentScrolled = contentScrolled,
                backdrop = topBarBackdrop,
                progressiveBlur = topBarBackdrop != null,
                scrollBehavior = scrollBehavior,
                bandBrush = bandBrush,
                bandOverhang = GlassTopAppBarDefaults.BandOverhang,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (topBarBackdrop != null) Modifier.layerBackdrop(topBarBackdrop) else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding,
            ),
        ) {
            item {
                SmallTitle(
                    text = "Current operating system",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    val activeOs = if (state.isDsuInUse) state.secondaryOs else state.primaryOs
                    BasicComponent(
                        title = activeOs.name,
                        summary = "${activeOs.versionLine}  ·  Currently running",
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone),
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(26.dp),
                            )
                        },
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            item {
                SmallTitle(
                    text = "Select operating system",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    val primarySelected = state.selectedSlot == OsSlot.PRIMARY
                    val secondarySelected = state.selectedSlot == OsSlot.SECONDARY

                    RadioButtonPreference(
                        title = state.primaryOs.name,
                        summary = "${state.primaryOs.versionLine}\n" + if (!state.isDsuInUse) {
                            "Primary system  ·  Currently running"
                        } else {
                            "Primary system  ·  Ready to boot"
                        },
                        selected = primarySelected,
                        onClick = { onSelectSlot(OsSlot.PRIMARY) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val secondaryStatus = when {
                        state.isDsuInUse -> "Secondary system  ·  Currently running"
                        state.isDsuInstalled -> "Secondary system  ·  Ready to boot"
                        else -> "Secondary system  ·  Not installed"
                    }
                    RadioButtonPreference(
                        title = state.secondaryOs.name,
                        summary = "${state.secondaryOs.versionLine}\n$secondaryStatus",
                        selected = secondarySelected,
                        onClick = { onSelectSlot(OsSlot.SECONDARY) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                val canReboot = state.isRootGranted &&
                    state.isPrivilegedConnected &&
                    (state.selectedSlot == OsSlot.PRIMARY || state.isDsuInstalled) &&
                    state.installationStep == InstallationStep.IDLE

                Button(
                    onClick = onRebootClick,
                    enabled = canReboot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    cornerRadius = 26.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "REBOOT NOW",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val rebootCaption = when {
                    !state.isRootGranted -> "Root access required to switch or install DSU systems."
                    state.selectedSlot == OsSlot.SECONDARY && !state.isDsuInstalled ->
                        "No secondary DSU image installed. Select a firmware file below."
                    state.selectedSlot == OsSlot.SECONDARY ->
                        "Reboot into ${state.secondaryOs.name} (one-shot DSU boot)."
                    else ->
                        "Reboot into ${state.primaryOs.name} (${state.primaryOs.versionLine})."
                }
                Text(
                    text = rebootCaption,
                    fontSize = 12.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }

            item {
                val showStatusCard = state.installationStep != InstallationStep.IDLE ||
                    (state.isDsuInstalled && !state.isDsuInUse)
                AnimatedVisibility(
                    visible = showStatusCard,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        InstallationStatusCard(
                            state = state,
                            accentBlue = accentBlue,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onCancel = onCancelInstall,
                            onDismiss = onDismissStatusCard,
                            onDiscard = onDiscardDsu,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                SmallTitle(
                    text = "System management",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Upload file",
                        summary = "Select a local firmware file (.img, .xz, .gz, .zip)",
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_file),
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp),
                            )
                        },
                        onClick = onPickFileClick,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Installed systems",
                        summary = "View both operating systems",
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_list),
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp),
                            )
                        },
                        onClick = { onOpenModal(ActiveModal.INSTALLED_SYSTEMS) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Manage storage",
                        summary = "View space used by each system",
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_storage),
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp),
                            )
                        },
                        onClick = { onOpenModal(ActiveModal.MANAGE_STORAGE) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Device & root status",
                        summary = if (state.isRootGranted) "Root granted · Dynamic partitions" else "Root not granted",
                        startAction = {
                            Icon(
                                painter = painterResource(R.drawable.ic_shield),
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp),
                            )
                        },
                        onClick = { onOpenModal(ActiveModal.DEVICE_STATUS) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: DualBootUiState,
    bottomPadding: Dp,
    onRefreshStatus: () -> Unit,
    onCycleTheme: () -> Unit,
    onToggleBuiltinInstaller: (Boolean) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val topBarBackdrop = rememberGlassBackdrop()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
    val contentScrolled by remember(listState) {
        computedStateOf { listState.canScrollBackward }
    }
    val uriHandler = LocalUriHandler.current
    val textSecondary = MiuixTheme.colorScheme.onBackgroundVariant

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GlassTopAppBar(
                title = "Settings",
                subtitle = "Root environment and appearance",
                isContentScrolled = contentScrolled,
                backdrop = topBarBackdrop,
                progressiveBlur = topBarBackdrop != null,
                scrollBehavior = scrollBehavior,
                bandBrush = bandBrush,
                bandOverhang = GlassTopAppBarDefaults.BandOverhang,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (topBarBackdrop != null) Modifier.layerBackdrop(topBarBackdrop) else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding,
            ),
        ) {
            item {
                SmallTitle(
                    text = "Environment",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "Root access",
                        summary = if (state.isRootGranted) "Granted (uid=0)" else "Not granted",
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "Privileged DSU service",
                        summary = if (state.isPrivilegedConnected) "Connected to IDynamicSystemService" else "Disconnected",
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BasicComponent(
                        title = "Dynamic partitions",
                        summary = if (state.hasDynamicPartitions) "ro.boot.dynamic_partitions = true" else "Not detected",
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Refresh DSU & root status",
                        summary = "Re-query IDynamicSystemService",
                        onClick = onRefreshStatus,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            item {
                SmallTitle(
                    text = "Installer",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "Built-in DSU installer",
                        summary = if (state.useBuiltinInstaller) {
                            "Use built-in root installer with in-app progress"
                        } else {
                            "Forward installation to system DSU (com.android.dynsystem)"
                        },
                        checked = state.useBuiltinInstaller,
                        onCheckedChange = onToggleBuiltinInstaller,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            item {
                SmallTitle(
                    text = "Appearance",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "Theme mode",
                        summary = "Current: ${state.themeMode.label} (tap to switch)",
                        onClick = onCycleTheme,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            item {
                SmallTitle(
                    text = "About",
                    insideMargin = PaddingValues(start = 4.dp, bottom = 8.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "wectazz",
                        summary = "Developer · github.com/wectazz",
                        onClick = { uriHandler.openUri("https://github.com/wectazz") },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "DSU-Sideloader",
                        summary = "github.com/VegaBobo/DSU-Sideloader",
                        startAction = {
                            Image(
                                painter = painterResource(R.drawable.ic_dsu_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        },
                        onClick = { uriHandler.openUri("https://github.com/VegaBobo/DSU-Sideloader") },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Miuix Compose",
                        summary = "github.com/compose-miuix-ui/miuix",
                        startAction = {
                            Image(
                                painter = painterResource(R.drawable.ic_miuix_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        },
                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallationStatusCard(
    state: DualBootUiState,
    accentBlue: Color,
    textPrimary: Color,
    textSecondary: Color,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
        ) {
            val effectiveStep = if (
                state.installationStep == InstallationStep.IDLE &&
                state.isDsuInstalled &&
                !state.isDsuInUse
            ) {
                InstallationStep.SUCCESS
            } else {
                state.installationStep
            }
            val title = when (effectiveStep) {
                InstallationStep.DECOMPRESSING -> "Decompressing ${state.installDetail}..."
                InstallationStep.CREATING_PARTITION -> "Creating ${state.installDetail} partition..."
                InstallationStep.INSTALLING -> {
                    if (state.installDetail.startsWith("Install", ignoreCase = true)) {
                        state.installDetail
                    } else {
                        "Installing ${state.installDetail}..."
                    }
                }
                InstallationStep.FINISHING -> "Finalizing DSU slot..."
                InstallationStep.SUCCESS -> "DSU installed successfully"
                InstallationStep.ERROR -> "Installation failed"
                InstallationStep.IDLE -> ""
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (effectiveStep == InstallationStep.ERROR) Color(0xFFFF453A) else textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (effectiveStep == InstallationStep.ERROR) {
                Text(
                    text = state.errorMessage ?: "Unknown error",
                    fontSize = 13.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Dismiss")
                }
            } else if (effectiveStep == InstallationStep.SUCCESS) {
                Text(
                    text = "Ready to boot. Tap REBOOT NOW above to switch into DSU.",
                    fontSize = 13.sp,
                    color = accentBlue,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Discard", color = Color(0xFFFF453A))
                }
            } else {
                LinearProgressIndicator(
                    progress = state.installProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(state.installProgress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFFFF453A),
                    )
                }
            }
        }
    }
}
