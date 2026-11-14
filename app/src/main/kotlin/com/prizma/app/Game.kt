package com.prizma.app

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prizma.core.LEVEL_COUNT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun levelTitle(id: LevelId): String = when (id) {
    is LevelId.Campaign -> stringResource(R.string.level_title, id.n)
    is LevelId.Daily -> stringResource(R.string.daily_title)
    is LevelId.Endless -> stringResource(DIFFICULTY_NAMES[id.difficulty]) + " " + (id.index + 1)
}

/** Builds the board off the main thread, then plays it. */
@Composable
fun GameScreen(id: LevelId, store: Store, sfx: Sfx, today: Int, onBack: () -> Unit, onOpen: (LevelId) -> Unit) {
    val session by produceState<GameSession?>(null, id) {
        value = withContext(Dispatchers.Default) { GameSession(id, id.build()) }
    }
    val current = session
    if (current == null || current.id != id) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TopBar(levelTitle(id), onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Txt(stringResource(R.string.preparing), style = Type.Small)
            }
        }
    } else {
        GameContent(current, store, sfx, today, onBack, onOpen)
    }
}

@Composable
fun GameContent(session: GameSession, store: Store, sfx: Sfx, today: Int, onBack: () -> Unit, onOpen: (LevelId) -> Unit) {
    val clock = rememberFrameMillis()
    val view = LocalView.current
    val id = session.id
    val intros = remember(session) {
        INTROS.filter { !store.seen.containsKey(it.key) && it.applies(session.level) }.toMutableStateList()
    }
    var showWin by remember(session) { mutableStateOf(session.won) }
    LaunchedEffect(session.won) {
        if (session.won && !showWin) {
            delay(700)
            showWin = true
        }
    }
    fun tick() {
        if (store.vibration) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBar(levelTitle(id), onBack)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Txt(stringResource(R.string.taps) + " ", style = Type.Small)
                Txt("${session.taps}", style = Type.Small.copy(fontWeight = FontWeight.SemiBold), color = Palette.Fg)
                Txt(" · " + stringResource(R.string.par) + " ${session.level.par}", style = Type.Small)
            }
            Board(
                session.level, session.trace, clock, Modifier.weight(1f).fillMaxWidth(),
                turns = session.turns, hint = session.hint, hintAt = session.hintAt, sparks = session.sparks,
                letters = colorLetters(store),
            ) { cell ->
                val lit = session.tap(cell, clock.value)
                if (lit >= 0) {
                    tick()
                    if (session.won) {
                        store.record(id, session.earned)
                        sfx.win()
                        if (store.vibration) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } else if (lit > 0) {
                        sfx.lit(com.prizma.core.litCount(session.level, session.trace) - 1)
                    } else {
                        sfx.turn(session.level.state[cell])
                    }
                }
            }
            if (showWin) {
                WinPanel(session, store, today, onBack, onOpen)
            } else {
                Txt(stringResource(R.string.goal), Modifier.fillMaxWidth(), style = Type.Small, align = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton(stringResource(R.string.undo), Modifier.weight(1f), enabled = session.history.isNotEmpty() && !session.won) {
                        session.undo(clock.value); sfx.undo(); tick()
                    }
                    PButton(stringResource(R.string.restart), Modifier.weight(1f), enabled = session.history.isNotEmpty() && !session.won) {
                        session.restart(clock.value); sfx.undo(); tick()
                    }
                    PButton(stringResource(R.string.hint), Modifier.weight(1f), enabled = !session.won) {
                        session.showHint(clock.value); sfx.hint()
                    }
                }
            }
        }
        intros.firstOrNull()?.let { intro ->
            IntroCard(intro, clock, colorLetters(store)) {
                store.markSeen(intro.key)
                intros.removeAt(0)
            }
        }
    }
}

@Composable
private fun WinPanel(session: GameSession, store: Store, today: Int, onBack: () -> Unit, onOpen: (LevelId) -> Unit) {
    val id = session.id
    val context = LocalContext.current
    val earned = session.earned
    // stars pop in one after another
    val pops = remember(session) { List(3) { Animatable(0f) } }
    LaunchedEffect(session) {
        pops.forEachIndexed { k, pop ->
            launch {
                delay(140L * k)
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val next: LevelId? = when (id) {
        is LevelId.Campaign -> if (id.n < LEVEL_COUNT) LevelId.Campaign(id.n + 1) else null
        is LevelId.Endless -> LevelId.Endless(id.difficulty, id.index + 1)
        is LevelId.Daily -> null
    }
    Column(
        Modifier.fillMaxWidth().panel(Palette.GoldDim).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Stars(earned, size = 30.dp, scales = pops.map { it.value })
        Txt(
            if (earned == 3) stringResource(R.string.win_perfect, session.taps)
            else stringResource(R.string.win_more, session.taps, session.level.par),
        )
        val streak = store.streak(today)
        if (id is LevelId.Daily && streak > 0) Txt(stringResource(R.string.streak, streak), style = Type.Small, color = Palette.Gold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PButton(stringResource(R.string.menu), kind = ButtonKind.Ghost, onClick = onBack)
            Box(Modifier.weight(1f))
            if (id is LevelId.Daily) {
                val shareText = stringResource(
                    R.string.share_text, longDate(id.date), "★".repeat(earned) + "☆".repeat(3 - earned), session.taps, streak,
                )
                PButton(stringResource(R.string.share), kind = ButtonKind.Primary) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
            if (next != null) {
                PButton(
                    stringResource(if (id is LevelId.Campaign) R.string.next_level else R.string.next_puzzle),
                    kind = ButtonKind.Primary,
                ) { onOpen(next) }
            }
        }
    }
}
