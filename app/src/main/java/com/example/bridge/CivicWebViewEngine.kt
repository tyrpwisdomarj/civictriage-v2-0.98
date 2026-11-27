package com.example.bridge

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

data class WebFileChooserRequest(
    val callback: ValueCallback<Array<Uri>>,
    val acceptTypes: List<String>,
    val allowMultiple: Boolean,
    val isCapturePreferred: Boolean
)

interface CivicWebViewCallbacks {
    fun getTrustedPortalUrl(): String
    fun isDarkTheme(): Boolean
    fun isNetworkOnline(): Boolean
    fun onProgressChanged(progress: Int)
    fun onInitialContentVisible()
    fun onCanGoBackChanged(canGoBack: Boolean)
    fun onMainFrameOfflineError(failingUrl: String?)
    fun onThemeSyncedFromWeb(isDark: Boolean)
    fun onUpiLaunchResult(result: UpiLaunchResult)
    fun onFileChooserRequested(request: WebFileChooserRequest)
    fun onWebPermissionRequested(request: PermissionRequest)
    fun onNativeSpeechStartRequested(lang: String)
    fun onNativeSpeechStopRequested()
    fun onReceiptDownloaded(result: DownloadSaveResult)
    fun onNotifyMessage(message: String)
    fun onPullToRefreshProgress(fraction: Float)
    fun onPullToRefreshTriggered()
}

private class CivicPortalWebView(
    context: Context,
    private val onPullProgress: (Float) -> Unit,
    private val onPullTriggered: () -> Unit
) : WebView(context) {

    private var trackingTopPull = false
    private var pullStartY = 0f
    private var pullStartX = 0f
    private var currentFraction = 0f

    private val topEdgeZonePx = 96f * resources.displayMetrics.density
    private val triggerDistancePx = 88f * resources.displayMetrics.density

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                trackingTopPull = scrollY == 0 && ev.y <= topEdgeZonePx
                pullStartY = ev.y
                pullStartX = ev.x
                currentFraction = 0f
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (trackingTopPull) {
                    val dy = ev.y - pullStartY
                    val dx = kotlin.math.abs(ev.x - pullStartX)
                    if (dy > 16f && dy > dx * 1.4f && scrollY == 0) {
                        currentFraction = (dy / triggerDistancePx).coerceIn(0f, 1.25f)
                        onPullProgress(currentFraction)
                    } else if (dy < 0f || dx > dy) {
                        trackingTopPull = false
                        if (currentFraction > 0f) {
                            currentFraction = 0f
                            onPullProgress(0f)
                        }
                    }
                }
            }

            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (trackingTopPull && currentFraction >= 1f && ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    onPullTriggered()
                }
                if (currentFraction > 0f) {
                    currentFraction = 0f
                    onPullProgress(0f)
                }
                trackingTopPull = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

object CivicWebViewEngine {

    private const val BRIDGE_NAME = "CivicNativeShell"

