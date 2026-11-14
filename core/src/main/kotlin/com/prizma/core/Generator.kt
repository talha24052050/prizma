package com.prizma.core

/** Small deterministic generator (SplitMix64) so a seed yields the same level on every device. */
class Rng(seed: Long) {
    private var s = seed

    private fun next(): Long {
        s += -0x61c8864680b583ebL
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform int in `0 until bound`. */
    fun int(bound: Int): Int = ((next() ushr 33) % bound).toInt()
}

/**
 * Recipe for a board.
 *
 * Piece counts are upper bounds: pieces the solution does not need are removed after generation.
 *
 * @property emit colour of each light source
 * @property mix require at least one target whose colour no single source emits
 * @property need a [Kind] that must survive pruning, so a chapter always shows its own piece
 * @property minPieces fewest movable cells the finished board may have
 */
data class Params(
    val w: Int,
    val h: Int,
    val emit: List<Int>,
    val targets: Int,
    val minPar: Int,
    val mirrors: Int = 0,
    val splits: Int = 0,
    val filters: Int = 0,
    val walls: Int = 0,
    val portals: Int = 0,
    val oneways: Int = 0,
    val mix: Boolean = false,
    val need: Int = Kind.EMPTY,
    val minPieces: Int = 2,
)

/**
 * Colour reaching each cell when every movable piece is treated as a wall: light no move can change.
 * A target standing in such light would be fed without the player doing anything.
 */
private fun fixedLight(lv: Level): IntArray {
    val frozen = lv.copy()
    for (i in 0 until lv.size) if (statesOf(lv.kind[i]) > 1) frozen.kind[i] = Kind.WALL
    return trace(frozen).inb
}

private fun clear(lv: Level, i: Int) {
    lv.kind[i] = Kind.EMPTY; lv.arg[i] = 0; lv.hue[i] = 0; lv.state[i] = 0
}

/** Removes every piece the solved board does not depend on, so nothing on the board is idle. */
private fun prune(lv: Level) {
    var changed = true
    while (changed) { // removing one piece can free another, so repeat until nothing more goes
        changed = false
        for (i in 0 until lv.size) {
            val kind = lv.kind[i]
            if (kind == Kind.EMPTY || kind == Kind.WALL || kind == Kind.TARGET) continue
            if (kind == Kind.PORTAL && lv.arg[i] < i) continue // handled with its partner
            val partner = if (kind == Kind.PORTAL) lv.arg[i] else -1
            val saved = intArrayOf(kind, lv.arg[i], lv.hue[i], lv.state[i])
            val savedPartnerHue = if (partner >= 0) lv.hue[partner] else 0
            clear(lv, i)
            if (partner >= 0) clear(lv, partner)
            if (isSolved(lv)) { changed = true; continue }
            lv.kind[i] = saved[0]; lv.arg[i] = saved[1]; lv.hue[i] = saved[2]; lv.state[i] = saved[3]
            if (partner >= 0) {
                lv.kind[partner] = Kind.PORTAL; lv.arg[partner] = i; lv.hue[partner] = savedPartnerHue
            }
        }
    }
}

/** True when some movable piece solves the board in every one of its states, i.e. turning it never matters. */
private fun hasIndifferentPiece(lv: Level): Boolean {
    for (cell in lv.movable) {
        val saved = lv.state[cell]
        var matters = false
        for (s in 0 until statesOf(lv.kind[cell])) {
            lv.state[cell] = s
            if (!isSolved(lv)) { matters = true; break }
        }
        lv.state[cell] = saved
        if (!matters) return true
    }
    return false
}

private fun solvedByTurningAll(lv: Level): Boolean {
    val all = lv.copy()
    all.movable.forEach { all.tap(it) }
    return isSolved(all)
}

private fun tryBuild(r: Rng, p: Params, minPar: Int, minPieces: Int, strict: Boolean): Level? {
    val lv = Level(p.w, p.h)
    val n = lv.size
    fun free(): Int {
        while (true) {
            val i = r.int(n)
            if (lv.kind[i] == Kind.EMPTY) return i
        }
    }
    fun put(kind: Int, count: Int, states: Int = 1, hue: () -> Int = { 0 }) {
        repeat(count) {
            val i = free()
            lv.kind[i] = kind
            lv.state[i] = if (states > 1) r.int(states) else 0
            lv.hue[i] = hue()
        }
    }

    // Sources sit on the edge and point inward.
    for (c in p.emit) {
        while (true) {
            val side = r.int(4)
            val x = when (side) { 0 -> 0; 2 -> p.w - 1; else -> 1 + r.int(p.w - 2) }
            val y = when (side) { 1 -> 0; 3 -> p.h - 1; else -> 1 + r.int(p.h - 2) }
            val i = y * p.w + x
            if (lv.kind[i] != Kind.EMPTY) continue
            lv.kind[i] = Kind.EMIT; lv.arg[i] = side; lv.hue[i] = c
            break
        }
    }
    put(Kind.WALL, p.walls)
    put(Kind.MIRROR, p.mirrors, 2)
    put(Kind.SPLIT, p.splits, 2)
    put(Kind.ONEWAY, p.oneways, 4)
    put(Kind.FILTER, p.filters) { Hue.PRIMARIES[r.int(3)] }
    repeat(p.portals) { pair ->
        val a = free()
        lv.kind[a] = Kind.PORTAL
        val b = free()
        lv.kind[b] = Kind.PORTAL
        lv.arg[a] = b; lv.arg[b] = a
        lv.hue[a] = pair; lv.hue[b] = pair // pair number, used only to draw matching portals alike
    }

    // The random arrangement is the solution. Targets go on cells its beams cross.
    for (k in 0 until p.targets) {
        val inb = trace(lv).inb
        val fixed = fixedLight(lv)
        var cand = (0 until n).filter { lv.kind[it] == Kind.EMPTY && inb[it] != 0 && fixed[it] == 0 }
        if (cand.isEmpty()) return null
        // The mixed target goes last: placed earlier, later targets would cut its beams.
        if (p.mix && k == p.targets - 1) {
            val mixed = cand.filter { inb[it] !in p.emit }
            if (mixed.isNotEmpty()) cand = mixed
        }
        val i = cand[r.int(cand.size)]
        lv.kind[i] = Kind.TARGET
        lv.hue[i] = inb[i]
    }
    // Targets block beams, so settle their colours on the final board.
    val recv = trace(lv).recv
    for (i in 0 until n) {
        if (lv.kind[i] != Kind.TARGET) continue
        if (recv[i] == 0) return null
        lv.hue[i] = recv[i]
    }

    prune(lv)
    val fixed = fixedLight(lv)
    if ((0 until n).any { lv.kind[it] == Kind.TARGET && fixed[it] != 0 }) return null
    if (strict) {
        if (p.mix && (0 until n).none { lv.kind[it] == Kind.TARGET && lv.hue[it] !in p.emit }) return null
        if (p.need != Kind.EMPTY && p.need !in lv.kind) return null
    }
    if (lv.movable.size < minPieces) return null
    if (hasIndifferentPiece(lv)) return null

    // Start from the scramble farthest from any solution in which no target is lit yet.
    // On boards with four or more pieces some must already be right; otherwise, with nothing idle
    // on the board, "turn every piece once" would solve every level.
    val sols = solutions(lv)
    val radix = IntArray(lv.movable.size) { statesOf(lv.kind[lv.movable[it]]) }
    val minRight = if (radix.size >= 4) maxOf(1, radix.size / 4) else 0
    var start: IntArray? = null
    var par = -1
    repeat(96) {
        val s = IntArray(radix.size) { r.int(radix[it]) }
        val (d, sol) = nearest(lv, sols, s)!!
        if (d > par && s.indices.count { s[it] == sol[it] } >= minRight) {
            lv.setMovableStates(s)
            if (litCount(lv) == 0 && !(radix.size >= 4 && solvedByTurningAll(lv))) { par = d; start = s }
        }
    }
    if (par < minPar) return null
    lv.setMovableStates(start!!)
    lv.par = par
    return lv
}

/**
 * Builds the same solvable board for the same seed, every time.
 * If the recipe proves hard to satisfy, the requirements loosen step by step so generation always ends.
 */
fun generate(seed: Long, p: Params): Level {
    val r = Rng(seed)
    var attempt = 0
    while (true) {
        val relax = attempt / 6000
        val lv = tryBuild(r, p, maxOf(1, p.minPar - relax), maxOf(1, p.minPieces - relax), strict = relax < 8)
        if (lv != null) return lv
        attempt++
    }
}

const val CHAPTERS = 6
const val LEVELS_PER_CHAPTER = 10
const val LEVEL_COUNT = CHAPTERS * LEVELS_PER_CHAPTER

/** Recipe for campaign level [n] (1-based). Each chapter of ten levels introduces one idea. */
fun campaignParams(n: Int): Params {
    val i = (n - 1) % LEVELS_PER_CHAPTER
    val one = Hue.PRIMARIES[n % 3]
    val two = listOf(listOf(1, 2), listOf(1, 4), listOf(2, 4))[n % 3]
    return when ((n - 1) / LEVELS_PER_CHAPTER) {
        0 -> when { // mirrors
            i < 3 -> Params(5, 5, listOf(one), targets = 1, minPar = if (i == 0) 1 else 2, mirrors = 4)
            i < 7 -> Params(5, 6, listOf(one), targets = 1, minPar = 3, mirrors = 7, walls = 1, minPieces = 4)
            else -> Params(6, 6, listOf(one, one), targets = 2, minPar = 3, mirrors = 9, walls = 2, minPieces = 5)
        }
        1 -> when { // colours and mixing
            i < 4 -> Params(5, 6, two, targets = 2, minPar = 3, mirrors = 8, walls = 1, minPieces = 4)
            else -> Params(6, 7, two + two[i % 2], targets = 2, minPar = 4, mirrors = 10, walls = 2, mix = true, minPieces = 6)
        }
        2 -> Params( // splitters
            6, 7, two, targets = 3, minPar = 4, mirrors = 6, splits = 4, walls = 2,
            mix = i >= 3, need = Kind.SPLIT, minPieces = 6,
        )
        3 -> Params( // filters and white light
            6, 8, listOf(Hue.WHITE, one), targets = 3, minPar = 4, mirrors = 8, splits = 3, filters = 3, walls = 3,
            mix = true, need = Kind.FILTER, minPieces = 6,
        )
        4 -> Params( // portals
            6, 8, if (i < 5) two else two + two[i % 2], targets = 3, minPar = 5, mirrors = 8, splits = 3,
            portals = if (i < 5) 1 else 2, walls = 3, mix = i >= 5, need = Kind.PORTAL, minPieces = 7,
        )
        else -> when { // one-sided mirrors
            i < 3 -> Params(6, 7, two, targets = 2, minPar = 5, mirrors = 4, oneways = 4, walls = 2, need = Kind.ONEWAY, minPieces = 5)
            else -> Params(
                7, 9, listOf(1, 2, 4), targets = 4, minPar = 7, mirrors = 7, oneways = 4, splits = 3, filters = 2,
                portals = 1, walls = 4, mix = true, need = Kind.ONEWAY, minPieces = 7,
            )
        }
    }
}

fun campaignLevel(n: Int): Level = generate(n * 7919L + 13, campaignParams(n))

/** The daily puzzle for a date written as the number YYYYMMDD. Its flavour rotates through the later chapters. */
fun dailyLevel(dateNum: Int): Level =
    generate(dateNum.toLong(), campaignParams(intArrayOf(36, 46, 57)[dateNum % 3]))

const val DIFFICULTIES = 3

/** Endless puzzle number [index] (0-based) at [difficulty] 0 easy, 1 medium, 2 hard. */
fun endlessLevel(difficulty: Int, index: Int): Level {
    val recipes = arrayOf(intArrayOf(8, 15, 23), intArrayOf(28, 35, 45), intArrayOf(48, 55, 58))[difficulty]
    return generate(1_000_003L * (difficulty + 1) + index * 7919L, campaignParams(recipes[index % 3]))
}
