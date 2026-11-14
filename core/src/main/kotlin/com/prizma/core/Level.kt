package com.prizma.core

/** Cell kinds. Stored as ints so the solver's inner loop stays allocation-free. */
object Kind {
    const val EMPTY = 0
    const val WALL = 1
    const val EMIT = 2
    /** Two-sided mirror. State 0 is '/', state 1 is '\'. */
    const val MIRROR = 3
    /** Lets the beam through and also reflects it like a mirror in the same state. */
    const val SPLIT = 4
    /** Passes only its own colour bits. */
    const val FILTER = 5
    const val TARGET = 6
    /** A beam entering one portal leaves its partner travelling in the same direction. */
    const val PORTAL = 7
    /** One-sided mirror: reflects on one face, blocks on the other. Four states, see [ONEWAY]. */
    const val ONEWAY = 8
}

/** Colours are bit masks: red 1, green 2, blue 4. Mixed light is the union (yellow 3, magenta 5, cyan 6, white 7). */
object Hue {
    const val RED = 1
    const val GREEN = 2
    const val BLUE = 4
    const val WHITE = 7
    val PRIMARIES = intArrayOf(RED, GREEN, BLUE)
}

/** Directions: 0 east, 1 south, 2 west, 3 north (y grows downward). */
val DX = intArrayOf(1, 0, -1, 0)
val DY = intArrayOf(0, 1, 0, -1)

/** REFLECT[state][travelDir] for two-sided mirrors and splitters. */
val REFLECT = arrayOf(intArrayOf(3, 2, 1, 0), intArrayOf(1, 0, 3, 2))

/**
 * ONEWAY[state][travelDir] is the new direction, or -1 when the beam hits the back and stops.
 * The reflective face points at a corner: 0 north-west, 1 north-east, 2 south-east, 3 south-west,
 * so each tap turns the mirror a quarter turn clockwise.
 */
val ONEWAY = arrayOf(
    intArrayOf(3, 2, -1, -1),
    intArrayOf(-1, 0, 3, -1),
    intArrayOf(-1, -1, 1, 0),
    intArrayOf(1, -1, -1, 2),
)

/** Number of states a player can cycle a cell through; 1 means the cell is fixed. */
fun statesOf(kind: Int): Int = when (kind) {
    Kind.MIRROR, Kind.SPLIT -> 2
    Kind.ONEWAY -> 4
    else -> 1
}

/**
 * A puzzle board. Cells are indexed `y * w + x`.
 *
 * @property kind one of [Kind] per cell
 * @property arg emitter direction, or partner cell index for a portal
 * @property hue colour of an emitter, filter or target
 * @property state current orientation of a movable cell; the only part that changes during play
 * @property par fewest taps that solve the board from its starting state
 */
class Level(
    val w: Int,
    val h: Int,
    val kind: IntArray = IntArray(w * h),
    val arg: IntArray = IntArray(w * h),
    val hue: IntArray = IntArray(w * h),
    val state: IntArray = IntArray(w * h),
    var par: Int = 0,
) {
    val size: Int get() = w * h

    /** Indices of the cells the player can turn, in board order. */
    val movable: IntArray by lazy { (0 until size).filter { statesOf(kind[it]) > 1 }.toIntArray() }

    fun copy(): Level = Level(w, h, kind.copyOf(), arg.copyOf(), hue.copyOf(), state.copyOf(), par)

    /** Turns a movable cell one step and returns true, or returns false for a fixed cell. */
    fun tap(cell: Int): Boolean {
        val n = statesOf(kind[cell])
        if (n == 1) return false
        state[cell] = (state[cell] + 1) % n
        return true
    }

    override fun equals(other: Any?): Boolean =
        other is Level && w == other.w && h == other.h && par == other.par &&
            kind.contentEquals(other.kind) && arg.contentEquals(other.arg) &&
            hue.contentEquals(other.hue) && state.contentEquals(other.state)

    override fun hashCode(): Int = kind.contentHashCode() * 31 + state.contentHashCode()
}

/** Result of following every beam across a board. */
class Trace(
    /** out[cell * 4 + dir]: colour of the beam leaving `cell` towards `dir`. */
    val out: IntArray,
    /** recv[cell]: colour arriving at a target. */
    val recv: IntArray,
    /** inb[cell]: colour of all beams entering the cell, whatever it holds. */
    val inb: IntArray,
)

fun trace(lv: Level): Trace {
    val w = lv.w
    val h = lv.h
    val n = lv.size
    val out = IntArray(n * 4)
    val recv = IntArray(n)
    val inb = IntArray(n)
    // Every processed entry adds a new colour bit to one (cell, dir) slot and pushes at most two
    // entries, so the queue never needs more than this.
    val queue = IntArray((n * 24 + n) * 3)
    var tail = 0
    for (i in 0 until n) {
        if (lv.kind[i] == Kind.EMIT) {
            queue[tail++] = i; queue[tail++] = lv.arg[i]; queue[tail++] = lv.hue[i]
        }
    }
    var head = 0
    while (head < tail) {
        val i = queue[head++]
        val d = queue[head++]
        val bits = queue[head++] and out[i * 4 + d].inv()
        if (bits == 0) continue
        out[i * 4 + d] = out[i * 4 + d] or bits
        val x = i % w + DX[d]
        val y = i / w + DY[d]
        if (x < 0 || y < 0 || x >= w || y >= h) continue
        val j = y * w + x
        inb[j] = inb[j] or bits
        when (lv.kind[j]) {
            Kind.EMPTY -> { queue[tail++] = j; queue[tail++] = d; queue[tail++] = bits }
            Kind.TARGET -> recv[j] = recv[j] or bits
            Kind.MIRROR -> { queue[tail++] = j; queue[tail++] = REFLECT[lv.state[j]][d]; queue[tail++] = bits }
            Kind.SPLIT -> {
                queue[tail++] = j; queue[tail++] = d; queue[tail++] = bits
                queue[tail++] = j; queue[tail++] = REFLECT[lv.state[j]][d]; queue[tail++] = bits
            }
            Kind.FILTER -> {
                val passed = bits and lv.hue[j]
                if (passed != 0) { queue[tail++] = j; queue[tail++] = d; queue[tail++] = passed }
            }
            Kind.PORTAL -> { queue[tail++] = lv.arg[j]; queue[tail++] = d; queue[tail++] = bits }
            Kind.ONEWAY -> {
                val nd = ONEWAY[lv.state[j]][d]
                if (nd >= 0) { queue[tail++] = j; queue[tail++] = nd; queue[tail++] = bits }
            }
        }
    }
    return Trace(out, recv, inb)
}

/** Number of targets currently receiving exactly their own colour. */
fun litCount(lv: Level, tr: Trace = trace(lv)): Int =
    (0 until lv.size).count { lv.kind[it] == Kind.TARGET && tr.recv[it] == lv.hue[it] }

fun isSolved(lv: Level, tr: Trace = trace(lv)): Boolean =
    (0 until lv.size).all { lv.kind[it] != Kind.TARGET || tr.recv[it] == lv.hue[it] }

/** 3 stars at par, 2 within two taps of it, otherwise 1. */
fun stars(taps: Int, par: Int): Int = if (taps <= par) 3 else if (taps <= par + 2) 2 else 1
