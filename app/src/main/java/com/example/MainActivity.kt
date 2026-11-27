package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bridge.CivicWebViewCallbacks
import com.example.bridge.CivicWebViewEngine
import com.example.bridge.DownloadSaveResult
import com.example.bridge.NativeIntentHandler
import com.example.bridge.NativeSpeechController
import com.example.bridge.UpiLaunchResult
import com.example.bridge.WebFileChooserRequest
import com.example.ui.ActiveNativeSheet
import com.example.ui.CivicShellViewModel
import com.example.ui.ShellOneShotEvent
import com.example.ui.components.DownloadSuccessBanner
import com.example.ui.components.EmergencyHelplinesBottomSheet
import com.example.ui.components.LiquidSplashOverlay
import com.example.ui.components.MediaPickerBottomSheet
import com.example.ui.components.OfflineConnectivityBanner
import com.example.ui.components.OfflineFallbackScreen
import com.example.ui.components.PortalSettingsBottomSheet
import com.example.ui.components.SavedReceiptsBottomSheet
import com.example.ui.components.ShellHubBottomSheet
import com.example.ui.components.UpiFallbackDialog
import com.example.ui.theme.CivicCyan
import com.example.ui.theme.CivicTriageTheme
import com.example.ui.theme.LocalCivicGlass
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val shellViewModel: CivicShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by shellViewModel.uiState.collectAsStateWithLifecycle()

            // Dynamically sync native status & navigation bar appearance with Liquid Glass Light/Dark mode
            LaunchedEffect(uiState.isDarkTheme) {
                if (uiState.isDarkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT
                        )
                    )
                }
            }

            CivicTriageTheme(darkTheme = uiState.isDarkTheme) {
                CivicTriageShellScreen(
                    viewModel = shellViewModel,
                    onExitApp = { finish() }
                )
            }
        }
    }
}

