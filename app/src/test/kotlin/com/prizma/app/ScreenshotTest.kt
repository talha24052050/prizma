package com.prizma.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen to build/shots/ so the UI can be checked without a device.
 * These are pictures to look at, not assertions; the tests fail only if a screen crashes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "tr-w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = 20261114
    private val sfx = Sfx { false }

    private fun shot(name: String, millis: Long = 1500, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { Box(Modifier.fillMaxSize().background(Palette.Bg)) { content() } }
        rule.mainClock.advanceTimeBy(millis)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun seenAll(store: Store) = INTROS.forEach { store.seen[it.key] = true }

    private fun progressed() = Store(null).apply {
        seenAll(this)
        for (n in 1..23) stars["$n"] = if (n % 4 == 0) 2 else 3
        stars["d20261113"] = 3; stars["d20261112"] = 2; stars["d20261111"] = 3
        endless[0] = 12; endless[1] = 4
    }

    private fun game(name: String, id: LevelId, store: Store = Store(null).also { seenAll(it) }, solve: Boolean = false) {
        val session = GameSession(id, id.build())
        if (solve) {
            solve(session)
            store.record(id, session.earned)
        }
        shot(name) { GameContent(session, store, sfx, today, {}, {}) }
    }

    @Test fun homeFresh() = shot("home_fresh") { HomeScreen(Store(null), today, {}, {}, {}) }
    @Test fun homeProgress() = shot("home_progress") { HomeScreen(progressed(), today, {}, {}, {}) }
    @Test @Config(qualifiers = "en-w400dp-h860dp-xhdpi")
    fun homeEnglish() = shot("home_en") { HomeScreen(progressed(), today, {}, {}, {}) }
    @Test fun chapter() = shot("chapter") { ChapterScreen(progressed(), 1, {}, {}) }
    @Test fun settings() = shot("settings") { SettingsScreen(progressed(), "1.0", {}) }

    @Test fun level1Intro() = game("game_01_intro", LevelId.Campaign(1), Store(null))
    @Test fun level5() = game("game_05", LevelId.Campaign(5))
    @Test fun level17() = game("game_17", LevelId.Campaign(17))
    @Test fun level26() = game("game_26", LevelId.Campaign(26))
    @Test fun level35() = game("game_35", LevelId.Campaign(35))
    @Test fun level46() = game("game_46", LevelId.Campaign(46))
    @Test fun level52() = game("game_52", LevelId.Campaign(52))
    @Test fun level58() = game("game_58", LevelId.Campaign(58))
    @Test fun level58Letters() = game("game_58_letters", LevelId.Campaign(58), Store(null).also { seenAll(it); it.colorLetters = true })
    @Test fun level35Won() = game("game_35_won", LevelId.Campaign(35), solve = true)
    @Test fun dailyWon() = game("daily_won", LevelId.Daily(today), progressed(), solve = true)
    @Test fun endless() = game("endless", LevelId.Endless(2, 3))

    @Test fun introPortal() = shot("intro_portal") { IntroCard(INTROS[5], rememberFrameMillis(), null) {} }
    @Test fun introOneway() = shot("intro_oneway", 2000) { IntroCard(INTROS[6], rememberFrameMillis(), null) {} }
    @Test fun introSplit() = shot("intro_split") { IntroCard(INTROS[3], rememberFrameMillis(), null) {} }
}
