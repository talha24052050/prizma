package com.prizma.app

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prizma.core.DIFFICULTIES
import com.prizma.core.LEVEL_COUNT
import com.prizma.core.Level
import com.prizma.core.campaignLevel
import com.prizma.core.dailyLevel
import com.prizma.core.endlessLevel
import java.util.Calendar

/** Which puzzle a game screen plays. [key] is the name its stars are saved under. */
sealed interface LevelId {
    val key: String
    fun build(): Level

    data class Campaign(val n: Int) : LevelId {
        override val key get() = "$n"
        override fun build() = campaignLevel(n)
    }

    /** [date] is written as the number YYYYMMDD. */
    data class Daily(val date: Int) : LevelId {
        override val key get() = "d$date"
        override fun build() = dailyLevel(date)
    }

    data class Endless(val difficulty: Int, val index: Int) : LevelId {
        override val key get() = "e$difficulty-$index"
        override fun build() = endlessLevel(difficulty, index)
    }
}

fun dateNum(cal: Calendar = Calendar.getInstance()): Int =
    cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)

fun calendarOf(dateNum: Int): Calendar = Calendar.getInstance().apply {
    clear()
    set(dateNum / 10000, dateNum / 100 % 100 - 1, dateNum % 100)
}

fun previousDay(dateNum: Int): Int = dateNum(calendarOf(dateNum).apply { add(Calendar.DAY_OF_MONTH, -1) })

/**
 * Player progress and settings, observable by Compose and saved to [prefs] on every change.
 * Pass null to keep everything in memory (tests).
 */
class Store(private val prefs: SharedPreferences?) {
    /** Best stars per finished puzzle, by [LevelId.key]. Endless puzzles are counted, not listed. */
    val stars = mutableStateMapOf<String, Int>()

    /** Keys of the introduction cards already shown. */
    val seen = mutableStateMapOf<String, Boolean>()

    /** Endless puzzles solved per difficulty; also the index of the next one. */
    val endless = mutableStateListOf<Int>().apply { repeat(DIFFICULTIES) { add(0) } }

    var sound by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var colorLetters by mutableStateOf(false)

    init {
        prefs?.let { p ->
            p.getString("stars", "")!!.split(',').filter { ':' in it }.forEach {
                stars[it.substringBefore(':')] = it.substringAfter(':').toIntOrNull() ?: 0
            }
            p.getString("seen", "")!!.split(',').filter { it.isNotEmpty() }.forEach { seen[it] = true }
            p.getString("endless", "")!!.split(',').mapNotNull { it.toIntOrNull() }
                .forEachIndexed { d, n -> if (d < endless.size) endless[d] = n }
            sound = p.getBoolean("sound", true)
            vibration = p.getBoolean("vibration", true)
            colorLetters = p.getBoolean("colorLetters", false)
        }
    }

    fun save() {
        prefs?.edit()
            ?.putString("stars", stars.entries.joinToString(",") { "${it.key}:${it.value}" })
            ?.putString("seen", seen.keys.joinToString(","))
            ?.putString("endless", endless.joinToString(","))
            ?.putBoolean("sound", sound)
            ?.putBoolean("vibration", vibration)
            ?.putBoolean("colorLetters", colorLetters)
            ?.apply()
    }

    /** Records a finished puzzle, keeping the best result. */
    fun record(id: LevelId, earned: Int) {
        when (id) {
            is LevelId.Endless -> if (id.index >= endless[id.difficulty]) endless[id.difficulty] = id.index + 1
            else -> if (earned > (stars[id.key] ?: 0)) stars[id.key] = earned
        }
        save()
    }

    fun markSeen(key: String) {
        seen[key] = true
        save()
    }

    fun resetProgress() {
        stars.clear()
        seen.clear()
        for (d in endless.indices) endless[d] = 0
        save()
    }

    /** Highest campaign level finished so far, 0 if none. */
    val highestDone: Int get() = (LEVEL_COUNT downTo 1).firstOrNull { stars.containsKey("$it") } ?: 0

    /** Levels open up to three past the highest finished one, so a hard level can be skipped. */
    fun unlocked(n: Int): Boolean = n <= highestDone + 3

    fun campaignStars(range: IntRange = 1..LEVEL_COUNT): Int = range.sumOf { stars["$it"] ?: 0 }

    /** Days in a row with a solved daily puzzle, counting today if done and otherwise up to yesterday. */
    fun streak(today: Int): Int {
        var day = if (stars.containsKey("d$today")) today else previousDay(today)
        var n = 0
        while (stars.containsKey("d$day")) {
            n++
            day = previousDay(day)
        }
        return n
    }
}
