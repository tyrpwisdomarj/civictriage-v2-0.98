package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.shellDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "civictriage_shell_prefs"
)

data class SavedReceiptItem(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String
)

class ShellPreferencesRepository(private val context: Context) {

    private object Keys {
        val LIVE_WEB_URL = stringPreferencesKey("live_web_url")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val HAS_EXPLICIT_THEME = booleanPreferencesKey("has_explicit_theme")
    }

    val liveWebUrl: Flow<String> = context.shellDataStore.data.map { prefs ->
        val stored = prefs[Keys.LIVE_WEB_URL]
        if (!stored.isNullOrBlank() && isValidLivePortalUrl(stored)) {
            stored
        } else {
            BuildConfig.DEFAULT_LIVE_WEB_URL
        }
    }

    val themePreference: Flow<Boolean?> = context.shellDataStore.data.map { prefs ->
        if (prefs[Keys.HAS_EXPLICIT_THEME] == true) {
            prefs[Keys.IS_DARK_THEME] ?: false
        } else {
            null
        }
    }

    suspend fun saveLiveWebUrl(rawUrl: String): Boolean {
        val normalized = normalizePortalUrl(rawUrl) ?: return false
        context.shellDataStore.edit { prefs ->
            prefs[Keys.LIVE_WEB_URL] = normalized
        }
        return true
    }

    suspend fun resetLiveWebUrl() {
        context.shellDataStore.edit { prefs ->
            prefs[Keys.LIVE_WEB_URL] = BuildConfig.DEFAULT_LIVE_WEB_URL
        }
    }

    suspend fun saveDarkTheme(isDark: Boolean) {
        context.shellDataStore.edit { prefs ->
            prefs[Keys.HAS_EXPLICIT_THEME] = true
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    fun getReceiptsDirectory(): File {
        val dir = File(context.filesDir, "receipts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun listSavedReceipts(): List<SavedReceiptItem> {
        val dir = getReceiptsDirectory()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                SavedReceiptItem(
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModifiedMillis = file.lastModified(),
                    mimeType = guessMimeType(file.name)
                )
            }
            .orEmpty()
    }

    fun deleteReceipt(fileName: String): Boolean {
        val dir = getReceiptsDirectory().canonicalFile
        val candidate = File(dir, fileName).canonicalFile
        if (!candidate.path.startsWith(dir.path + File.separator)) return false
        return candidate.exists() && candidate.delete()
    }

    companion object {
        fun normalizePortalUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > 512) return null
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host.isBlank() || !host.contains('.')) return null
            if (isBlockedPrivateHost(host)) return null
            return trimmed.trimEnd('/')
        }

        fun isValidLivePortalUrl(raw: String): Boolean = normalizePortalUrl(raw) != null

        private fun isBlockedPrivateHost(host: String): Boolean {
            if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "169.254.169.254") {
                return true
            }
            if (host.startsWith("10.") || host.startsWith("192.168.")) return true
            if (host.startsWith("172.")) {
                val secondOctet = host.split('.').getOrNull(1)?.toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) return true
            }
            return false
        }

        fun guessMimeType(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "txt" -> "text/plain"
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "json" -> "application/json"
                "csv" -> "text/csv"
                else -> "application/octet-stream"
            }
        }
    }
}
