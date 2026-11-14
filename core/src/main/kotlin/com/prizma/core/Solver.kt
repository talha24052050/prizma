package com.prizma.core

/** Current states of the movable cells, in [Level.movable] order. */
fun Level.movableStates(): IntArray = IntArray(movable.size) { state[movable[it]] }

fun Level.setMovableStates(states: IntArray) {
    for (k in movable.indices) state[movable[k]] = states[k]
}

/**
 * Every combination of movable states that solves the board, by brute force.
 * Boards are generated with few enough movable cells (a few thousand combinations) for this to be instant.
 */
fun solutions(level: Level): List<IntArray> {
    val lv = level.copy()
    val pieces = lv.movable
    val radix = IntArray(pieces.size) { statesOf(lv.kind[pieces[it]]) }
    val cur = IntArray(pieces.size)
    val found = ArrayList<IntArray>()
    while (true) {
        for (k in pieces.indices) lv.state[pieces[k]] = cur[k]
        if (isSolved(lv)) found.add(cur.copyOf())
        // odometer increment
        var k = 0
        while (k < cur.size) {
            if (++cur[k] < radix[k]) break
            cur[k] = 0
            k++
        }
        if (k == cur.size) break
    }
    return found
}

/** Taps needed to go from one state vector to another. Cells only turn one way, so the distance is cyclic. */
fun tapsBetween(lv: Level, from: IntArray, to: IntArray): Int {
    var sum = 0
    for (k in from.indices) {
        val n = statesOf(lv.kind[lv.movable[k]])
        sum += (to[k] - from[k] + n) % n
    }
    return sum
}

/** The solution reachable in the fewest taps from [from], or null when [sols] is empty. */
fun nearest(lv: Level, sols: List<IntArray>, from: IntArray = lv.movableStates()): Pair<Int, IntArray>? {
    var best: Pair<Int, IntArray>? = null
    for (s in sols) {
        val d = tapsBetween(lv, from, s)
        if (best == null || d < best.first) best = d to s
    }
    return best
}

/** A cell that still needs turning on the shortest way to a solution, or -1 when already solved. */
fun hintCell(lv: Level, sols: List<IntArray>): Int {
    val target = nearest(lv, sols)?.second ?: return -1
    val cur = lv.movableStates()
    for (k in cur.indices) if (cur[k] != target[k]) return lv.movable[k]
    return -1
}
