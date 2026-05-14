package opensource.cached_dupe_scanner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestConfigChangesTest {
    @Test
    fun mainActivityHandlesUiModeChanges() {
        val document = parseManifest()
        val activities = document.getElementsByTagName("activity")

        var found = false
        var configChanges: String? = null
        for (index in 0 until activities.length) {
            val node = activities.item(index)
            val element = node as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(
                ANDROID_NS,
                "name"
            )
            if (name == ".MainActivity" || name.endsWith(".MainActivity")) {
                found = true
                configChanges = element.getAttributeNS(
                    ANDROID_NS,
                    "configChanges"
                )
                break
            }
        }

        assertTrue("MainActivity should be declared in the manifest", found)
        val configList = configChanges
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        assertTrue("MainActivity should handle uiMode config changes", configList.contains("uiMode"))
    }

    @Test
    fun scanForegroundServiceIsDeclaredForBackgroundScanning() {
        val document = parseManifest()
        val manifest = document.documentElement
        val permissions = (0 until manifest.getElementsByTagName("uses-permission").length)
            .map { manifest.getElementsByTagName("uses-permission").item(it) }
            .mapNotNull { it as? org.w3c.dom.Element }
            .map { it.getAttributeNS(ANDROID_NS, "name") }

        assertTrue(
            "Scan foreground service should have the base foreground service permission",
            permissions.contains("android.permission.FOREGROUND_SERVICE")
        )
        assertTrue(
            "Scan foreground service should declare the dataSync foreground service permission",
            permissions.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
        )

        val services = document.getElementsByTagName("service")
        val scanService = (0 until services.length)
            .map { services.item(it) }
            .mapNotNull { it as? org.w3c.dom.Element }
            .firstOrNull {
                val name = it.getAttributeNS(ANDROID_NS, "name")
                name == ".notifications.ScanForegroundService" || name.endsWith(".notifications.ScanForegroundService")
            }

        assertTrue("ScanForegroundService should be declared", scanService != null)
        assertEquals("false", scanService?.getAttributeNS(ANDROID_NS, "exported"))
        assertEquals("dataSync", scanService?.getAttributeNS(ANDROID_NS, "foregroundServiceType"))
    }

    private fun parseManifest(): org.w3c.dom.Document {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val manifestFile = sequenceOf(
            File(projectDir, "app/src/main/AndroidManifest.xml"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assertTrue("AndroidManifest.xml should exist", manifestFile != null)

        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
            .newDocumentBuilder()
            .parse(manifestFile!!)
    }

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
