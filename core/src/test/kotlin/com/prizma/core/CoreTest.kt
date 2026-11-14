package com.prizma.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Builds a board from rows of symbols:
 * `.` empty, `#` wall, `/` `\` mirror, `x` `y` splitter ('/' and '\'), `>` `v` `<` `^` red source,
 * `g` green source facing east, `w` white source facing east, `R G B Y W` targets, `r` red filter,
 * `p` portal (exactly two per board), `0`..`3` one-sided mirror in that state.
 */
private fun board(vararg rows: String): Level {
    val lv = Level(rows[0].length, rows.size)
    val portals = ArrayList<Int>()
    rows.joinToString("").forEachIndexed { i, ch ->
        fun set(kind: Int, arg: Int = 0, hue: Int = 0, state: Int = 0) {
            lv.kind[i] = kind; lv.arg[i] = arg; lv.hue[i] = hue; lv.state[i] = state
        }
        when (ch) {
            '#' -> set(Kind.WALL)
            '/' -> set(Kind.MIRROR, state = 0)
            '\\' -> set(Kind.MIRROR, state = 1)
            'x' -> set(Kind.SPLIT, state = 0)
            'y' -> set(Kind.SPLIT, state = 1)
            '>' -> set(Kind.EMIT, arg = 0, hue = 1)
            'v' -> set(Kind.EMIT, arg = 1, hue = 1)
            '<' -> set(Kind.EMIT, arg = 2, hue = 1)
            '^' -> set(Kind.EMIT, arg = 3, hue = 1)
            'g' -> set(Kind.EMIT, arg = 0, hue = 2)
            'w' -> set(Kind.EMIT, arg = 0, hue = 7)
            'R' -> set(Kind.TARGET, hue = 1)
            'G' -> set(Kind.TARGET, hue = 2)
            'B' -> set(Kind.TARGET, hue = 4)
            'Y' -> set(Kind.TARGET, hue = 3)
            'W' -> set(Kind.TARGET, hue = 7)
            'r' -> set(Kind.FILTER, hue = 1)
            'p' -> { set(Kind.PORTAL); portals.add(i) }
            in '0'..'3' -> set(Kind.ONEWAY, state = ch - '0')
        }
    }
    if (portals.size == 2) {
        lv.arg[portals[0]] = portals[1]
        lv.arg[portals[1]] = portals[0]
    }
    return lv
}

class TraceTest {
    @Test fun `beam travels straight and lights its target`() = assertTrue(isSolved(board(">..R")))

    @Test fun `wall stops the beam`() = assertFalse(isSolved(board(">.#R")))

    @Test fun `slash mirror turns an eastbound beam north`() {
        assertTrue(isSolved(board("..R", ">./")))
        assertFalse(isSolved(board("..R", ">.\\")))
    }

    @Test fun `backslash mirror turns an eastbound beam south`() = assertTrue(isSolved(board(">.\\", "..R")))

    @Test fun `splitter passes and reflects`() = assertTrue(isSolved(board(".R.", ">xR")))

    @Test fun `two colours mix on one target`() {
        assertFalse(isSolved(board(".v.", "g.Y"))) // only green arrives
        assertTrue(isSolved(board("..v", "g.Y")))
        assertTrue(isSolved(board("v..", "\\.Y", "...", "g./")))
    }

    @Test fun `target needs exactly its colour`() = assertFalse(isSolved(board("..v", "g.G")))

    @Test fun `filter passes only its own colour`() {
        assertTrue(isSolved(board("w.rR")))
        assertFalse(isSolved(board("w.rW")))
        assertFalse(isSolved(board("g.rR")))
    }

    @Test fun `portal moves the beam and keeps its direction`() {
        assertTrue(isSolved(board(">p..", "....", ".p.R")))
        assertFalse(isSolved(board(">p.R", "....", ".p..")))
    }

    @Test fun `portal works in both directions`() = assertTrue(isSolved(board("Rp..", "....", ".p.<")))

    @Test fun `one-sided mirror reflects on its face and blocks on its back`() {
        // state 0 faces north-west: an eastbound beam turns north, a westbound beam is blocked
        assertTrue(isSolved(board(".R.", ">0.")))
        assertFalse(isSolved(board(".R.", ".0<")))
        assertFalse(isSolved(board("...", ".0<", ".R.")))
        // state 2 faces south-east: a westbound beam turns south
        assertTrue(isSolved(board("...", ".2<", ".R.")))
        // state 3 faces south-west: an eastbound beam turns south
        assertTrue(isSolved(board(">3.", ".R.")))
        // state 1 faces north-east: a westbound beam turns north
        assertTrue(isSolved(board(".R.", ".1<")))
    }

    @Test fun `every one-sided state reflects two directions and blocks two`() {
        for (s in 0..3) assertEquals(2, ONEWAY[s].count { it >= 0 })
    }

    @Test fun `beams circling between mirrors terminate`() {
        trace(board("/y\\", ">./", "\\./"))
        trace(board("p.>", "/.\\", "\\p/"))
    }
}

class SolverTest {
    @Test fun `tap cycles through the states of a piece`() {
        val lv = board("/0R")
        assertTrue(lv.tap(0)); assertEquals(1, lv.state[0])
        assertTrue(lv.tap(0)); assertEquals(0, lv.state[0])
        repeat(4) { assertTrue(lv.tap(1)) }
        assertEquals(0, lv.state[1])
        assertFalse(lv.tap(2))
    }

    @Test fun `solutions lists exactly the solving states and leaves the board alone`() {
        val lv = board("..R", ">.\\")
        val sols = solutions(lv)
        assertEquals(1, sols.size)
        assertContentEquals(intArrayOf(0), sols[0])
        assertEquals(1, lv.state[5])
    }

    @Test fun `distance to a solution counts one-way turns`() {
        val lv = board(".R.", ">3.") // needs state 0: one tap from state 3, three taps from state 1
        val sols = solutions(lv)
        assertEquals(1, nearest(lv, sols)!!.first)
        lv.state[4] = 1
        assertEquals(3, nearest(lv, sols)!!.first)
    }

    @Test fun `hint names a piece that needs turning`() {
        val lv = board("..R", ">.\\")
        val sols = solutions(lv)
        assertEquals(5, hintCell(lv, sols))
        lv.tap(5)
        assertEquals(-1, hintCell(lv, sols))
    }

    @Test fun `stars reward reaching par`() =
        assertEquals(listOf(3, 3, 2, 2, 1), listOf(3, 4, 5, 6, 7).map { stars(it, 4) })
}

class GeneratorTest {
    private fun check(name: String, lv: Level) {
        assertEquals(0, litCount(lv), "$name starts with a lit target")
        val sols = solutions(lv)
        assertTrue(sols.isNotEmpty(), "$name has no solution")
        assertEquals(lv.par, nearest(lv, sols)!!.first, "$name par")
        assertTrue(lv.par >= 1, "$name par >= 1")
        // Light that reaches a target without passing a movable piece would feed it for free.
        val frozen = lv.copy()
        for (i in 0 until lv.size) if (statesOf(lv.kind[i]) > 1) frozen.kind[i] = Kind.WALL
        val fixed = trace(frozen).recv
        for (i in 0 until lv.size) {
            if (lv.kind[i] == Kind.TARGET) assertEquals(0, fixed[i], "$name has a target lit without any movable piece")
        }
        for (i in 0 until lv.size) {
            if (lv.kind[i] == Kind.PORTAL) {
                assertEquals(Kind.PORTAL, lv.kind[lv.arg[i]], "$name portal partner")
                assertEquals(i, lv.arg[lv.arg[i]], "$name portal pairing")
            }
        }
    }

    @Test fun `same seed gives the same level`() {
        val p = campaignParams(25)
        assertEquals(generate(42, p), generate(42, p))
        assertNotEquals(generate(42, p), generate(43, p))
    }

    @Test fun `every campaign level is solvable and fair`() {
        for (n in 1..LEVEL_COUNT) {
            val lv = campaignLevel(n)
            val p = campaignParams(n)
            check("level $n", lv)
            assertEquals(p.w * p.h, lv.size, "level $n size")
            assertEquals(p.targets, lv.kind.count { it == Kind.TARGET }, "level $n targets")
            if (p.need != Kind.EMPTY) assertTrue(p.need in lv.kind, "level $n lacks its chapter's piece")
        }
    }

    @Test fun `no piece is idle in the solved board`() {
        // Applying the nearest solution, removing any single movable piece must change the outcome
        // for at least most pieces; a piece is idle when the board stays solved without it.
        for (n in 1..LEVEL_COUNT) {
            val lv = campaignLevel(n)
            val sols = solutions(lv)
            val anyTight = sols.any { s ->
                val solved = lv.copy().also { it.setMovableStates(s) }
                solved.movable.all { cell ->
                    val without = solved.copy()
                    without.kind[cell] = Kind.EMPTY
                    !isSolved(without)
                }
            }
            assertTrue(anyTight, "level $n has an idle piece in every solution")
        }
    }

    @Test fun `turning every piece once does not solve a level`() {
        for (n in 1..LEVEL_COUNT) {
            val lv = campaignLevel(n)
            if (lv.movable.size < 4) continue
            lv.movable.forEach { lv.tap(it) }
            assertFalse(isSolved(lv), "level $n is solved by tapping everything once")
        }
    }

    @Test fun `difficulty grows through the campaign`() {
        fun avg(range: IntRange) = range.map { campaignLevel(it).par }.average()
        assertTrue(avg(1..10) < avg(21..30))
        assertTrue(avg(21..30) < avg(51..60))
    }

    @Test fun `daily puzzles are fair and differ by day`() {
        for (d in 20261101..20261130) check("daily $d", dailyLevel(d))
        assertNotEquals(dailyLevel(20261113), dailyLevel(20261114))
        assertEquals(dailyLevel(20261113), dailyLevel(20261113))
    }

    @Test fun `endless puzzles are fair at every difficulty`() {
        for (d in 0 until DIFFICULTIES) for (i in 0 until 12) check("endless $d/$i", endlessLevel(d, i))
    }
}
