package opensource.cached_dupe_scanner.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.MainActivity
import opensource.cached_dupe_scanner.notifications.TaskForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManifestConfigChangesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val packageManager = context.packageManager

    @Test
    fun mainActivityHandlesUiAndScreenSizeChanges() {
        val activityInfo = packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        val expectedChanges = ActivityInfo.CONFIG_UI_MODE or
            ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN

        assertEquals(expectedChanges, activityInfo.configChanges and expectedChanges)
    }

    @Test
    fun packagedApplicationManifestOptsIntoOnBackInvokedCallbacks() {
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { manifest ->
            val application = manifest.moveToStartTag("application")

            assertTrue(
                application.getAttributeBooleanValue(
                    ANDROID_NAMESPACE,
                    "enableOnBackInvokedCallback",
                    false
                )
            )
        }
    }

    @Test
    fun taskForegroundServiceHasRuntimeManifestContract() {
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(requestedPermissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(requestedPermissions.contains(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC))

        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, TaskForegroundService::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(serviceInfo.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun XmlResourceParser.moveToStartTag(tagName: String): XmlResourceParser {
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && name == tagName) return this
            next()
        }
        error("Packaged manifest does not contain <$tagName>")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
