package com.prizma.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.prizma.core.campaignLevel
import com.prizma.core.movableStates
import com.prizma.core.nearest
import com.prizma.core.solutions
import com.prizma.core.statesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the whole app the way a player would: real screens, real touches on the board. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w400dp-h860dp-xhdpi")
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val store = Store(null)

    /**
     * Moves time on in small slices, letting the UI thread settle after each one.
     * Without the settling, work queued by an injected touch (the click handler, the recomposition)
     * is never run and the screen under test does not change.
     */
    private fun step(millis: Long = 1000) = repeat((millis / 100).toInt().coerceAtLeast(1)) {
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }

    private fun start() {
        rule.mainClock.autoAdvance = false // the board animates forever, so the test moves time itself
        rule.setContent { PrizmaApp(store, Sfx { false }, "1.0") }
        step()
    }

    private fun waitForText(text: String) = rule.waitUntil(20_000) {
        step(100)
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    /** Touches the board cells that solve campaign level [n] in the fewest taps. */
    private fun solveOnScreen(n: Int) {
        val lv = campaignLevel(n)
        val target = nearest(lv, solutions(lv))!!.second
        val cur = lv.movableStates()
        lv.movable.forEachIndexed { k, cell ->
            val turns = (target[k] - cur[k] + statesOf(lv.kind[cell])) % statesOf(lv.kind[cell])
            repeat(turns) {
                rule.onNodeWithTag("board").performTouchInput {
                    val s = width / lv.w.toFloat()
                    down(Offset((cell % lv.w + 0.5f) * s, (cell / lv.w + 0.5f) * s))
                    up()
                }
                step(200)
            }
        }
    }

    @Test fun `plays the first two levels from the home screen`() {
        start()
        rule.onNodeWithText("Mirrors").performClick()
        step()
        rule.onNodeWithText("1").performClick()
        waitForText("Got it")
        rule.onNodeWithText("Got it").performClick()
        step()
        assertTrue(store.seen.containsKey("mirror"))

        solveOnScreen(1)
        step(1500)
        assertEquals(3, store.stars["1"])
        rule.onNodeWithText("Next level").assertIsDisplayed().performClick()
        waitForText("Level 2")
        step()
        solveOnScreen(2)
        step(1500)
        assertEquals(3, store.stars["2"])

        // back to the chapter, then home, with the system back button
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        step()
        rule.onNodeWithText("CHAPTER 1").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        step()
        rule.onNodeWithText("Daily Puzzle").assertIsDisplayed()
        rule.onNodeWithText("6 / 180").assertIsDisplayed()
    }

    @Test fun `undo and hint work on screen`() {
        INTROS.forEach { store.seen[it.key] = true }
        start()
        rule.onNodeWithText("Mirrors").performClick()
        step()
        rule.onNodeWithText("3").performClick()
        waitForText("Hint")
        step()
        val lv = campaignLevel(3)
        // one tap, then undo
        val wrong = lv.movable[0]
        rule.onNodeWithTag("board").performTouchInput {
            val s = width / lv.w.toFloat()
            down(Offset((wrong % lv.w + 0.5f) * s, (wrong / lv.w + 0.5f) * s)); up()
        }
        step()
        rule.onNodeWithText("1").assertIsDisplayed() // tap counter
        rule.onNodeWithText("Undo").performClick()
        step()
        rule.onNodeWithText("0").assertIsDisplayed()
        rule.onNodeWithText("Hint").performClick()
        step()
        rule.onNodeWithText("1").assertIsDisplayed() // the hint cost a tap
    }

    @Test fun `daily puzzle, endless and settings open from home`() {
        INTROS.forEach { store.seen[it.key] = true }
        start()
        rule.onNodeWithText("Daily Puzzle").performClick()
        waitForText("Hint")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        step()
        rule.onNodeWithText("Hard").performClick()
        waitForText("Hard 1")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        step()
        rule.onNodeWithText("Settings").performClick()
        step()
        rule.onNodeWithText("Colour letters").performClick()
        step()
        assertTrue(store.colorLetters)
        rule.onNodeWithText("How to play").performClick()
        step()
        repeat(INTROS.size) {
            rule.onNodeWithText("Got it").performClick()
            step()
        }
        assertEquals(0, rule.onAllNodesWithText("Got it").fetchSemanticsNodes().size)
    }
}
