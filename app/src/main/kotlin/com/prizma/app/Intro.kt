package com.prizma.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prizma.core.Hue
import com.prizma.core.Kind
import com.prizma.core.Level
import com.prizma.core.trace
import kotlinx.coroutines.delay

/**
 * A short card shown the first time a level contains something new.
 *
 * @property applies whether a level contains what the card explains
 * @property flips cells of the demo board that are tapped again and again to show the piece working
 */
class Intro(
    val key: String,
    val title: Int,
    val text: Int,
    val applies: (Level) -> Boolean,
    val flips: IntArray,
    val demo: () -> Level,
)

private class DemoBuilder(val lv: Level) {
    fun emit(i: Int, dir: Int, hue: Int) { lv.kind[i] = Kind.EMIT; lv.arg[i] = dir; lv.hue[i] = hue }
    fun piece(i: Int, kind: Int, state: Int = 0) { lv.kind[i] = kind; lv.state[i] = state }
    fun filter(i: Int, hue: Int) { lv.kind[i] = Kind.FILTER; lv.hue[i] = hue }
    fun target(i: Int, hue: Int) { lv.kind[i] = Kind.TARGET; lv.hue[i] = hue }
    fun portals(a: Int, b: Int) {
        lv.kind[a] = Kind.PORTAL; lv.kind[b] = Kind.PORTAL
        lv.arg[a] = b; lv.arg[b] = a
    }
}

private fun demo(w: Int, h: Int, build: DemoBuilder.() -> Unit): Level = Level(w, h).also { DemoBuilder(it).build() }

private fun Level.has(kind: Int) = kind in this.kind

val INTROS = listOf(
    Intro("mirror", R.string.intro_mirror_title, R.string.intro_mirror_text, { true }, intArrayOf(5)) {
        demo(3, 2) { target(2, Hue.RED); emit(3, 0, Hue.RED); piece(5, Kind.MIRROR) }
    },
    Intro(
        "color", R.string.intro_color_title, R.string.intro_color_text,
        { lv -> lv.kind.count { it == Kind.EMIT } > 1 }, intArrayOf(1, 7),
    ) {
        demo(3, 3) {
            emit(0, 0, Hue.RED); piece(1, Kind.MIRROR); target(4, Hue.BLUE)
            emit(6, 0, Hue.BLUE); piece(7, Kind.MIRROR)
        }
    },
    Intro(
        "mix", R.string.intro_mix_title, R.string.intro_mix_text,
        { lv -> (0 until lv.size).any { lv.kind[it] == Kind.TARGET && lv.hue[it] !in Hue.PRIMARIES } }, intArrayOf(7),
    ) {
        demo(3, 3) {
            emit(0, 0, Hue.RED); piece(1, Kind.MIRROR, 1); target(4, Hue.RED or Hue.GREEN)
            emit(6, 0, Hue.GREEN); piece(7, Kind.MIRROR)
        }
    },
    Intro("split", R.string.intro_split_title, R.string.intro_split_text, { it.has(Kind.SPLIT) }, intArrayOf(4)) {
        demo(3, 2) { target(1, Hue.RED); emit(3, 0, Hue.RED); piece(4, Kind.SPLIT); target(5, Hue.RED) }
    },
    Intro("filter", R.string.intro_filter_title, R.string.intro_filter_text, { it.has(Kind.FILTER) }, intArrayOf()) {
        demo(4, 1) { emit(0, 0, Hue.WHITE); filter(1, Hue.RED); target(3, Hue.RED) }
    },
    Intro("portal", R.string.intro_portal_title, R.string.intro_portal_text, { it.has(Kind.PORTAL) }, intArrayOf()) {
        demo(4, 2) { emit(0, 0, Hue.GREEN); portals(1, 6); target(7, Hue.GREEN) }
    },
    Intro("oneway", R.string.intro_oneway_title, R.string.intro_oneway_text, { it.has(Kind.ONEWAY) }, intArrayOf(4)) {
        demo(3, 3) { target(1, Hue.BLUE); emit(3, 0, Hue.BLUE); piece(4, Kind.ONEWAY) }
    },
)

@Composable
fun IntroCard(intro: Intro, clock: State<Long>, letters: List<String>?, onDone: () -> Unit) {
    val level = remember(intro) { intro.demo() }
    var tr by remember(intro) { mutableStateOf(trace(level)) }
    val turns = remember(intro) { HashMap<Int, Turn>() }
    LaunchedEffect(intro) {
        while (intro.flips.isNotEmpty()) {
            delay(1300)
            for (cell in intro.flips) {
                level.tap(cell)
                turns[cell] = Turn(clock.value, back = false)
            }
            tr = trace(level)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg.copy(alpha = 0.9f))
            .pressable(role = null) {} // swallow touches meant for the board underneath
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().panel().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt(stringResource(R.string.new_piece).uppercase(), style = Type.Eyebrow)
            Txt(stringResource(intro.title), style = Type.Heading.copy(fontSize = 22.sp, lineHeight = 28.sp), align = TextAlign.Center)
            Board(
                level, tr, clock, Modifier.fillMaxWidth().height(60.dp * level.h),
                turns = turns, letters = letters, maxCell = 60.dp,
            )
            Txt(stringResource(intro.text), align = TextAlign.Center)
            PButton(stringResource(R.string.got_it), Modifier.fillMaxWidth(), ButtonKind.Primary, onClick = onDone)
        }
    }
}