    @SuppressLint("SetJavaScriptEnabled")
    fun createConfiguredWebView(
        context: Context,
        callbacks: CivicWebViewCallbacks
    ): WebView {
        val webView = CivicPortalWebView(
            context = context,
            onPullProgress = { fraction -> callbacks.onPullToRefreshProgress(fraction) },
            onPullTriggered = { callbacks.onPullToRefreshTriggered() }
        ).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(
                if (callbacks.isDarkTheme()) Color.parseColor("#0A0D14")
                else Color.parseColor("#F5F4F0")
            )
            // Enable GPU hardware acceleration for 60fps Three.js 3D Earth, Relief Maps & Cabins
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.textZoom = 100
        // Hardened WebView file/mixed-content posture
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = if (callbacks.isNetworkOnline()) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        settings.userAgentString = "${settings.userAgentString} CivicTriageLSGMobile/1.0"

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        val mainHandler = Handler(Looper.getMainLooper())
        webView.addJavascriptInterface(
            CivicJsBridge(
                webView = webView,
                mainHandler = mainHandler,
                callbacks = callbacks
            ),
            BRIDGE_NAME
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url ?: return false
                val scheme = uri.scheme?.lowercase().orEmpty()

                // 1. Intercept UPI payment deep links across main or sub frames
                if (NativeIntentHandler.isUpiUri(uri)) {
                    val result = NativeIntentHandler.launchUpiIntent(context, uri)
                    callbacks.onUpiLaunchResult(result)
                    return true
                }

                // 2. Intercept emergency & hospital telephone dialer links
                if (scheme == "tel") {
                    val launched = NativeIntentHandler.launchEmergencyDialer(context, uri.toString())
                    if (!launched) {
                        callbacks.onNotifyMessage("Unable to open phone dialer on this device.")
                    }
                    return true
                }

                // 3. Intercept mailto links cleanly
                if (scheme == "mailto") {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, uri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                    return true
                }

                // 4. Allow embedded iframes (Google Maps embeds, etc.) to render seamlessly in-app
                if (!request.isForMainFrame) {
                    return scheme != "https" && scheme != "http"
                }

                // 5. Block unsafe top-level schemes
                if (scheme != "https" && scheme != "http") {
                    return true
                }

                // 6. Keep trusted live portal origin inside the native shell
                if (isSamePortalHost(uri, callbacks.getTrustedPortalUrl())) {
                    return false
                }

                // 7. Open external links (Thuna Police, DWMS Knowledge Mission, external Maps) in Chrome Custom Tabs
                NativeIntentHandler.openInCustomTab(
                    context = context,
                    url = uri.toString(),
                    isDarkTheme = callbacks.isDarkTheme()
                )
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                callbacks.onCanGoBackChanged(view.canGoBack())
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                if (isTrustedUrlString(url, callbacks.getTrustedPortalUrl())) {
                    view.evaluateJavascript(SHELL_BRIDGE_JS, null)
                }
                callbacks.onInitialContentVisible()
                callbacks.onCanGoBackChanged(view.canGoBack())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (isTrustedUrlString(url, callbacks.getTrustedPortalUrl())) {
                    view.evaluateJavascript(SHELL_BRIDGE_JS, null)
                }
                callbacks.onInitialContentVisible()
                callbacks.onCanGoBackChanged(view.canGoBack())
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    callbacks.onMainFrameOfflineError(request.url?.toString())
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChanged(newProgress)
                if (newProgress >= 85) {
                    callbacks.onInitialContentVisible()
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) return false
                val acceptTypes = fileChooserParams?.acceptTypes
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                val allowMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val capturePreferred = fileChooserParams?.isCaptureEnabled == true
                callbacks.onFileChooserRequested(
                    WebFileChooserRequest(
                        callback = filePathCallback,
                        acceptTypes = acceptTypes,
                        allowMultiple = allowMultiple,
                        isCapturePreferred = capturePreferred
                    )
                )
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val origin = request.origin
                if (!isSamePortalHost(origin, callbacks.getTrustedPortalUrl())) {
                    request.deny()
                    return
                }
                callbacks.onWebPermissionRequested(request)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (view == null || resultMsg == null) return false
                val tempWebView = WebView(context)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        subView: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val targetUri = request.url ?: return true
                        handleExternalOrDeepLink(context, targetUri, callbacks)
                        subView.destroy()
                        return true
                    }

                    override fun onPageStarted(subView: WebView, url: String?, favicon: Bitmap?) {
                        if (!url.isNullOrBlank()) {
                            handleExternalOrDeepLink(context, Uri.parse(url), callbacks)
                        }
                        subView.destroy()
                    }
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = tempWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.isNullOrBlank()) return@DownloadListener
            val suggestedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            when {
                url.startsWith("blob:") -> {
                    val safeUrlJson = JSONObject.quote(url)
                    val safeNameJson = JSONObject.quote(suggestedName)
                    val safeMimeJson = JSONObject.quote(mimeType ?: "text/plain")
                    webView.evaluateJavascript(
                        "window.__civicFetchBlobForNative && window.__civicFetchBlobForNative($safeUrlJson, $safeNameJson, $safeMimeJson);",
                        null
                    )
                }

                url.startsWith("data:") -> {
                    val result = NativeIntentHandler.saveDownloadedPayload(
                        context = context,
                        rawFileName = suggestedName,
                        rawMimeType = mimeType ?: "text/plain",
                        base64Payload = url
                    )
                    callbacks.onReceiptDownloaded(result)
                }

                url.startsWith("https://") -> {
                    enqueueSystemDownload(context, url, userAgent, suggestedName, mimeType, callbacks)
                }
            }
        })

        return webView
    }

    fun applyThemeToWebPortal(webView: WebView, isDark: Boolean, trustedPortalUrl: String) {
        if (!isTrustedUrlString(webView.url, trustedPortalUrl)) return
        val mode = if (isDark) "dark" else "light"
        val script = """
            (function() {
              try {
                localStorage.setItem('civictriage_theme_mode_v1', '$mode');
                if ('$mode' === 'dark') {
                  document.documentElement.classList.add('dark');
                } else {
                  document.documentElement.classList.remove('dark');
                }
                window.dispatchEvent(new StorageEvent('storage', { key: 'civictriage_theme_mode_v1', newValue: '$mode' }));
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun updateCacheMode(webView: WebView, isOnline: Boolean) {
        webView.settings.cacheMode = if (isOnline) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
    }

    private fun handleExternalOrDeepLink(
        context: Context,
        uri: Uri,
        callbacks: CivicWebViewCallbacks
    ) {
        val scheme = uri.scheme?.lowercase().orEmpty()
        when {
            NativeIntentHandler.isUpiUri(uri) -> {
                callbacks.onUpiLaunchResult(NativeIntentHandler.launchUpiIntent(context, uri))
            }

            scheme == "tel" -> {
                NativeIntentHandler.launchEmergencyDialer(context, uri.toString())
            }

            scheme == "https" || scheme == "http" -> {
                NativeIntentHandler.openInCustomTab(
                    context = context,
                    url = uri.toString(),
                    isDarkTheme = callbacks.isDarkTheme()
                )
            }
        }
    }

    private fun enqueueSystemDownload(
        context: Context,
        url: String,
        userAgent: String?,
        fileName: String,
        mimeType: String?,
        callbacks: CivicWebViewCallbacks
    ) {
        runCatching {
            val safeName = NativeIntentHandler.sanitizeDownloadFilename(fileName, mimeType.orEmpty())
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType ?: "application/octet-stream")
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setTitle(safeName)
                setDescription("Downloading from CivicTriage LSG")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
            callbacks.onNotifyMessage("Downloading $safeName to Downloads…")
        }.onFailure {
            callbacks.onNotifyMessage("Could not start download.")
        }
    }

    fun isSamePortalHost(uri: Uri?, trustedPortalUrl: String): Boolean {
        if (uri == null) return false
        val trustedHost = Uri.parse(trustedPortalUrl).host?.lowercase() ?: return false
        val currentHost = uri.host?.lowercase() ?: return false
        return currentHost == trustedHost
    }

    fun isTrustedUrlString(url: String?, trustedPortalUrl: String): Boolean {
        if (url.isNullOrBlank()) return false
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "https") return false
        return isSamePortalHost(parsed, trustedPortalUrl)
    }

    private class CivicJsBridge(
        private val webView: WebView,
        private val mainHandler: Handler,
        private val callbacks: CivicWebViewCallbacks
    ) {
        private fun verifyTrustedOriginOnMain(action: () -> Unit) {
            mainHandler.post {
                if (isTrustedUrlString(webView.url, callbacks.getTrustedPortalUrl())) {
                    action()
                }
            }
        }

        @JavascriptInterface
        fun onThemeChanged(isDark: Boolean) {
            verifyTrustedOriginOnMain {
                callbacks.onThemeSyncedFromWeb(isDark)
            }
        }

        @JavascriptInterface
        fun saveDownloadedFile(fileName: String, mimeType: String, base64Data: String) {
            verifyTrustedOriginOnMain {
                val context = webView.context.applicationContext
                Thread {
                    val result = NativeIntentHandler.saveDownloadedPayload(
                        context = context,
                        rawFileName = fileName,
                        rawMimeType = mimeType,
                        base64Payload = base64Data
                    )
                    mainHandler.post {
                        callbacks.onReceiptDownloaded(result)
                    }
                }.start()
            }
        }

        @JavascriptInterface
        fun startNativeSpeech(lang: String) {
            verifyTrustedOriginOnMain {
                callbacks.onNativeSpeechStartRequested(lang)
            }
        }

        @JavascriptInterface
        fun stopNativeSpeech() {
            verifyTrustedOriginOnMain {
                callbacks.onNativeSpeechStopRequested()
            }
        }
    }

    private val SHELL_BRIDGE_JS = """
        (function() {
          if (window.__civicNativeShellInjected) return;
          window.__civicNativeShellInjected = true;

          // 1. Sync Light/Dark Liquid Glass Theme (civictriage_theme_mode_v1 & html.dark)
          function reportTheme() {
            try {
              var stored = localStorage.getItem('civictriage_theme_mode_v1');
              var isDark = document.documentElement.classList.contains('dark') || stored === 'dark';
              if (window.CivicNativeShell && window.CivicNativeShell.onThemeChanged) {
                window.CivicNativeShell.onThemeChanged(!!isDark);
              }
            } catch (e) {}
          }
          reportTheme();
          try {
            var observer = new MutationObserver(function() { reportTheme(); });
            observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
            window.addEventListener('storage', function(ev) {
              if (!ev || ev.key === 'civictriage_theme_mode_v1') reportTheme();
            });
          } catch (e) {}

          // 2. Native Blob & Receipt Downloader (.txt OP Slips, Admission/Job/E-Ticket Receipts)
          window.__civicFetchBlobForNative = function(blobUrl, fileName, mimeType) {
            try {
              fetch(blobUrl)
                .then(function(res) { return res.blob(); })
                .then(function(blob) {
                  var reader = new FileReader();
                  reader.onloadend = function() {
                    if (typeof reader.result === 'string' && window.CivicNativeShell) {
                      window.CivicNativeShell.saveDownloadedFile(
                        fileName || 'CivicTriage_Receipt.txt',
                        mimeType || blob.type || 'text/plain',
                        reader.result
                      );
                    }
                  };
                  reader.readAsDataURL(blob);
                })
                .catch(function() {});
            } catch (e) {}
          };

          document.addEventListener('click', function(e) {
            try {
              var anchor = e.target && e.target.closest ? e.target.closest('a[download]') : null;
              if (!anchor) return;
              var href = anchor.getAttribute('href') || '';
              var downloadName = anchor.getAttribute('download') || 'CivicTriage_Receipt.txt';
              if (href.indexOf('blob:') === 0) {
                e.preventDefault();
                window.__civicFetchBlobForNative(href, downloadName, 'text/plain');
              } else if (href.indexOf('data:') === 0 && window.CivicNativeShell) {
                e.preventDefault();
                window.CivicNativeShell.saveDownloadedFile(downloadName, 'text/plain', href);
              }
            } catch (err) {}
          }, true);

          // 3. Multilingual Native SpeechRecognition Fallback (en-IN & ml-IN)
          if (!window.SpeechRecognition && !window.webkitSpeechRecognition && window.CivicNativeShell) {
            var activeRecognition = null;
            window.__civicNativeSpeechDispatch = function(ev) {
              if (!activeRecognition || !ev) return;
              if (ev.type === 'start' && activeRecognition.onstart) {
                activeRecognition.onstart();
              } else if (ev.type === 'result' && activeRecognition.onresult) {
                var alt = { transcript: ev.payload || '', confidence: 0.95 };
                var resItem = [alt];
                resItem.isFinal = !!ev.isFinal;
                var fakeEvent = { resultIndex: 0, results: [resItem] };
                activeRecognition.onresult(fakeEvent);
              } else if (ev.type === 'error' && activeRecognition.onerror) {
                activeRecognition.onerror({ error: ev.payload || 'aborted' });
              } else if (ev.type === 'end' && activeRecognition.onend) {
                activeRecognition.onend();
              }
            };

            function CivicNativeSpeechRecognition() {
              this.lang = 'en-IN';
              this.continuous = false;
              this.interimResults = true;
              this.onstart = null;
              this.onresult = null;
              this.onerror = null;
              this.onend = null;
            }
            CivicNativeSpeechRecognition.prototype.start = function() {
              activeRecognition = this;
              window.CivicNativeShell.startNativeSpeech(this.lang || 'en-IN');
            };
            CivicNativeSpeechRecognition.prototype.stop = function() {
              window.CivicNativeShell.stopNativeSpeech();
            };
            CivicNativeSpeechRecognition.prototype.abort = function() {
              window.CivicNativeShell.stopNativeSpeech();
            };
            window.SpeechRecognition = CivicNativeSpeechRecognition;
            window.webkitSpeechRecognition = CivicNativeSpeechRecognition;
          }
        })();
    """.trimIndent()
}
