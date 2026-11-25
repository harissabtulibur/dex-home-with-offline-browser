package com.offlinecyberdeck.library

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.LibraryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

private const val ZIM_HOST = "zim.offline"
private const val ZIM_ORIGIN = "https://$ZIM_HOST"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZimViewerScreen(
    app: CyberDeckApplication,
    itemId: String,
    webTextZoom: Int = 100,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var item by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var reader by remember { mutableStateOf<ZimArchiveReader?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingArchive by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("ZIM Archive Reader") }
    var canGoBackWeb by remember { mutableStateOf(false) }
    var mainPageUrl by remember { mutableStateOf("$ZIM_ORIGIN/__index__") }

    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<ZimSearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var nightFilterEnabled by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    LaunchedEffect(itemId) {
        isLoadingArchive = true
        errorMessage = null
        val loaded = app.database.libraryDao().getById(itemId)
        item = loaded
        if (loaded == null) {
            errorMessage = "ZIM archive record not found in Knowledge Vault."
            isLoadingArchive = false
            return@LaunchedEffect
        }
        pageTitle = loaded.title

        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = app.driveManager.openReadFileDescriptor(loaded.fileUri)
                    ?: error("Cannot access ZIM file. Reconnect Deck Drive if stored externally.")
                val opened = ZimArchiveReader(pfd)
                val startKey = opened.getMainPageKey()
                val startUrl = if (!startKey.isNullOrBlank()) {
                    "$ZIM_ORIGIN/${startKey.trimStart('/')}"
                } else {
                    "$ZIM_ORIGIN/__index__"
                }
                opened to startUrl
            }.onSuccess { (opened, startUrl) ->
                reader = opened
                mainPageUrl = startUrl
                isLoadingArchive = false
            }.onFailure { err ->
                errorMessage = "Failed opening ZIM: ${err.message}"
                isLoadingArchive = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
            reader?.close()
            reader = null
        }
    }

    BackHandler {
        val wv = webViewRef
        if (searchVisible) {
            searchVisible = false
        } else if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    LaunchedEffect(nightFilterEnabled, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, nightFilterEnabled) }
        }
        val script = if (nightFilterEnabled) {
            """
            (function(){
              var id='cd-zim-dark';
              if(!document.getElementById(id)){
                var s=document.createElement('style');
                s.id=id;
                s.textContent='html{filter:invert(0.9) hue-rotate(180deg);background:#111!important;} img,video,svg{filter:invert(1) hue-rotate(180deg);}';
                (document.head||document.documentElement).appendChild(s);
              }
            })();
            """.trimIndent()
        } else {
            "(function(){var el=document.getElementById('cd-zim-dark'); if(el) el.remove();})();"
        }
        wv.evaluateJavascript(script, null)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val activeReader = reader
                    if (activeReader != null) {
                        Text(
                            text = "ZIM v${activeReader.header.majorVersion} • ${activeReader.header.entryCount} entries",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { webViewRef?.loadUrl(mainPageUrl) },
                    enabled = reader != null
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "ZIM Home")
                }
                IconButton(
                    onClick = { nightFilterEnabled = !nightFilterEnabled },
                    enabled = reader != null
                ) {
                    Icon(
                        Icons.Filled.Contrast,
                        contentDescription = "Toggle Dark Reading",
                        tint = if (nightFilterEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { searchVisible = !searchVisible },
                    enabled = reader != null
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search inside ZIM")
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Close ZIM Reader")
                }
            }
        }

        if (isLoadingArchive || isPageLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (searchVisible && reader != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { next ->
                            searchQuery = next
                            searchJob?.cancel()
                            val activeReader = reader ?: return@OutlinedTextField
                            searchJob = scope.launch {
                                delay(220L)
                                if (next.isBlank()) {
                                    searchHits = emptyList()
                                    return@launch
                                }
                                isSearching = true
                                val hits = withContext(Dispatchers.IO) {
                                    activeReader.searchTitles(next, limit = 35)
                                }
                                searchHits = hits
                                isSearching = false
                            }
                        },
                        singleLine = true,
                        placeholder = { Text("Search article titles inside ZIM...") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchHits = emptyList()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isSearching) {
                        Text(
                            "Searching ZIM index...",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else if (searchHits.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .padding(top = 6.dp)
                        ) {
                            LazyColumn {
                                items(searchHits, key = { "${it.entryIndex}_${it.fullKey}" }) { hit ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchVisible = false
                                                webViewRef?.loadUrl("$ZIM_ORIGIN/${hit.fullKey.trimStart('/')}")
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = hit.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = hit.fullKey,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Text(
                            "No matching titles found for \"$searchQuery\".",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val activeReader = reader
            when {
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                activeReader == null -> {
                    Text(
                        text = "Opening ZIM archive index (low-RAM stream mode)...",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                    textZoom = webTextZoom.coerceIn(75, 150)
                                    allowFileAccess = false
                                    allowContentAccess = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        canGoBackWeb = view?.canGoBack() == true
                                        val title = view?.title?.takeIf { it.isNotBlank() && it != "about:blank" }
                                        if (title != null) pageTitle = title
                                        if (nightFilterEnabled) {
                                            view?.evaluateJavascript(
                                                "(function(){var id='cd-zim-dark';if(!document.getElementById(id)){var s=document.createElement('style');s.id=id;s.textContent='html{filter:invert(0.9) hue-rotate(180deg);background:#111!important;} img,video,svg{filter:invert(1) hue-rotate(180deg);}';(document.head||document.documentElement).appendChild(s);}})();",
                                                null
                                            )
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url ?: return false
                                        // Allow internal navigation within zim.offline; block external internet leaks
                                        return url.host != ZIM_HOST
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val uri = request?.url ?: return emptyNotFound()
                                        if (uri.host != ZIM_HOST) {
                                            return emptyNotFound()
                                        }
                                        val path = uri.encodedPath.orEmpty().trimStart('/')
                                        if (path == "__index__" || path.isBlank() && mainPageUrl.endsWith("__index__")) {
                                            val html = buildFallbackIndexHtml(
                                                title = item?.title ?: "ZIM Archive",
                                                entryCount = activeReader.header.entryCount,
                                                samples = activeReader.getSampleArticles(40)
                                            )
                                            return WebResourceResponse(
                                                "text/html",
                                                "UTF-8",
                                                ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                                            )
                                        }
                                        val loaded = activeReader.loadWebPath(path).getOrNull()
                                            ?: return emptyNotFound()
                                        val encoding = if (loaded.mimeType.startsWith("text/") ||
                                            loaded.mimeType.contains("javascript") ||
                                            loaded.mimeType.contains("svg")
                                        ) "UTF-8" else null
                                        return WebResourceResponse(
                                            loaded.mimeType,
                                            encoding,
                                            ByteArrayInputStream(loaded.bytes)
                                        )
                                    }
                                }
                                webViewRef = this
                                loadUrl(mainPageUrl)
                            }
                        },
                        update = { wv ->
                            wv.settings.textZoom = webTextZoom.coerceIn(75, 150)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun emptyNotFound(): WebResourceResponse =
    WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private fun buildFallbackIndexHtml(
    title: String,
    entryCount: Int,
    samples: List<ZimSearchHit>
): String {
    val links = samples.joinToString("\n") { hit ->
        val safeTitle = hit.title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        "<li><a href=\"/${hit.fullKey.trimStart('/')}\">$safeTitle</a></li>"
    }
    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width,initial-scale=1" />
          <title>$title</title>
          <style>
            body { font-family: sans-serif; padding: 16px; line-height: 1.5; background: #fff; color: #111; }
            h1 { font-size: 1.3rem; }
            ul { padding-left: 20px; }
            li { margin: 8px 0; }
            a { color: #0369a1; text-decoration: none; font-weight: 500; }
          </style>
        </head>
        <body>
          <h1>$title</h1>
          <p>Total entries indexed: $entryCount. Tap the Search icon above to look up any article.</p>
          <h3>Sample Articles</h3>
          <ul>$links</ul>
        </body>
        </html>
    """.trimIndent()
}
