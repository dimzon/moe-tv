package tv.own.myowntv.features.shell.components

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsNavigationTest {

    private data class BackCase(
        val name: String,
        val state: SettingsRootBackState,
        val expected: SettingsRootBackAction,
    )

    @Test
    fun `root back follows the priority table`() {
        val cases = listOf(
            BackCase(
                "modal beats every root action",
                SettingsRootBackState(modalOpen = true, searchQuery = "theme", searchMode = true, quickReturn = true),
                SettingsRootBackAction.DEFER_TO_OVERLAY,
            ),
            BackCase(
                "row menu beats search and exit",
                SettingsRootBackState(menuOpen = true, searchQuery = "theme", searchMode = true),
                SettingsRootBackAction.DEFER_TO_OVERLAY,
            ),
            BackCase(
                "nonblank search clears before empty-search return",
                SettingsRootBackState(searchQuery = "theme", searchMode = true, searchFromMore = true, quickReturn = true),
                SettingsRootBackAction.CLEAR_SEARCH,
            ),
            BackCase(
                "nonblank search clears even without search mode",
                SettingsRootBackState(searchQuery = "theme", quickReturn = true),
                SettingsRootBackAction.CLEAR_SEARCH,
            ),
            BackCase(
                "blank search opened from More returns first",
                SettingsRootBackState(searchQuery = "   ", searchMode = true, searchFromMore = true, quickReturn = true),
                SettingsRootBackAction.CLOSE_SEARCH,
            ),
            BackCase(
                "blank search opened from a group returns first",
                SettingsRootBackState(searchMode = true, quickReturn = true),
                SettingsRootBackAction.CLOSE_SEARCH,
            ),
            BackCase(
                "Quick return precedes root exit",
                SettingsRootBackState(quickReturn = true),
                SettingsRootBackAction.RETURN_TO_QUICK,
            ),
            BackCase(
                "ordinary root exits",
                SettingsRootBackState(),
                SettingsRootBackAction.EXIT_ROOT,
            ),
            BackCase(
                "without a root callback the shell keeps Back",
                SettingsRootBackState(canExitRoot = false),
                SettingsRootBackAction.NONE,
            ),
            BackCase(
                "overlay defer does not require a root callback",
                SettingsRootBackState(modalOpen = true, canExitRoot = false),
                SettingsRootBackAction.DEFER_TO_OVERLAY,
            ),
        )

        cases.forEach { case ->
            assertEquals(case.name, case.expected, resolveSettingsRootBack(case.state))
        }
    }

    private data class QuickCase(
        val keys: List<String>,
        val removed: String,
        val expected: String?,
    )

    @Test
    fun `unpin return prefers the next neighbour then the previous one`() {
        val cases = listOf(
            QuickCase(listOf("a", "b", "c"), "a", "b"),
            QuickCase(listOf("a", "b", "c"), "b", "c"),
            QuickCase(listOf("a", "b", "c"), "c", "b"),
            QuickCase(listOf("only"), "only", null),
            QuickCase(emptyList(), "missing", null),
            QuickCase(listOf("a", "b"), "missing", "a"),
        )

        cases.forEach { case ->
            assertEquals("unpin ${case.removed} from ${case.keys}", case.expected, quickReturnKeyAfterUnpin(case.keys, case.removed))
        }
    }

    private enum class FocusOutcome { FOCUSED, NOT_FOCUSED, THREW }

    private data class FocusCase(
        val name: String,
        val outcomes: Map<String, List<FocusOutcome>>,
        val framesPerTarget: Int,
        val expectedTarget: String?,
        val expectedFrames: Int,
    )

    @Test
    fun `focus fallback retries false results across frames before moving on`() = runBlocking {
        val targets = listOf("neighbour", "first_row", "search")
        val cases = listOf(
            FocusCase(
                "false is retried until the neighbour attaches",
                mapOf(
                    "neighbour" to listOf(FocusOutcome.NOT_FOCUSED, FocusOutcome.FOCUSED),
                    "first_row" to listOf(FocusOutcome.FOCUSED),
                    "search" to listOf(FocusOutcome.FOCUSED),
                ),
                framesPerTarget = 3,
                expectedTarget = "neighbour",
                expectedFrames = 2,
            ),
            FocusCase(
                "an unattached neighbour falls back to the first row",
                mapOf(
                    "neighbour" to List(3) { FocusOutcome.NOT_FOCUSED },
                    "first_row" to listOf(FocusOutcome.FOCUSED),
                    "search" to listOf(FocusOutcome.FOCUSED),
                ),
                framesPerTarget = 3,
                expectedTarget = "first_row",
                expectedFrames = 4,
            ),
            FocusCase(
                "exceptions are false results and empty Quick reaches search",
                mapOf(
                    "neighbour" to listOf(FocusOutcome.THREW, FocusOutcome.NOT_FOCUSED),
                    "first_row" to List(2) { FocusOutcome.NOT_FOCUSED },
                    "search" to listOf(FocusOutcome.FOCUSED),
                ),
                framesPerTarget = 2,
                expectedTarget = "search",
                expectedFrames = 5,
            ),
            FocusCase(
                "all explicit targets can fail",
                mapOf(
                    "neighbour" to List(2) { FocusOutcome.NOT_FOCUSED },
                    "first_row" to List(2) { FocusOutcome.NOT_FOCUSED },
                    "search" to List(2) { FocusOutcome.NOT_FOCUSED },
                ),
                framesPerTarget = 2,
                expectedTarget = null,
                expectedFrames = 6,
            ),
            FocusCase(
                "zero frames makes no request",
                emptyMap(),
                framesPerTarget = 0,
                expectedTarget = null,
                expectedFrames = 0,
            ),
        )

        cases.forEach { case ->
            var frames = 0
            var focusedTarget: String? = null
            val attempts = mutableListOf<String>()
            val landed = requestFocusAcrossFrames(
                targets = targets,
                maxFramesPerTarget = case.framesPerTarget,
                waitForNextFrame = { frames++ },
            ) { target ->
                attempts += target
                val attempt = attempts.count { it == target }
                val outcome = case.outcomes[target]?.getOrElse(attempt - 1) { FocusOutcome.NOT_FOCUSED }
                    ?: FocusOutcome.NOT_FOCUSED
                when (outcome) {
                    FocusOutcome.FOCUSED -> {
                        focusedTarget = target
                        true
                    }
                    FocusOutcome.NOT_FOCUSED -> false
                    FocusOutcome.THREW -> error("unattached requester")
                }
            }
            assertEquals(case.name, case.expectedTarget != null, landed)
            assertEquals(case.name, case.expectedTarget, focusedTarget)
            assertEquals(case.name, case.expectedFrames, frames)
        }
    }

    @Test
    fun `empty focus target list does not claim focus`() = runBlocking {
        var frames = 0
        val landed = requestFocusAcrossFrames<Unit>(
            targets = emptyList(),
            waitForNextFrame = { frames++ },
        ) { true }
        assertFalse(landed)
        assertEquals(0, frames)
    }
}
