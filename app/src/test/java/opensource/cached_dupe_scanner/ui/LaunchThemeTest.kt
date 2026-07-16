package opensource.cached_dupe_scanner.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import opensource.cached_dupe_scanner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchThemeTest {
    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun legacyLaunchWindowUsesDarkBackgroundAtNight() {
        val color = resolveWindowBackground()

        assertTrue(Color.luminance(color) < 0.2f)
    }

    @Test
    @Config(qualifiers = "night")
    fun launchThemeUsesDarkWindowAndSplashBackgroundsAtNight() {
        val colors = resolveLaunchColors()

        assertTrue(Color.luminance(colors.windowBackground) < 0.2f)
        assertTrue(Color.luminance(colors.splashBackground) < 0.2f)
        assertEquals(colors.windowBackground, colors.splashBackground)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun launchThemeKeepsLightWindowAndSplashBackgroundsDuringDay() {
        val colors = resolveLaunchColors()

        assertTrue(Color.luminance(colors.windowBackground) > 0.8f)
        assertTrue(Color.luminance(colors.splashBackground) > 0.8f)
        assertEquals(colors.windowBackground, colors.splashBackground)
    }

    private fun resolveLaunchColors(): LaunchColors {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(context, R.style.Theme_CachedDupeScanner)
        val attributes = themedContext.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowBackground,
                android.R.attr.windowSplashScreenBackground
            )
        )
        return try {
            assertTrue(attributes.hasValue(0))
            assertTrue(attributes.hasValue(1))
            val windowBackground = (attributes.getDrawable(0) as ColorDrawable).color
            val splashBackground = attributes.getColor(1, Color.TRANSPARENT)
            assertEquals(255, Color.alpha(windowBackground))
            assertEquals(255, Color.alpha(splashBackground))
            LaunchColors(
                windowBackground = windowBackground,
                splashBackground = splashBackground
            )
        } finally {
            attributes.recycle()
        }
    }

    private fun resolveWindowBackground(): Int {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(context, R.style.Theme_CachedDupeScanner)
        val attributes = themedContext.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowBackground)
        )
        return try {
            assertTrue(attributes.hasValue(0))
            (attributes.getDrawable(0) as ColorDrawable).color.also { color ->
                assertEquals(255, Color.alpha(color))
            }
        } finally {
            attributes.recycle()
        }
    }
}

private data class LaunchColors(
    val windowBackground: Int,
    val splashBackground: Int
)
