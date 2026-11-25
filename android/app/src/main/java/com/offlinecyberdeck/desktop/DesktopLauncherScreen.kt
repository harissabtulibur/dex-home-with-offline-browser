package com.offlinecyberdeck.desktop

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.archive.ArchiveReaderScreen
import com.offlinecyberdeck.bookmarks.BookmarksScreen
import com.offlinecyberdeck.bookmarks.HistoryScreen
import com.offlinecyberdeck.browser.BrowserScreen
import com.offlinecyberdeck.browser.BrowserViewModel
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.model.LibraryFileType
import com.offlinecyberdeck.downloads.DownloadsScreen
import com.offlinecyberdeck.library.LibraryScreen
import com.offlinecyberdeck.library.PdfViewerScreen
import com.offlinecyberdeck.library.TextDocumentReaderScreen
import com.offlinecyberdeck.library.ZimViewerScreen
import com.offlinecyberdeck.media.MediaPlayerScreen
import com.offlinecyberdeck.media.MediaVaultScreen
import com.offlinecyberdeck.search.UnifiedSearchScreen
import com.offlinecyberdeck.settings.SettingsScreen
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt

enum class DesktopWindowKind(val defaultTitle: String) {
    BROWSER("Cyber Deck Browser"),
    LIBRARY("Knowledge Vault"),
    ARCHIVES("Web Archives"),
    MEDIA("OfflineTube"),
    DOWNLOADS("Downloads"),
    BOOKMARKS("Bookmarks"),
    HISTORY("History"),
    SEARCH("Offline Search"),
    SETTINGS("Settings"),
    ARCHIVE_READER("Archive Reader"),
    PDF_READER("PDF Viewer"),
    TEXT_READER("Document Reader"),
    ZIM_READER("ZIM Encyclopedia"),
    MEDIA_PLAYER("Media Player")
}

data class DesktopWindowState(
    val id: String = UUID.randomUUID().toString(),
    val kind: DesktopWindowKind,
    val title: String = kind.defaultTitle,
    val refId: String = "",
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val xDp: Float = 40f,
    val yDp: Float = 20f,
    val widthDp: Float = 760f,
    val heightDp: Float = 520f,
    val zIndex: Int = 1
)

