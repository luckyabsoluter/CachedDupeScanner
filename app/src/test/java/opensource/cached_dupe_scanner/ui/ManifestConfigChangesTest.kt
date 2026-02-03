package opensource.cached_dupe_scanner.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestConfigChangesTest {
    @Test
    fun mainActivityHandlesUiModeChanges() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val manifestFile = sequenceOf(
            File(projectDir, "app/src/main/AndroidManifest.xml"),
            File(projectDir.parentFile ?: projectDir, "app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assertTrue("AndroidManifest.xml should exist", manifestFile != null)

        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
            .newDocumentBuilder()
            .parse(manifestFile!!)
        val activities = document.getElementsByTagName("activity")

        var found = false
        var configChanges: String? = null
        for (index in 0 until activities.length) {
            val node = activities.item(index)
            val element = node as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android",
                "name"
            )
            if (name == ".MainActivity" || name.endsWith(".MainActivity")) {
                found = true
                configChanges = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android",
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
}
