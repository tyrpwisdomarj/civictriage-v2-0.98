package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.NativeIntentHandler
import com.example.data.ShellPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches CivicTriage LSG`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CivicTriage LSG", appName)
    }

    @Test
    fun `portal url validation enforces https and blocks unsafe targets`() {
        assertNotNull(ShellPreferencesRepository.normalizePortalUrl(BuildConfig.DEFAULT_LIVE_WEB_URL))
        assertNull(ShellPreferencesRepository.normalizePortalUrl("http://example.com"))
        assertNull(ShellPreferencesRepository.normalizePortalUrl("javascript:alert(1)"))
        assertNull(ShellPreferencesRepository.normalizePortalUrl("https://127.0.0.1/admin"))
        assertNull(ShellPreferencesRepository.normalizePortalUrl("https://169.254.169.254/latest"))
    }

    @Test
    fun `receipt filename sanitization prevents path traversal`() {
        val safe = NativeIntentHandler.sanitizeDownloadFilename("../../etc/passwd.txt", "text/plain")
        assertFalse(safe.contains(".."))
        assertFalse(safe.contains("/"))
        assertTrue(safe.endsWith(".txt"))
    }
}