@Composable
private fun CivicTriageShellScreen(
    viewModel: CivicShellViewModel,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val glass = LocalCivicGlass.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    var pullToRefreshFraction by remember { mutableStateOf(0f) }

    // Pending runtime callbacks
    var activeFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingCameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingSpeechLang by remember { mutableStateOf<String?>(null) }

    val speechController = remember(context) {
        NativeSpeechController(
            context = context.applicationContext,
            onDispatchJsEvent = { script ->
                webViewRef?.post {
                    val wv = webViewRef ?: return@post
                    if (CivicWebViewEngine.isTrustedUrlString(wv.url, viewModel.uiState.value.liveUrl)) {
                        wv.evaluateJavascript(script, null)
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            speechController.destroy()
            activeFileCallback?.onReceiveValue(null)
            activeFileCallback = null
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    // 1. Zero-permission Android Photo Picker (single & multiple) — Google Play Policy compliant
    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val cb = activeFileCallback
        activeFileCallback = null
        cb?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val cb = activeFileCallback
        activeFileCallback = null
        cb?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
    }

    // 2. Zero-permission Storage Access Framework document/PDF picker
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val cb = activeFileCallback
        activeFileCallback = null
        cb?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }

    // 3. Camera Capture via FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val cb = activeFileCallback
        val uri = pendingCameraCaptureUri
        activeFileCallback = null
        pendingCameraCaptureUri = null
        cb?.onReceiveValue(if (success && uri != null) arrayOf(uri) else null)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = NativeIntentHandler.createCameraCaptureUri(context)
            pendingCameraCaptureUri = uri
            takePictureLauncher.launch(uri)
        } else {
            activeFileCallback?.onReceiveValue(null)
            activeFileCallback = null
            viewModel.showMessage(context.getString(R.string.permission_camera_denied))
        }
    }

    // 4. WebRTC / Microphone & Camera Runtime Permissions
    val webPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val req = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (req != null) {
            val grantedResources = mutableListOf<String>()
            req.resources.forEach { res ->
                when (res) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                        if (results[Manifest.permission.RECORD_AUDIO] == true) {
                            grantedResources.add(res)
                        }
                    }

                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                        if (results[Manifest.permission.CAMERA] == true) {
                            grantedResources.add(res)
                        }
                    }
                }
            }
            if (grantedResources.isNotEmpty()) {
                req.grant(grantedResources.toTypedArray())
            } else {
                req.deny()
                viewModel.showMessage(context.getString(R.string.permission_mic_denied))
            }
        }
    }

    // 5. Native SpeechRecognizer Microphone Permission (en-IN / ml-IN fallback)
    val speechPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val lang = pendingSpeechLang
        pendingSpeechLang = null
        if (granted && lang != null) {
            speechController.startListening(lang)
        } else {
            viewModel.showMessage(context.getString(R.string.permission_mic_denied))
        }
    }

    // Helper to launch Camera capture safely
    fun launchCameraForPendingChooser() {
        val request = viewModel.consumePendingFileChooser() ?: return
        activeFileCallback = request.callback
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            val uri = NativeIntentHandler.createCameraCaptureUri(context)
            pendingCameraCaptureUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchPhotoLibraryForPendingChooser() {
        val request = viewModel.consumePendingFileChooser() ?: return
        activeFileCallback = request.callback
        val visualRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (request.allowMultiple) {
            multiPhotoPicker.launch(visualRequest)
        } else {
            singlePhotoPicker.launch(visualRequest)
        }
    }

    fun launchDocumentForPendingChooser() {
        val request = viewModel.consumePendingFileChooser() ?: return
        activeFileCallback = request.callback
        documentPicker.launch(arrayOf("application/pdf", "image/*", "text/plain"))
    }

    // Sync WebView cache mode when connectivity changes
    LaunchedEffect(uiState.isOnline, webViewRef) {
        webViewRef?.let { wv ->
            CivicWebViewEngine.updateCacheMode(wv, uiState.isOnline)
        }
    }

    // Sync WebView background color when theme changes
    LaunchedEffect(uiState.isDarkTheme, webViewRef) {
        webViewRef?.setBackgroundColor(
            if (uiState.isDarkTheme) AndroidColor.parseColor("#0A0D14")
            else AndroidColor.parseColor("#F5F4F0")
        )
    }

    // Handle one-shot events (Live Reload, Theme Push to Web, Snackbars)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShellOneShotEvent.ReloadUrl -> {
                    webViewRef?.let { wv ->
                        CivicWebViewEngine.updateCacheMode(wv, viewModel.uiState.value.isOnline)
                        if (event.bypassCache) {
                            wv.clearCache(false)
                        }
                        if (wv.url.isNullOrBlank() || !CivicWebViewEngine.isTrustedUrlString(wv.url, event.url)) {
                            wv.loadUrl(event.url)
                        } else {
                            wv.reload()
                        }
                    }
                }

                is ShellOneShotEvent.SyncThemeToWeb -> {
                    webViewRef?.let { wv ->
                        CivicWebViewEngine.applyThemeToWebPortal(
                            webView = wv,
                            isDark = event.isDark,
                            trustedPortalUrl = viewModel.uiState.value.liveUrl
                        )
                    }
                }

                is ShellOneShotEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // Auto-dismiss splash overlay after generous timeout so slow networks never trap the user
    LaunchedEffect(uiState.isInitialSplashVisible) {
        if (uiState.isInitialSplashVisible) {
            delay(5500L)
            viewModel.onInitialContentVisible()
        }
    }

    // Auto-dismiss download success banner after 8 seconds
    LaunchedEffect(uiState.recentDownloadedReceipt) {
        if (uiState.recentDownloadedReceipt != null) {
            delay(8000L)
            viewModel.dismissDownloadBanner()
        }
    }

    // Hardware Back Button Navigation (closes sheets -> WebView history -> double-back exit)
    BackHandler(enabled = true) {
        when {
            uiState.upiFallbackState != null -> viewModel.dismissUpiFallback()
            uiState.activeSheet != ActiveNativeSheet.NONE -> viewModel.dismissSheet()
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 2000L) {
                    onExitApp()
                } else {
                    lastBackPressMs = now
                    viewModel.showMessage(context.getString(R.string.back_exit_hint))
                }
            }
        }
    }

    val webCallbacks = remember(viewModel, context) {
        object : CivicWebViewCallbacks {
            override fun getTrustedPortalUrl(): String = viewModel.uiState.value.liveUrl
            override fun isDarkTheme(): Boolean = viewModel.uiState.value.isDarkTheme
            override fun isNetworkOnline(): Boolean = viewModel.uiState.value.isOnline
            override fun onProgressChanged(progress: Int) = viewModel.onProgressChanged(progress)
            override fun onInitialContentVisible() = viewModel.onInitialContentVisible()
            override fun onCanGoBackChanged(canGoBack: Boolean) = viewModel.onCanGoBackChanged(canGoBack)
            override fun onMainFrameOfflineError(failingUrl: String?) = viewModel.onMainFrameOfflineError()
            override fun onThemeSyncedFromWeb(isDark: Boolean) = viewModel.onThemeSyncedFromWeb(isDark)
            override fun onUpiLaunchResult(result: UpiLaunchResult) = viewModel.onUpiLaunchResult(result)

            override fun onFileChooserRequested(request: WebFileChooserRequest) {
                viewModel.onFileChooserRequested(request)
            }

            override fun onWebPermissionRequested(request: PermissionRequest) {
                val needed = mutableListOf<String>()
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(Manifest.permission.RECORD_AUDIO)
                }
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(Manifest.permission.CAMERA)
                }
                if (needed.isEmpty()) {
                    request.grant(request.resources)
                } else {
                    pendingWebPermissionRequest = request
                    webPermissionsLauncher.launch(needed.toTypedArray())
                }
            }

            override fun onNativeSpeechStartRequested(lang: String) {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    speechController.startListening(lang)
                } else {
                    pendingSpeechLang = lang
                    speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            override fun onNativeSpeechStopRequested() {
                speechController.stopListening()
            }

            override fun onReceiptDownloaded(result: DownloadSaveResult) {
                viewModel.onReceiptDownloaded(result)
            }

            override fun onNotifyMessage(message: String) {
                viewModel.showMessage(message)
            }

            override fun onPullToRefreshProgress(fraction: Float) {
                pullToRefreshFraction = fraction
            }

            override fun onPullToRefreshTriggered() {
                pullToRefreshFraction = 0f
                viewModel.requestLiveSync(bypassCache = true)
            }
        }
    }

    val canvasBgColor = if (uiState.isDarkTheme) Color(0xFF0A0D14) else Color(0xFFF5F4F0)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBgColor),
        containerColor = canvasBgColor,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasBgColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Primary Full-Screen Hardware-Accelerated Live WebView Shell
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("civictriage_live_webview"),
                factory = { ctx ->
                    CivicWebViewEngine.createConfiguredWebView(ctx, webCallbacks).also { wv ->
                        webViewRef = wv
                        wv.loadUrl(uiState.liveUrl)
                    }
                },
                update = { _ -> }
            )

            // Thin top Liquid Glass loading shimmer bar during navigation / live sync
            AnimatedVisibility(
                visible = !uiState.isInitialSplashVisible && uiState.loadProgress in 1..99 && !uiState.showOfflineFallbackScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(uiState.loadProgress / 100f)
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(glass.accentPill, CivicCyan)
                                )
                            )
                    )
                }
            }

            // Liquid Glass Pull-to-Refresh Pill Indicator (top-edge drag)
            AnimatedVisibility(
                visible = pullToRefreshFraction > 0.12f && !uiState.isInitialSplashVisible && !uiState.showOfflineFallbackScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (12.dp + (pullToRefreshFraction * 28f).dp))
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (uiState.isDarkTheme) Color(0xE8121724) else Color(0xECFFFFFF),
                    border = BorderStroke(1.dp, glass.glassBorder),
                    shadowElevation = 8.dp
                ) {
                    androidx.compose.material3.Text(
                        text = if (pullToRefreshFraction >= 1f) "Release to sync latest push" else "Pull to sync live portal…",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = glass.accentPill,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Non-intrusive Offline Connectivity Indicator Banner when viewing cached PWA offline
            OfflineConnectivityBanner(
                visible = !uiState.isOnline && !uiState.showOfflineFallbackScreen && !uiState.isInitialSplashVisible,
                onRetry = { viewModel.requestLiveSync(bypassCache = true) },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Full-Screen Liquid Glass Offline Fallback Portal when no cache is available
            if (uiState.showOfflineFallbackScreen) {
                OfflineFallbackScreen(
                    savedReceiptCount = uiState.savedReceipts.size,
                    onRetryConnection = { viewModel.requestLiveSync(bypassCache = true) },
                    onDialEmergency = { number ->
                        NativeIntentHandler.launchEmergencyDialer(context, number)
                    },
                    onOpenReceipts = { viewModel.openSheet(ActiveNativeSheet.SAVED_RECEIPTS) },
                    onOpenSettings = { viewModel.openSheet(ActiveNativeSheet.PORTAL_SETTINGS) }
                )
            }

            // Subtle, unobtrusive floating Liquid Glass Native Hub trigger (Sync, Helplines, Receipts, URL)
            if (!uiState.isInitialSplashVisible && !uiState.showOfflineFallbackScreen) {
                Surface(
                    onClick = { viewModel.openSheet(ActiveNativeSheet.SHELL_HUB) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 14.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("native_shell_hub_button"),
                    shape = CircleShape,
                    color = if (uiState.isDarkTheme) Color(0xD6121724) else Color(0xD9FFFFFF),
                    border = BorderStroke(1.dp, glass.glassBorder),
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.cd_shell_controls),
                            tint = glass.accentPill,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            // Receipt Downloaded Banner with instant Open & Share Sheet triggers
            DownloadSuccessBanner(
                receipt = uiState.recentDownloadedReceipt,
                onOpen = { receipt ->
                    viewModel.dismissDownloadBanner()
                    NativeIntentHandler.openReceipt(context, receipt)
                },
                onShare = { receipt ->
                    viewModel.dismissDownloadBanner()
                    NativeIntentHandler.shareReceipt(context, receipt)
                },
                onDismiss = { viewModel.dismissDownloadBanner() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Liquid Glass Launch Splash Overlay
            LiquidSplashOverlay(
                visible = uiState.isInitialSplashVisible,
                loadProgress = uiState.loadProgress
            )
        }
    }

    // Native Liquid Glass Bottom Sheets
    when (uiState.activeSheet) {
        ActiveNativeSheet.NONE -> Unit

        ActiveNativeSheet.SHELL_HUB -> {
            ShellHubBottomSheet(
                liveUrl = uiState.liveUrl,
                isDarkTheme = uiState.isDarkTheme,
                isOnline = uiState.isOnline,
                savedReceiptsCount = uiState.savedReceipts.size,
                onToggleTheme = { isDark -> viewModel.toggleThemeFromNative(isDark) },
                onSyncLatest = { viewModel.requestLiveSync(bypassCache = true) },
                onOpenEmergency = { viewModel.openSheet(ActiveNativeSheet.EMERGENCY_HELPLINES) },
                onOpenReceipts = { viewModel.openSheet(ActiveNativeSheet.SAVED_RECEIPTS) },
                onOpenPortalSettings = { viewModel.openSheet(ActiveNativeSheet.PORTAL_SETTINGS) },
                onDismiss = { viewModel.dismissSheet() }
            )
        }

        ActiveNativeSheet.MEDIA_PICKER -> {
            val accepts = uiState.pendingFileChooser?.acceptTypes.orEmpty()
            val allowsDocs = accepts.isEmpty() || accepts.any {
                it.contains("pdf", ignoreCase = true) || it == "*/*" || !it.startsWith("image/")
            }
            MediaPickerBottomSheet(
                allowDocuments = allowsDocs,
                onPickCamera = { launchCameraForPendingChooser() },
                onPickPhotoLibrary = { launchPhotoLibraryForPendingChooser() },
                onPickDocument = { launchDocumentForPendingChooser() },
                onDismiss = { viewModel.dismissSheet() }
            )
        }

        ActiveNativeSheet.EMERGENCY_HELPLINES -> {
            EmergencyHelplinesBottomSheet(
                onDial = { number ->
                    viewModel.dismissSheet()
                    NativeIntentHandler.launchEmergencyDialer(context, number)
                },
                onDismiss = { viewModel.dismissSheet() }
            )
        }

        ActiveNativeSheet.SAVED_RECEIPTS -> {
            SavedReceiptsBottomSheet(
                receipts = uiState.savedReceipts,
                onOpenReceipt = { receipt -> NativeIntentHandler.openReceipt(context, receipt) },
                onShareReceipt = { receipt -> NativeIntentHandler.shareReceipt(context, receipt) },
                onDeleteReceipt = { name -> viewModel.deleteSavedReceipt(name) },
                onDismiss = { viewModel.dismissSheet() }
            )
        }

        ActiveNativeSheet.PORTAL_SETTINGS -> {
            PortalSettingsBottomSheet(
                currentUrl = uiState.liveUrl,
                hasValidationError = uiState.urlValidationError,
                onSaveUrl = { draft -> viewModel.saveCustomLiveUrl(draft) },
                onResetDefault = { viewModel.resetDefaultLiveUrl() },
                onDismiss = { viewModel.dismissSheet() }
            )
        }
    }

    // UPI Graceful Fallback Dialog (when no UPI app is installed on device)
    uiState.upiFallbackState?.let { fallback ->
        UpiFallbackDialog(
            state = fallback,
            onCopyVpa = { vpa ->
                NativeIntentHandler.copyToClipboard(context, "CivicTriage UPI ID", vpa)
                viewModel.dismissUpiFallback()
                viewModel.showMessage("Copied UPI ID: $vpa")
            },
            onDismiss = { viewModel.dismissUpiFallback() }
        )
    }
}
