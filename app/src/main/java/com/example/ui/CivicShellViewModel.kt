package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.bridge.DownloadSaveResult
import com.example.bridge.UpiLaunchResult
import com.example.bridge.WebFileChooserRequest
import com.example.data.SavedReceiptItem
import com.example.data.ShellPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveNativeSheet {
    NONE,
    SHELL_HUB,
    EMERGENCY_HELPLINES,
    SAVED_RECEIPTS,
    PORTAL_SETTINGS,
    MEDIA_PICKER
}

data class UpiFallbackState(
    val merchantVpa: String,
    val rawUri: String
)

data class CivicShellUiState(
    val liveUrl: String = BuildConfig.DEFAULT_LIVE_WEB_URL,
    val isDarkTheme: Boolean = false,
    val isOnline: Boolean = true,
    val loadProgress: Int = 0,
    val isInitialSplashVisible: Boolean = true,
    val showOfflineFallbackScreen: Boolean = false,
    val canGoBack: Boolean = false,
    val activeSheet: ActiveNativeSheet = ActiveNativeSheet.NONE,
    val savedReceipts: List<SavedReceiptItem> = emptyList(),
    val recentDownloadedReceipt: SavedReceiptItem? = null,
    val upiFallbackState: UpiFallbackState? = null,
    val pendingFileChooser: WebFileChooserRequest? = null,
    val urlValidationError: Boolean = false
)

sealed interface ShellOneShotEvent {
    data class ReloadUrl(val url: String, val bypassCache: Boolean = false) : ShellOneShotEvent
    data class SyncThemeToWeb(val isDark: Boolean) : ShellOneShotEvent
    data class ShowMessage(val message: String) : ShellOneShotEvent
}

class CivicShellViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShellPreferencesRepository(application.applicationContext)
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _uiState = MutableStateFlow(
        CivicShellUiState(isOnline = checkCurrentlyOnline())
    )
    val uiState: StateFlow<CivicShellUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ShellOneShotEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ShellOneShotEvent> = _events.asSharedFlow()

    private var wasOffline = !_uiState.value.isOnline

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onConnectivityChanged(true)
        }

        override fun onLost(network: Network) {
            onConnectivityChanged(checkCurrentlyOnline())
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            onConnectivityChanged(hasInternet)
        }
    }

    init {
        observePreferences()
        refreshSavedReceipts()
        registerNetworkMonitor()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.liveWebUrl.collect { url ->
                val previous = _uiState.value.liveUrl
                _uiState.update { it.copy(liveUrl = url) }
                if (previous != url) {
                    _events.tryEmit(ShellOneShotEvent.ReloadUrl(url, bypassCache = true))
                }
            }
        }

        viewModelScope.launch {
            repository.themePreference.collect { prefDark ->
                if (prefDark != null) {
                    _uiState.update { it.copy(isDarkTheme = prefDark) }
                }
            }
        }
    }

    private fun registerNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun checkCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun onConnectivityChanged(online: Boolean) {
        val recoveredFromOffline = online && (wasOffline || _uiState.value.showOfflineFallbackScreen)
        wasOffline = !online
        _uiState.update { state ->
            state.copy(
                isOnline = online,
                showOfflineFallbackScreen = if (online) false else state.showOfflineFallbackScreen
            )
        }
        if (recoveredFromOffline) {
            _events.tryEmit(ShellOneShotEvent.ShowMessage("Back online — syncing live CivicTriage portal…"))
            _events.tryEmit(ShellOneShotEvent.ReloadUrl(_uiState.value.liveUrl, bypassCache = true))
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update { it.copy(loadProgress = progress.coerceIn(0, 100)) }
    }

    fun onInitialContentVisible() {
        _uiState.update {
            it.copy(
                isInitialSplashVisible = false,
                showOfflineFallbackScreen = false
            )
        }
    }

    fun onCanGoBackChanged(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    fun onMainFrameOfflineError() {
        val online = checkCurrentlyOnline()
        _uiState.update {
            it.copy(
                isOnline = online,
                isInitialSplashVisible = false,
                showOfflineFallbackScreen = true
            )
        }
    }

    fun onThemeSyncedFromWeb(isDark: Boolean) {
        if (_uiState.value.isDarkTheme == isDark) return
        _uiState.update { it.copy(isDarkTheme = isDark) }
        viewModelScope.launch {
            repository.saveDarkTheme(isDark)
        }
    }

    fun toggleThemeFromNative(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        viewModelScope.launch {
            repository.saveDarkTheme(isDark)
        }
        _events.tryEmit(ShellOneShotEvent.SyncThemeToWeb(isDark))
    }

    fun requestLiveSync(bypassCache: Boolean = true) {
        val online = checkCurrentlyOnline()
        _uiState.update {
            it.copy(
                isOnline = online,
                showOfflineFallbackScreen = if (online) false else it.showOfflineFallbackScreen,
                activeSheet = ActiveNativeSheet.NONE
            )
        }
        if (online) {
            _events.tryEmit(ShellOneShotEvent.ReloadUrl(_uiState.value.liveUrl, bypassCache = bypassCache))
        } else {
            _events.tryEmit(ShellOneShotEvent.ShowMessage("Still offline — connect to internet to sync live updates."))
        }
    }

    fun openSheet(sheet: ActiveNativeSheet) {
        if (sheet == ActiveNativeSheet.SAVED_RECEIPTS) {
            refreshSavedReceipts()
        }
        _uiState.update {
            it.copy(
                activeSheet = sheet,
                urlValidationError = false
            )
        }
    }

    fun dismissSheet() {
        val pending = _uiState.value.pendingFileChooser
        if (_uiState.value.activeSheet == ActiveNativeSheet.MEDIA_PICKER && pending != null) {
            pending.callback.onReceiveValue(null)
        }
        _uiState.update {
            it.copy(
                activeSheet = ActiveNativeSheet.NONE,
                pendingFileChooser = if (it.activeSheet == ActiveNativeSheet.MEDIA_PICKER) null else it.pendingFileChooser,
                urlValidationError = false
            )
        }
    }

    fun onFileChooserRequested(request: WebFileChooserRequest) {
        // Cancel any stale pending callback before replacing
        _uiState.value.pendingFileChooser?.callback?.onReceiveValue(null)
        _uiState.update {
            it.copy(
                pendingFileChooser = request,
                activeSheet = ActiveNativeSheet.MEDIA_PICKER
            )
        }
    }

    fun consumePendingFileChooser(): WebFileChooserRequest? {
        val current = _uiState.value.pendingFileChooser
        _uiState.update {
            it.copy(
                pendingFileChooser = null,
                activeSheet = if (it.activeSheet == ActiveNativeSheet.MEDIA_PICKER) {
                    ActiveNativeSheet.NONE
                } else {
                    it.activeSheet
                }
            )
        }
        return current
    }

    fun onUpiLaunchResult(result: UpiLaunchResult) {
        when (result) {
            is UpiLaunchResult.Launched -> Unit
            is UpiLaunchResult.NoUpiAppFound -> {
                _uiState.update {
                    it.copy(
                        upiFallbackState = UpiFallbackState(
                            merchantVpa = result.merchantVpa,
                            rawUri = result.rawUri
                        )
                    )
                }
            }
            is UpiLaunchResult.InvalidUri -> {
                _events.tryEmit(ShellOneShotEvent.ShowMessage("Invalid UPI payment link."))
            }
        }
    }

    fun dismissUpiFallback() {
        _uiState.update { it.copy(upiFallbackState = null) }
    }

    fun onReceiptDownloaded(result: DownloadSaveResult) {
        if (result.success && result.receipt != null) {
            refreshSavedReceipts()
            _uiState.update { it.copy(recentDownloadedReceipt = result.receipt) }
        } else {
            _events.tryEmit(
                ShellOneShotEvent.ShowMessage(result.errorMessage ?: "Could not save receipt.")
            )
        }
    }

    fun dismissDownloadBanner() {
        _uiState.update { it.copy(recentDownloadedReceipt = null) }
    }

    fun refreshSavedReceipts() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = repository.listSavedReceipts()
            _uiState.update { it.copy(savedReceipts = items) }
        }
    }

    fun deleteSavedReceipt(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReceipt(fileName)
            val items = repository.listSavedReceipts()
            _uiState.update { it.copy(savedReceipts = items) }
        }
    }

    fun saveCustomLiveUrl(rawInput: String) {
        viewModelScope.launch {
            val saved = repository.saveLiveWebUrl(rawInput)
            if (saved) {
                _uiState.update {
                    it.copy(
                        activeSheet = ActiveNativeSheet.NONE,
                        urlValidationError = false
                    )
                }
                _events.tryEmit(ShellOneShotEvent.ShowMessage("Live portal URL updated — syncing now…"))
            } else {
                _uiState.update { it.copy(urlValidationError = true) }
            }
        }
    }

    fun resetDefaultLiveUrl() {
        viewModelScope.launch {
            repository.resetLiveWebUrl()
            _uiState.update {
                it.copy(
                    activeSheet = ActiveNativeSheet.NONE,
                    urlValidationError = false
                )
            }
            _events.tryEmit(ShellOneShotEvent.ShowMessage("Restored default CivicTriage LSG URL."))
        }
    }

    fun showMessage(message: String) {
        _events.tryEmit(ShellOneShotEvent.ShowMessage(message))
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        _uiState.value.pendingFileChooser?.callback?.onReceiveValue(null)
    }
}
