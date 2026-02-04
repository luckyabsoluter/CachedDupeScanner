package opensource.cached_dupe_scanner.core

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class PathNormalizerTest {
    @Test
    fun normalizeForSdk_preO_usesFallbackNormalization() {
        val normalized = PathNormalizer.normalizeForSdk(
            path = "root/dir/../file.txt",
            sdkInt = Build.VERSION_CODES.N
        )

        assertEquals("root/file.txt", normalized)
    }

    @Test
    fun normalizeForSdk_oOrAbove_usesPathsNormalization() {
        val normalized = PathNormalizer.normalizeForSdk(
            path = "root/dir/../file.txt",
            sdkInt = Build.VERSION_CODES.O
        )

        assertEquals("root/file.txt", normalized)
    }
}
