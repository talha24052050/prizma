package com.prizma.app

import android.content.Context
import com.prizma.core.Kind
import com.prizma.core.Level
import com.prizma.core.LEVEL_COUNT
import com.prizma.core.campaignLevel
import com.prizma.core.movableStates
import com.prizma.core.nearest
import com.prizma.core.solutions
import com.prizma.core.statesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Taps [session] along the shortest way to a solution. */
fun solve(session: GameSession) {
    val lv = session.level
    val target = nearest(lv, solutions(lv))!!.second
    val cur = lv.movableStates()
    lv.movable.forEachIndexed { k, cell ->
        val n = statesOf(lv.kind[cell])
        repeat((target[k] - cur[k] + n) % n) { session.tap(cell, 0) }
    }
}

class SessionTest {
    private fun session(n: Int = 12) = GameSession(LevelId.Campaign(n), campaignLevel(n))

    @Test fun `solving along the shortest way wins with three stars at par`() {
        for (n in listOf(1, 12, 27, 38, 49, 60)) {
            val s = session(n)
            solve(s)
            assertTrue("level $n won", s.won)
            assertEquals("level $n taps", s.level.par, s.taps)
            assertEquals(3, s.earned)
        }
    }

    @Test fun `tapping a fixed cell does nothing`() {
        val s = session()
        val wall = (0 until s.level.size).first { s.level.kind[it] == Kind.EMPTY }
        assertEquals(-1, s.tap(wall, 0))
        assertEquals(0, s.taps)
    }

    @Test fun `undo and restart return to the start`() {
        val s = session(55) // has one-sided mirrors, which turn four ways
        val start = s.level.movableStates()
        val cells = s.level.movable
        s.tap(cells[0], 0); s.tap(cells[1], 0); s.tap(cells[0], 0)
        s.undo(0)
        assertEquals(2, s.taps)
        s.undo(0); s.undo(0)
        assertTrue(start.contentEquals(s.level.movableStates()))
        s.tap(cells.last(), 0); s.tap(cells.last(), 0)
        s.restart(0)
        assertTrue(start.contentEquals(s.level.movableStates()))
        assertEquals(0, s.taps)
    }

    @Test fun `a hint costs a tap and points at a piece that needs turning`() {
        val s = session()
        s.showHint(0)
        assertEquals(1, s.taps)
        assertTrue(s.hint in s.level.movable)
        val before = nearest(s.level, solutions(s.level))!!.first
        s.tap(s.hint, 0)
        assertEquals(before - 1, nearest(s.level, solutions(s.level))!!.first)
        assertEquals(-1, s.hint)
    }

    @Test fun `nothing changes after the win`() {
        val s = session(1)
        solve(s)
        val taps = s.taps
        assertEquals(-1, s.tap(s.level.movable[0], 0))
        s.undo(0); s.restart(0); s.showHint(0)
        assertEquals(taps, s.taps)
        assertTrue(s.won)
    }
}

class StoreTest {
    @Test fun `record keeps the best result`() {
        val store = Store(null)
        store.record(LevelId.Campaign(3), 2)
        store.record(LevelId.Campaign(3), 1)
        assertEquals(2, store.stars["3"])
        store.record(LevelId.Campaign(3), 3)
        assertEquals(3, store.stars["3"])
    }

    @Test fun `levels unlock three past the highest finished`() {
        val store = Store(null)
        assertTrue(store.unlocked(3)); assertFalse(store.unlocked(4))
        store.record(LevelId.Campaign(2), 1)
        assertTrue(store.unlocked(5)); assertFalse(store.unlocked(6))
        assertEquals(0, Store(null).highestDone)
        assertEquals(2, store.highestDone)
    }

    @Test fun `endless counts solved puzzles per difficulty`() {
        val store = Store(null)
        store.record(LevelId.Endless(1, 0), 3)
        store.record(LevelId.Endless(1, 0), 3) // replaying the same puzzle does not count twice
        store.record(LevelId.Endless(1, 1), 1)
        assertEquals(listOf(0, 2, 0), store.endless.toList())
        assertNull(store.stars["e1-0"])
    }

    @Test fun `streak counts consecutive days and survives an unplayed today`() {
        val store = Store(null)
        assertEquals(0, store.streak(20261114))
        store.record(LevelId.Daily(20261112), 3)
        store.record(LevelId.Daily(20261113), 2)
        assertEquals(2, store.streak(20261114)) // today not played yet
        store.record(LevelId.Daily(20261114), 1)
        assertEquals(3, store.streak(20261114))
        assertEquals(0, store.streak(20261116)) // a missed day breaks it
    }

    @Test fun `previous day crosses month and year boundaries`() {
        assertEquals(20261031, previousDay(20261101))
        assertEquals(20251231, previousDay(20260101))
        assertEquals(20240229, previousDay(20240301))
    }

    @Test fun `campaign stars sum over a range`() {
        val store = Store(null)
        store.record(LevelId.Campaign(1), 3); store.record(LevelId.Campaign(11), 2)
        assertEquals(5, store.campaignStars())
        assertEquals(2, store.campaignStars(chapterLevels(1)))
        assertEquals(LEVEL_COUNT, chapterLevels(5).last)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorePersistenceTest {
    @Test fun `progress and settings survive a restart`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("t", Context.MODE_PRIVATE)
        Store(prefs).apply {
            record(LevelId.Campaign(7), 3)
            record(LevelId.Daily(20261114), 2)
            record(LevelId.Endless(2, 4), 3)
            markSeen("mirror")
            sound = false; colorLetters = true
            save()
        }
        val again = Store(prefs)
        assertEquals(3, again.stars["7"])
        assertEquals(2, again.stars["d20261114"])
        assertEquals(listOf(0, 0, 5), again.endless.toList())
        assertTrue(again.seen.containsKey("mirror"))
        assertFalse(again.sound); assertTrue(again.vibration); assertTrue(again.colorLetters)
        again.resetProgress()
        assertTrue(Store(prefs).stars.isEmpty())
    }
}