@Composable
fun DesktopLauncherScreen(
    app: CyberDeckApplication,
    browserViewModel: BrowserViewModel,
    settings: AppSettingsEntity,
    onShowPermissionsDialog: () -> Unit,
    onSwitchToClassicMode: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isOnline by browserViewModel.isOnline.collectAsStateWithLifecycle()

    val windows = remember { mutableStateListOf<DesktopWindowState>() }
    var topZ by remember { mutableIntStateOf(10) }
    var appDrawerOpen by remember { mutableStateOf(false) }
    var launchExternalFloating by remember { mutableStateOf(true) }
    var externalCascadeIndex by remember { mutableIntStateOf(0) }

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var appSearchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var permStatus by remember { mutableStateOf(DesktopLauncherManager.inspectPermissions(context)) }
    var clockText by remember { mutableStateOf("") }
    var batteryPct by remember { mutableStateOf<Int?>(null) }

    // Open Cyber Deck Browser inside the DeX desktop on initial start
    LaunchedEffect(Unit) {
        if (windows.isEmpty()) {
            windows += DesktopWindowState(
                id = "main_browser",
                kind = DesktopWindowKind.BROWSER,
                title = "Cyber Deck Browser",
                isMaximized = true,
                zIndex = ++topZ
            )
        }
        installedApps = DesktopLauncherManager.loadInstalledApps(context)
    }

    // Refresh permissions & installed apps on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permStatus = DesktopLauncherManager.inspectPermissions(context)
                scope.launch {
                    installedApps = DesktopLauncherManager.loadInstalledApps(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Live clock & battery status
    LaunchedEffect(Unit) {
        while (true) {
            clockText = DateFormat.format("HH:mm", Date()).toString()
            batteryPct = DesktopLauncherManager.readBatteryPercent(context)
            delay(20_000L)
        }
    }

    val pinnedPackages = remember(settings.pinnedExternalPackages) {
        settings.pinnedExternalPackages
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun bringToFront(winId: String) {
        val idx = windows.indexOfFirst { it.id == winId }
        if (idx >= 0) {
            windows[idx] = windows[idx].copy(isMinimized = false, zIndex = ++topZ)
        }
    }

    fun openOrFocusWindow(
        kind: DesktopWindowKind,
        refId: String = "",
        title: String = kind.defaultTitle,
        startMaximized: Boolean = false
    ) {
        val existingIdx = windows.indexOfFirst {
            it.kind == kind && (refId.isBlank() || it.refId == refId)
        }
        if (existingIdx >= 0) {
            windows[existingIdx] = windows[existingIdx].copy(
                title = title,
                isMinimized = false,
                zIndex = ++topZ
            )
            appDrawerOpen = false
            return
        }

        // Enforce 4-window memory cap for 4 GB RAM tablets (close oldest non-browser window if > 3)
        if (windows.size >= 4) {
            val evictIdx = windows.indexOfFirst { it.kind != DesktopWindowKind.BROWSER }
            if (evictIdx >= 0) windows.removeAt(evictIdx)
        }

        val cascade = (windows.size % 4) * 28f
        windows += DesktopWindowState(
            id = if (kind == DesktopWindowKind.BROWSER) "main_browser" else UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            refId = refId,
            isMinimized = false,
            isMaximized = startMaximized,
            xDp = 56f + cascade,
            yDp = 24f + cascade,
            widthDp = 780f,
            heightDp = 500f,
            zIndex = ++topZ
        )
        appDrawerOpen = false
    }

    fun toggleMinimize(winId: String) {
        val idx = windows.indexOfFirst { it.id == winId }
        if (idx >= 0) {
            val w = windows[idx]
            if (w.isMinimized) {
                windows[idx] = w.copy(isMinimized = false, zIndex = ++topZ)
            } else {
                windows[idx] = w.copy(isMinimized = true)
            }
        }
    }

    fun toggleMaximize(winId: String) {
        val idx = windows.indexOfFirst { it.id == winId }
        if (idx >= 0) {
            val w = windows[idx]
            windows[idx] = w.copy(isMaximized = !w.isMaximized, isMinimized = false, zIndex = ++topZ)
        }
    }

    fun closeWindow(winId: String) {
        windows.removeAll { it.id == winId }
    }

    fun togglePinExternalApp(pkg: String) {
        scope.launch {
            val next = if (pkg in pinnedPackages) {
                pinnedPackages - pkg
            } else {
                pinnedPackages + pkg
            }
            app.settingsRepository.updateSettings {
                it.copy(pinnedExternalPackages = next.joinToString(","))
            }
            onShowMessage(
                if (pkg in next) "Aplikasi disematkan ke Desktop & Taskbar"
                else "Pin desktop dilepas"
            )
        }
    }

    fun launchExternalPackage(pkg: String) {
        DesktopLauncherManager.launchExternalApp(
            context = context,
            packageName = pkg,
            floating = launchExternalFloating,
            cascadeIndex = externalCascadeIndex++
        ).onSuccess { msg ->
            appDrawerOpen = false
            onShowMessage(msg)
        }.onFailure { err ->
            onShowMessage("Gagal membuka app: ${err.message}")
        }
    }

    val openUrlInDesktopBrowser: (String, Boolean) -> Unit = { url, incognito ->
        openOrFocusWindow(DesktopWindowKind.BROWSER, startMaximized = false)
        if (incognito) {
            browserViewModel.createNewTab(url = url, isIncognito = true)
        } else {
            browserViewModel.navigateFromInput(url)
        }
    }

    val openLibraryItemById: (String) -> Unit = { itemId ->
        scope.launch {
            val item = app.database.libraryDao().getById(itemId)
            if (item == null) {
                onShowMessage("Item tidak ditemukan.")
            } else if (!app.vaultRepository.isItemAvailable(item)) {
                onShowMessage("File tidak tersedia — cek koneksi Deck Drive.")
            } else {
                when (item.fileType) {
                    LibraryFileType.PDF -> openOrFocusWindow(DesktopWindowKind.PDF_READER, item.id, item.title)
                    LibraryFileType.ZIM -> openOrFocusWindow(DesktopWindowKind.ZIM_READER, item.id, item.title)
                    LibraryFileType.EPUB,
                    LibraryFileType.HTML,
                    LibraryFileType.TEXT -> openOrFocusWindow(DesktopWindowKind.TEXT_READER, item.id, item.title)
                    else -> {
                        val intent = app.vaultRepository.buildExternalViewIntent(item)
                        if (intent != null) {
                            runCatching { context.startActivity(intent) }
                                .onFailure { onShowMessage("Tidak ada aplikasi untuk format ${item.fileType.label}") }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = appDrawerOpen || windows.any { !it.isMinimized }) {
        if (appDrawerOpen) {
            appDrawerOpen = false
        } else {
            val topWin = windows.filter { !it.isMinimized }.maxByOrNull { it.zIndex }
            if (topWin != null) {
                toggleMinimize(topWin.id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B12),
                        Color(0xFF0E1726),
                        Color(0xFF09111B)
                    )
                )
            )
    ) {
        // DESKTOP WORKSPACE AREA
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val deskWidthDp = maxWidth.value
            val deskHeightDp = maxHeight.value
            val density = LocalDensity.current

            // Desktop Shortcuts Grid (always underneath windows)
            val builtInShortcuts = listOf(
                Triple(DesktopWindowKind.BROWSER, "Cyber Browser", Icons.Filled.Public),
                Triple(DesktopWindowKind.LIBRARY, "Knowledge Vault", Icons.Filled.MenuBook),
                Triple(DesktopWindowKind.MEDIA, "OfflineTube", Icons.Filled.VideoLibrary),
                Triple(DesktopWindowKind.DOWNLOADS, "Downloads", Icons.Filled.Download),
                Triple(DesktopWindowKind.SEARCH, "Offline Search", Icons.Filled.Search),
                Triple(DesktopWindowKind.BOOKMARKS, "Bookmarks", Icons.Filled.Bookmark),
                Triple(DesktopWindowKind.HISTORY, "History", Icons.Filled.History),
                Triple(DesktopWindowKind.SETTINGS, "Settings", Icons.Filled.Settings)
            )

            val pinnedInfos = remember(installedApps, pinnedPackages) {
                pinnedPackages.mapNotNull { pkg -> installedApps.firstOrNull { it.packageName == pkg } }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CYBER DECK DESKTOP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = if (permStatus.allCoreReady) DeckGreen.copy(alpha = 0.16f) else DeckAmber.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (permStatus.allCoreReady) DeckGreen.copy(alpha = 0.5f) else DeckAmber.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.clickable { onShowPermissionsDialog() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (permStatus.allCoreReady) DeckGreen else DeckAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (permStatus.allCoreReady) "Izin DeX Siap" else "Atur Izin Floating & Launcher",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (permStatus.allCoreReady) DeckGreen else DeckAmber
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(builtInShortcuts, key = { it.first.name }) { (kind, label, icon) ->
                        DesktopShortcutTile(
                            label = label,
                            onClick = { openOrFocusWindow(kind) },
                            iconContent = {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        )
                    }

                    item(key = "dex_permissions_shortcut") {
                        DesktopShortcutTile(
                            label = "Izin DeX & Floating",
                            onClick = onShowPermissionsDialog,
                            iconContent = {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DeckAmber.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.AdminPanelSettings,
                                        contentDescription = "Izin DeX",
                                        tint = DeckAmber,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        )
                    }

                    items(pinnedInfos, key = { "pin_${it.packageName}" }) { extApp ->
                        DesktopShortcutTile(
                            label = extApp.label,
                            onClick = { launchExternalPackage(extApp.packageName) },
                            iconContent = {
                                InstalledAppIcon(
                                    packageName = extApp.packageName,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        )
                    }
                }
            }

            // OPEN INTERNAL FLOATING WINDOWS
            val sortedWindows = windows.sortedBy { it.zIndex }
            sortedWindows.forEach { win ->
                if (!win.isMinimized) {
                    key(win.id) {
                        val clampedW = win.widthDp.coerceIn(300f, deskWidthDp.coerceAtLeast(320f))
                        val clampedH = win.heightDp.coerceIn(240f, deskHeightDp.coerceAtLeast(260f))
                        val clampedX = win.xDp.coerceIn(0f, (deskWidthDp - clampedW).coerceAtLeast(0f))
                        val clampedY = win.yDp.coerceIn(0f, (deskHeightDp - clampedH).coerceAtLeast(0f))

                        val windowModifier = if (win.isMaximized) {
                            Modifier
                                .fillMaxSize()
                                .zIndex(win.zIndex.toFloat())
                        } else {
                            Modifier
                                .offset {
                                    with(density) {
                                        IntOffset(clampedX.dp.roundToPx(), clampedY.dp.roundToPx())
                                    }
                                }
                                .width(clampedW.dp)
                                .height(clampedH.dp)
                                .zIndex(win.zIndex.toFloat())
                        }

                        Surface(
                            shape = if (win.isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            shadowElevation = if (win.isMaximized) 0.dp else 12.dp,
                            border = if (win.isMaximized) null else BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            ),
                            modifier = windowModifier
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // DeX-style Window Title Bar (Draggable + Minimize / Maximize / Close)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .pointerInput(win.id, win.isMaximized) {
                                                detectDragGestures(
                                                    onDragStart = { bringToFront(win.id) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (!win.isMaximized) {
                                                            val idx = windows.indexOfFirst { it.id == win.id }
                                                            if (idx >= 0) {
                                                                val cur = windows[idx]
                                                                val dxDp = dragAmount.x / density.density
                                                                val dyDp = dragAmount.y / density.density
                                                                windows[idx] = cur.copy(
                                                                    xDp = (cur.xDp + dxDp).coerceIn(
                                                                        0f,
                                                                        (deskWidthDp - cur.widthDp).coerceAtLeast(0f)
                                                                    ),
                                                                    yDp = (cur.yDp + dyDp).coerceIn(
                                                                        0f,
                                                                        (deskHeightDp - cur.heightDp).coerceAtLeast(0f)
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            .clickable { bringToFront(win.id) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = windowIconForKind(win.kind),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = win.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Minimize
                                        IconButton(
                                            onClick = { toggleMinimize(win.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Remove,
                                                contentDescription = "Minimize",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        // Maximize / Restore
                                        IconButton(
                                            onClick = { toggleMaximize(win.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                if (win.isMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                                                contentDescription = if (win.isMaximized) "Restore" else "Maximize",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        // Close
                                        IconButton(
                                            onClick = { closeWindow(win.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Close",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                    // Window Content
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    ) {
                                        when (win.kind) {
                                            DesktopWindowKind.BROWSER -> {
                                                BrowserScreen(
                                                    viewModel = browserViewModel,
                                                    onOpenDownloads = { openOrFocusWindow(DesktopWindowKind.DOWNLOADS) },
                                                    onOpenUnifiedSearch = { openOrFocusWindow(DesktopWindowKind.SEARCH) },
                                                    onOpenArchives = { openOrFocusWindow(DesktopWindowKind.ARCHIVES) },
                                                    onOpenBookmarks = { openOrFocusWindow(DesktopWindowKind.BOOKMARKS) },
                                                    onOpenHistory = { openOrFocusWindow(DesktopWindowKind.HISTORY) },
                                                    onOpenSettings = { openOrFocusWindow(DesktopWindowKind.SETTINGS) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.LIBRARY -> {
                                                LibraryScreen(
                                                    app = app,
                                                    initialTabIndex = 0,
                                                    onOpenArchiveReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, id, "Archive Reader")
                                                    },
                                                    onOpenPdfReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.PDF_READER, id, "PDF Viewer")
                                                    },
                                                    onOpenTextReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.TEXT_READER, id, "Document Reader")
                                                    },
                                                    onOpenZimReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ZIM_READER, id, "ZIM Reader")
                                                    },
                                                    onOpenBrowserUrl = { url -> openUrlInDesktopBrowser(url, false) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.ARCHIVES -> {
                                                LibraryScreen(
                                                    app = app,
                                                    initialTabIndex = 1,
                                                    onOpenArchiveReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, id, "Archive Reader")
                                                    },
                                                    onOpenPdfReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.PDF_READER, id, "PDF Viewer")
                                                    },
                                                    onOpenTextReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.TEXT_READER, id, "Document Reader")
                                                    },
                                                    onOpenZimReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ZIM_READER, id, "ZIM Reader")
                                                    },
                                                    onOpenBrowserUrl = { url -> openUrlInDesktopBrowser(url, false) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.MEDIA -> {
                                                MediaVaultScreen(
                                                    app = app,
                                                    onPlayMedia = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.MEDIA_PLAYER, id, "OfflineTube Player")
                                                    },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.DOWNLOADS -> {
                                                DownloadsScreen(app = app, onShowMessage = onShowMessage)
                                            }
                                            DesktopWindowKind.BOOKMARKS -> {
                                                BookmarksScreen(
                                                    app = app,
                                                    onOpenUrl = openUrlInDesktopBrowser
                                                )
                                            }
                                            DesktopWindowKind.HISTORY -> {
                                                HistoryScreen(
                                                    app = app,
                                                    onOpenUrl = { url -> openUrlInDesktopBrowser(url, false) }
                                                )
                                            }
                                            DesktopWindowKind.SEARCH -> {
                                                UnifiedSearchScreen(
                                                    app = app,
                                                    onOpenArchive = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, id, "Archive Reader")
                                                    },
                                                    onOpenLibraryItem = openLibraryItemById,
                                                    onOpenMediaItem = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.MEDIA_PLAYER, id, "OfflineTube Player")
                                                    },
                                                    onOpenUrl = { url -> openUrlInDesktopBrowser(url, false) }
                                                )
                                            }
                                            DesktopWindowKind.SETTINGS -> {
                                                SettingsScreen(
                                                    app = app,
                                                    onShowMessage = onShowMessage,
                                                    onOpenDexPermissions = onShowPermissionsDialog
                                                )
                                            }
                                            DesktopWindowKind.ARCHIVE_READER -> {
                                                ArchiveReaderScreen(
                                                    app = app,
                                                    archiveId = win.refId,
                                                    onBack = { closeWindow(win.id) },
                                                    onOpenArchiveId = { nextId ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, nextId, "Archive Reader")
                                                    },
                                                    onOpenLiveUrl = { url -> openUrlInDesktopBrowser(url, false) }
                                                )
                                            }
                                            DesktopWindowKind.PDF_READER -> {
                                                PdfViewerScreen(
                                                    app = app,
                                                    itemId = win.refId,
                                                    onBack = { closeWindow(win.id) }
                                                )
                                            }
                                            DesktopWindowKind.TEXT_READER -> {
                                                TextDocumentReaderScreen(
                                                    app = app,
                                                    itemId = win.refId,
                                                    onBack = { closeWindow(win.id) }
                                                )
                                            }
                                            DesktopWindowKind.ZIM_READER -> {
                                                ZimViewerScreen(
                                                    app = app,
                                                    itemId = win.refId,
                                                    webTextZoom = settings.webViewTextZoom,
                                                    onBack = { closeWindow(win.id) }
                                                )
                                            }
                                            DesktopWindowKind.MEDIA_PLAYER -> {
                                                MediaPlayerScreen(
                                                    app = app,
                                                    mediaId = win.refId,
                                                    onBack = { closeWindow(win.id) },
                                                    onSelectOtherMedia = { nextId ->
                                                        openOrFocusWindow(DesktopWindowKind.MEDIA_PLAYER, nextId, "OfflineTube Player")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom-right Resize Grip when floating
                                if (!win.isMaximized) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(22.dp)
                                            .pointerInput(win.id) {
                                                detectDragGestures(
                                                    onDragStart = { bringToFront(win.id) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val idx = windows.indexOfFirst { it.id == win.id }
                                                        if (idx >= 0) {
                                                            val cur = windows[idx]
                                                            val dw = dragAmount.x / density.density
                                                            val dh = dragAmount.y / density.density
                                                            windows[idx] = cur.copy(
                                                                widthDp = (cur.widthDp + dw).coerceIn(
                                                                    300f,
                                                                    (deskWidthDp - cur.xDp).coerceAtLeast(300f)
                                                                ),
                                                                heightDp = (cur.heightDp + dh).coerceIn(
                                                                    240f,
                                                                    (deskHeightDp - cur.yDp).coerceAtLeast(240f)
                                                                )
                                                            )
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.OpenInFull,
                                            contentDescription = "Resize window",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // APP DRAWER (START MENU) OVERLAY
            if (appDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9999f)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { appDrawerOpen = false }
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .widthIn(max = 680.dp)
                            .fillMaxWidth(0.94f)
                            .heightIn(max = (deskHeightDp * 0.85f).dp)
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("App Drawer & Cyber Deck", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        appDrawerOpen = false
                                        onShowPermissionsDialog()
                                    }) {
                                        Icon(
                                            Icons.Filled.AdminPanelSettings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Izin DeX")
                                    }
                                    IconButton(onClick = { appDrawerOpen = false }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                placeholder = { Text("Cari WhatsApp, YouTube, Files, atau aplikasi lain...") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = launchExternalFloating,
                                    onClick = { launchExternalFloating = true },
                                    label = { Text("Buka Floating (Freeform)") }
                                )
                                FilterChip(
                                    selected = !launchExternalFloating,
                                    onClick = { launchExternalFloating = false },
                                    label = { Text("Buka Fullscreen") }
                                )
                                FilterChip(
                                    selected = showSystemApps,
                                    onClick = { showSystemApps = !showSystemApps },
                                    label = { Text("Tampilkan App Sistem") }
                                )
                            }

                            // Built-in Cyber Deck Apps quick strip
                            Text(
                                "Cyber Deck Modules (Window Internal)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                builtInShortcuts.forEach { (kind, label, icon) ->
                                    FilterChip(
                                        selected = windows.any { it.kind == kind && !it.isMinimized },
                                        onClick = { openOrFocusWindow(kind) },
                                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            HorizontalDivider()

                            val filteredApps = remember(installedApps, appSearchQuery, showSystemApps) {
                                installedApps.filter { appInfo ->
                                    val sysOk = showSystemApps || !appInfo.isSystemApp ||
                                        appInfo.label.contains("youtube", true) ||
                                        appInfo.label.contains("chrome", true) ||
                                        appInfo.label.contains("files", true) ||
                                        appInfo.label.contains("gallery", true) ||
                                        appInfo.label.contains("play store", true)
                                    val qOk = appSearchQuery.isBlank() ||
                                        appInfo.label.contains(appSearchQuery, true) ||
                                        appInfo.packageName.contains(appSearchQuery, true)
                                    sysOk && qOk
                                }
                            }

                            Text(
                                "Aplikasi Android Terinstall (${filteredApps.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(filteredApps, key = { it.packageName }) { extApp ->
                                    val isPinned = extApp.packageName in pinnedPackages
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        ),
                                        modifier = Modifier.clickable { launchExternalPackage(extApp.packageName) }
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                            IconButton(
                                                onClick = { togglePinExternalApp(extApp.packageName) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.PushPin,
                                                    contentDescription = "Pin to Desktop",
                                                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                            ) {
                                                InstalledAppIcon(
                                                    packageName = extApp.packageName,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = extApp.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM DeX TASKBAR
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start / App Drawer Button
                Surface(
                    color = if (appDrawerOpen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { appDrawerOpen = !appDrawerOpen }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Filled.Apps,
                            contentDescription = "App Drawer",
                            tint = if (appDrawerOpen) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Apps",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (appDrawerOpen) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Quick Taskbar items (Open/Minimized Windows + Pinned External Apps)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (windows.none { it.kind == DesktopWindowKind.BROWSER }) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { openOrFocusWindow(DesktopWindowKind.BROWSER) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Browser", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    windows.forEach { win ->
                        val active = !win.isMinimized && windows.maxByOrNull { it.zIndex }?.id == win.id
                        Surface(
                            color = when {
                                active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                win.isMinimized -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { toggleMinimize(win.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    windowIconForKind(win.kind),
                                    contentDescription = null,
                                    tint = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = win.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                            }
                        }
                    }

                    // Pinned External Apps quick icons on Taskbar
                    pinnedPackages.forEach { pkg ->
                        val label = installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { launchExternalPackage(pkg) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                InstalledAppIcon(packageName = pkg, modifier = Modifier.size(18.dp))
                                Text(
                                    label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.widthIn(max = 80.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Right System Tray
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onShowPermissionsDialog,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = "Izin DeX & Floating",
                            tint = if (permStatus.allCoreReady) DeckGreen else DeckAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onSwitchToClassicMode,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.Fullscreen,
                            contentDescription = "Mode Klasik Fullscreen",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isOnline) "NET" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOnline) DeckGreen else DeckAmber,
                        fontFamily = FontFamily.Monospace
                    )
                    if (batteryPct != null) {
                        Text(
                            text = "$batteryPct%",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (clockText.isNotBlank()) {
                        Text(
                            text = clockText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DexPermissionsSetupDialog(
    onDismiss: () -> Unit,
    onDismissForever: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(DesktopLauncherManager.inspectPermissions(context)) }

    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = DesktopLauncherManager.inspectPermissions(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = DesktopLauncherManager.inspectPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Izin Launcher DeX & Floating Apps")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Aktifkan izin & pengaturan sistem berikut agar Cyber Deck bisa menjadi Home Launcher dan membuka WhatsApp, YouTube, dll. sebagai jendela floating:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionShortcutRow(
                    title = "1. Home Launcher Utama",
                    subtitle = if (status.isDefaultHomeLauncher) "Cyber Deck sudah menjadi Home Launcher utama."
                    else "Atur Cyber Deck sebagai aplikasi layar utama / launcher tablet.",
                    granted = status.isDefaultHomeLauncher,
                    buttonLabel = "Pilih Launcher",
                    onAction = {
                        val roleIntent = DesktopLauncherManager.buildDefaultHomeRoleIntentOrNull(context)
                        if (roleIntent != null) {
                            runCatching { homeRoleLauncher.launch(roleIntent) }
                                .onFailure {
                                    onShowMessage(DesktopLauncherManager.openHomeLauncherSettings(context))
                                }
                        } else {
                            onShowMessage(DesktopLauncherManager.openHomeLauncherSettings(context))
                        }
                    }
                )

                PermissionShortcutRow(
                    title = "2. Izin Overlay (Tampil di Atas App Lain)",
                    subtitle = if (status.canDrawOverlays) "Sudah diizinkan."
                    else "Diperlukan untuk dukungan overlay & kontrol mengambang.",
                    granted = status.canDrawOverlays,
                    buttonLabel = "Izinkan Overlay",
                    onAction = {
                        onShowMessage(DesktopLauncherManager.openOverlayPermissionSettings(context))
                    }
                )

                PermissionShortcutRow(
                    title = "3. Freeform / Floating Windows (WA, YouTube, dll.)",
                    subtitle = if (status.isFreeformEnabledInSettings || status.hasFreeformFeature) {
                        "Dukungan Freeform terdeteksi. Di One UI, pastikan Multi-window untuk semua aplikasi juga aktif."
                    } else {
                        "Buka Developer Options lalu aktifkan 'Enable freeform windows' & 'Force activities to be resizable'."
                    },
                    granted = status.isFreeformEnabledInSettings || status.hasFreeformFeature,
                    buttonLabel = "Buka Developer Options",
                    onAction = {
                        onShowMessage(DesktopLauncherManager.openDeveloperOptionsSettings(context))
                    }
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("4. Shortcut Samsung One UI / Baterai", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Untuk Galaxy Tab A9: aktifkan Multi-jendela untuk semua aplikasi (Fitur Lanjutan → Labs) dan atur baterai Cyber Deck ke Tidak Dibatasi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onShowMessage(DesktopLauncherManager.openSystemMultiWindowSettings(context)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Setelan Display/Labs", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { onShowMessage(DesktopLauncherManager.openAppDetailsSettings(context)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Info App & Baterai", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { status = DesktopLauncherManager.inspectPermissions(context) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cek Lagi")
                }
                Button(onClick = onDismiss) { Text("Selesai") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissForever) { Text("Jangan Tampilkan Lagi") }
        }
    )
}

@Composable
private fun PermissionShortcutRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (granted) DeckGreen else DeckAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun DesktopShortcutTile(
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        iconContent()
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InstalledAppIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            DesktopLauncherManager.loadAppIconBitmap(context, packageName)
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = packageName,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Apps, contentDescription = null)
        }
    }
}

private fun windowIconForKind(kind: DesktopWindowKind): ImageVector = when (kind) {
    DesktopWindowKind.BROWSER -> Icons.Filled.Public
    DesktopWindowKind.LIBRARY,
    DesktopWindowKind.ARCHIVES,
    DesktopWindowKind.ARCHIVE_READER,
    DesktopWindowKind.PDF_READER,
    DesktopWindowKind.TEXT_READER,
    DesktopWindowKind.ZIM_READER -> Icons.Filled.MenuBook
    DesktopWindowKind.MEDIA,
    DesktopWindowKind.MEDIA_PLAYER -> Icons.Filled.VideoLibrary
    DesktopWindowKind.DOWNLOADS -> Icons.Filled.Download
    DesktopWindowKind.BOOKMARKS -> Icons.Filled.Bookmark
    DesktopWindowKind.HISTORY -> Icons.Filled.History
    DesktopWindowKind.SEARCH -> Icons.Filled.Search
    DesktopWindowKind.SETTINGS -> Icons.Filled.Settings
}
