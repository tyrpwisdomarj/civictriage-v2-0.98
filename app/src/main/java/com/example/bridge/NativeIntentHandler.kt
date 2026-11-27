package com.example.bridge

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import com.example.data.SavedReceiptItem
import com.example.data.ShellPreferencesRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UpiLaunchResult {
    data object Launched : UpiLaunchResult
    data class NoUpiAppFound(val merchantVpa: String, val rawUri: String) : UpiLaunchResult
    data class InvalidUri(val reason: String) : UpiLaunchResult
}

data class DownloadSaveResult(
    val success: Boolean,
    val receipt: SavedReceiptItem? = null,
    val errorMessage: String? = null
)

object NativeIntentHandler {

    private val ALLOWED_UPI_SCHEMES = setOf("upi", "tez", "phonepe", "paytmmp", "bhim", "credpay")
    private val SAFE_PHONE_REGEX = Regex("^[0-9+*#,;\\-\\s]{2,24}$")
    private val SAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9._-]")
    private const val DEFAULT_MERCHANT_VPA = "7012692128@fam"

    fun isUpiUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in ALLOWED_UPI_SCHEMES
    }

    fun launchUpiIntent(context: Context, uri: Uri): UpiLaunchResult {
        if (!isUpiUri(uri)) {
            return UpiLaunchResult.InvalidUri("Unsupported payment URI scheme")
        }
        val vpa = uri.getQueryParameter("pa")?.takeIf { it.isNotBlank() } ?: DEFAULT_MERCHANT_VPA
        val paymentIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            val chooser = Intent.createChooser(paymentIntent, "Pay with UPI App").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val resolveInfo = context.packageManager.queryIntentActivities(paymentIntent, 0)
            if (resolveInfo.isEmpty()) {
                UpiLaunchResult.NoUpiAppFound(merchantVpa = vpa, rawUri = uri.toString())
            } else {
                context.startActivity(chooser)
                UpiLaunchResult.Launched
            }
        } catch (_: ActivityNotFoundException) {
            UpiLaunchResult.NoUpiAppFound(merchantVpa = vpa, rawUri = uri.toString())
        }
    }

    fun launchEmergencyDialer(context: Context, rawTelUriOrNumber: String): Boolean {
        val number = rawTelUriOrNumber.removePrefix("tel:").trim()
        if (!SAFE_PHONE_REGEX.matches(number)) return false
        val dialUri = Uri.fromParts("tel", number, null)
        val intent = Intent(Intent.ACTION_DIAL, dialUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun openInCustomTab(context: Context, url: String, isDarkTheme: Boolean): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false

        val toolbarColor = if (isDarkTheme) 0xFF0C101A.toInt() else 0xFFF5F4F0.toInt()
        val secondaryColor = if (isDarkTheme) 0xFF121724.toInt() else 0xFFECEAE5.toInt()

        val colorParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .setSecondaryToolbarColor(secondaryColor)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorParams)
            .setColorScheme(
                if (isDarkTheme) CustomTabsIntent.COLOR_SCHEME_DARK
                else CustomTabsIntent.COLOR_SCHEME_LIGHT
            )
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()

        return try {
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun createCameraCaptureUri(context: Context): Uri {
        val capturesDir = File(context.cacheDir, "camera_captures").apply {
            if (!exists()) mkdirs()
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFile = File(capturesDir, "CIVIC_CAPTURE_$stamp.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun saveDownloadedPayload(
        context: Context,
        rawFileName: String,
        rawMimeType: String,
        base64Payload: String
    ): DownloadSaveResult {
        return try {
            val cleanBase64 = base64Payload.substringAfter(",", base64Payload)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > 15 * 1024 * 1024) {
                return DownloadSaveResult(false, errorMessage = "Invalid or oversized file payload")
            }

            val safeName = sanitizeDownloadFilename(rawFileName, rawMimeType)
            val safeMime = resolveSafeMimeType(safeName, rawMimeType)

            // 1. Save an app-scoped offline copy in filesDir/receipts/ for instant offline view & FileProvider share
            val receiptsDir = File(context.filesDir, "receipts").apply {
                if (!exists()) mkdirs()
            }.canonicalFile
            val localFile = File(receiptsDir, safeName).canonicalFile
            if (!localFile.path.startsWith(receiptsDir.path + File.separator)) {
                return DownloadSaveResult(false, errorMessage = "Rejected unsafe file path")
            }
            localFile.writeBytes(bytes)

            // 2. Also export to public Downloads via MediaStore on Android 10+ (zero storage permission needed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CivicTriage")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (downloadUri != null) {
                    resolver.openOutputStream(downloadUri)?.use { out ->
                        out.write(bytes)
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(downloadUri, values, null, null)
                }
            }

            DownloadSaveResult(
                success = true,
                receipt = SavedReceiptItem(
                    fileName = localFile.name,
                    absolutePath = localFile.absolutePath,
                    sizeBytes = localFile.length(),
                    lastModifiedMillis = localFile.lastModified(),
                    mimeType = safeMime
                )
            )
        } catch (t: Throwable) {
            DownloadSaveResult(false, errorMessage = t.message ?: "Failed to save receipt")
        }
    }

    fun shareReceipt(context: Context, receipt: SavedReceiptItem): Boolean {
        val file = verifyScopedReceiptFile(context, receipt.absolutePath) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = receipt.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, receipt.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            val chooser = Intent.createChooser(shareIntent, "Share ${receipt.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun openReceipt(context: Context, receipt: SavedReceiptItem): Boolean {
        val file = verifyScopedReceiptFile(context, receipt.absolutePath) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, receipt.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(viewIntent)
            true
        } catch (_: ActivityNotFoundException) {
            shareReceipt(context, receipt)
        }
    }

    private fun verifyScopedReceiptFile(context: Context, path: String): File? {
        val baseDir = File(context.filesDir, "receipts").canonicalFile
        val target = File(path).canonicalFile
        if (!target.path.startsWith(baseDir.path + File.separator)) return null
        return target.takeIf { it.exists() && it.isFile }
    }

    fun sanitizeDownloadFilename(rawName: String, rawMimeType: String): String {
        val baseOnly = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("..", "")
            .replace(SAFE_FILENAME_CHARS, "_")
            .trim('_', '.')
            .take(80)

        val fallbackExt = when {
            rawMimeType.contains("pdf", ignoreCase = true) -> "pdf"
            rawMimeType.contains("png", ignoreCase = true) -> "png"
            rawMimeType.contains("jpeg", ignoreCase = true) || rawMimeType.contains("jpg", ignoreCase = true) -> "jpg"
            rawMimeType.contains("json", ignoreCase = true) -> "json"
            else -> "txt"
        }

        return if (baseOnly.isBlank()) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            "CivicTriage_Receipt_$stamp.$fallbackExt"
        } else if (!baseOnly.contains('.')) {
            "$baseOnly.$fallbackExt"
        } else {
            baseOnly
        }
    }

    private fun resolveSafeMimeType(fileName: String, rawMimeType: String): String {
        val cleaned = rawMimeType.substringBefore(';').trim().lowercase()
        if (cleaned in setOf("text/plain", "application/pdf", "image/png", "image/jpeg", "application/json", "text/csv")) {
            return cleaned
        }
        return ShellPreferencesRepository.guessMimeType(fileName)
    }
}
