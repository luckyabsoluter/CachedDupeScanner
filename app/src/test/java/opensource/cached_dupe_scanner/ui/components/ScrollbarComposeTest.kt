package opensource.cached_dupe_scanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrollbarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingLazyScrollbarToTrackEndReachesVariableHeightListEnd() {
        var capturedState: LazyListState? = null
        composeRule.setContent {
            val listState = rememberLazyListState()
            SideEffect { capturedState = listState }
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .height(400.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items((0 until 20).toList()) { index ->
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (index < 12) 50.dp else 200.dp)
                        )
                    }
                }
                VerticalLazyScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .testTag("lazy-scrollbar")
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lazy-scrollbar").performTouchInput {
            swipe(
                start = Offset(center.x, 8f),
                end = Offset(center.x, bottom),
                durationMillis = 500L
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val listState = requireNotNull(capturedState)
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { item -> item.index == 19 })
            assertFalse(listState.canScrollForward)
        }
    }
}
