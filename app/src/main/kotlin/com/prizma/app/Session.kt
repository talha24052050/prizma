package com.prizma.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prizma.core.Kind
import com.prizma.core.Level
import com.prizma.core.Trace
import com.prizma.core.hintCell
import com.prizma.core.isSolved
import com.prizma.core.movableStates
import com.prizma.core.solutions
import com.prizma.core.stars
import com.prizma.core.statesOf
import com.prizma.core.trace
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A piece that started turning at [at] (frame clock, ms). [back] is true for an undo, which turns the other way. */
class Turn(val at: Long, val back: Boolean)

/** A particle thrown from a target; positions are in cells, velocity in cells per second. */
class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val hue: Int)

/** One play-through of a board: the moves made, and everything the screen animates. */
class GameSession(val id: LevelId, val level: Level) {
    private val sols = solutions(level)
    private val start = level.movableStates()

    var trace: Trace by mutableStateOf(trace(level))
        private set
    val history = mutableStateListOf<Int>()
    /** Extra taps charged for hints. */
    var penalty by mutableIntStateOf(0)
        private set
    var won by mutableStateOf(false)
        private set
    var hint by mutableIntStateOf(-1)
        private set
    var hintAt = 0L
        private set

    // Read every frame while drawing, so plain collections are enough.
    val turns = HashMap<Int, Turn>()
    val sparks = ArrayList<Spark>()

    val taps: Int get() = history.size + penalty
    val earned: Int get() = stars(taps, level.par)

    /** Turns [cell]. Returns the number of targets this lit, or -1 if nothing happened. */
    fun tap(cell: Int, now: Long): Int {
        if (won || !level.tap(cell)) return -1
        history.add(cell)
        turns[cell] = Turn(now, back = false)
        if (cell == hint) hint = -1
        return refresh(now)
    }

    fun undo(now: Long) {
        if (won || history.isEmpty()) return
        val cell = history.removeAt(history.lastIndex)
        val n = statesOf(level.kind[cell])
        level.state[cell] = (level.state[cell] + n - 1) % n
        turns[cell] = Turn(now, back = true)
        refresh(now)
    }

    fun restart(now: Long) {
        if (won) return
        level.movable.forEachIndexed { k, cell ->
            if (level.state[cell] != start[k]) {
                level.state[cell] = start[k]
                turns[cell] = Turn(now, back = true)
            }
        }
        history.clear()
        refresh(now)
    }

    /** Points out a piece that still needs turning. Costs one tap. */
    fun showHint(now: Long) {
        if (won) return
        hint = hintCell(level, sols)
        hintAt = now
        penalty++
    }

    private fun refresh(now: Long): Int {
        val before = trace
        val after = trace(level)
        var newlyLit = 0
        for (i in 0 until level.size) {
            if (level.kind[i] != Kind.TARGET) continue
            val hue = level.hue[i]
            if (after.recv[i] == hue && before.recv[i] != hue) {
                newlyLit++
                burst(i, 10, now)
            }
        }
        trace = after
        if (isSolved(level, after)) {
            won = true
            for (i in 0 until level.size) if (level.kind[i] == Kind.TARGET) burst(i, 28, now)
        }
        return newlyLit
    }

    private fun burst(cell: Int, count: Int, now: Long) {
        repeat(count) {
            val a = Random.nextFloat() * 6.283f
            val v = 0.6f + Random.nextFloat() * 1.9f
            sparks.add(
                Spark(cell % level.w + 0.5f, cell / level.w + 0.5f, cos(a) * v, sin(a) * v, now, level.hue[cell]),
            )
        }
    }
}
