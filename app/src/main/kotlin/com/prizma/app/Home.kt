package com.prizma.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prizma.core.CHAPTERS
import com.prizma.core.DIFFICULTIES
import com.prizma.core.Hue
import com.prizma.core.LEVELS_PER_CHAPTER
import com.prizma.core.LEVEL_COUNT
import java.text.DateFormat

val CHAPTER_NAMES = intArrayOf(
    R.string.chapter_1, R.string.chapter_2, R.string.chapter_3,
    R.string.chapter_4, R.string.chapter_5, R.string.chapter_6,
)
val DIFFICULTY_NAMES = intArrayOf(R.string.easy, R.string.medium, R.string.hard)

fun chapterLevels(chapter: Int): IntRange =
    chapter * LEVELS_PER_CHAPTER + 1..(chapter + 1) * LEVELS_PER_CHAPTER

fun longDate(dateNum: Int): String = DateFormat.getDateInstance(DateFormat.LONG).format(calendarOf(dateNum).time)

/** Back button on the left, title in the middle, optional content on the right. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxWidth()) {
        PButton("‹ " + stringResource(R.string.back), Modifier.align(Alignment.CenterStart), ButtonKind.Ghost, onClick = onBack)
        Txt(title, Modifier.align(Alignment.Center), style = Type.Heading, maxLines = 1)
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun HomeScreen(
    store: Store,
    today: Int,
    onOpen: (LevelId) -> Unit,
    onChapter: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton(stringResource(R.string.settings), Modifier.align(Alignment.End), ButtonKind.Ghost, onClick = onSettings)
            Txt(stringResource(R.string.title), style = Type.Title, maxLines = 1)
            // the three primary beams
            Row(Modifier.widthIn(max = 250.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (hue in Hue.PRIMARIES) {
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(hueColor(hue)))
                }
            }
            Txt(stringResource(R.string.tagline), Modifier.widthIn(max = 320.dp), color = Palette.Muted)
        }

        // Daily puzzle
        val dailyStars = store.stars["d$today"]
        val streak = store.streak(today)
        Row(
            Modifier.fillMaxWidth().pressable { onOpen(LevelId.Daily(today)) }.panel().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.daily_title), style = Type.Heading)
                Txt(longDate(today), style = Type.Small)
                if (streak > 0) Txt(stringResource(R.string.streak, streak), style = Type.Small, color = Palette.Gold)
            }
            if (dailyStars != null) Stars(dailyStars, size = 18.dp)
            else Txt(stringResource(R.string.play) + " ›", style = Type.Button, color = Palette.Gold)
        }

        // Endless
        Column(Modifier.fillMaxWidth().panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.endless_title), style = Type.Heading)
                Txt(stringResource(R.string.endless_desc), style = Type.Small)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in 0 until DIFFICULTIES) {
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .pressable { onOpen(LevelId.Endless(d, store.endless[d])) }
                            .clip(shape).background(Palette.Bg, shape).border(1.dp, Palette.Line, shape)
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Txt(stringResource(DIFFICULTY_NAMES[d]), style = Type.Button)
                        Txt(stringResource(R.string.solved_count, store.endless[d]), style = Type.Small.copy(fontSize = Type.Small.fontSize * 0.86f))
                    }
                }
            }
        }

        // Chapters
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(stringResource(R.string.chapters), Modifier.weight(1f), style = Type.Heading)
            Stars(1, size = 14.dp, modifier = Modifier.width(14.dp).clip(RoundedCornerShape(0.dp)))
            Spacer(Modifier.width(6.dp))
            Txt("${store.campaignStars()} / ${LEVEL_COUNT * 3}", style = Type.Button, color = Palette.Gold)
        }
        for (c in 0 until CHAPTERS) {
            val levels = chapterLevels(c)
            val open = store.unlocked(levels.first)
            val earned = store.campaignStars(levels)
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(enabled = open) { onChapter(c) }
                    .panel(if (earned > 0) Palette.GoldDim else Palette.Line)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Txt("${c + 1}", Modifier.width(22.dp), style = Type.Number.copy(fontSize = Type.Number.fontSize * 1.3f), color = Palette.Gold, align = TextAlign.Center)
                Column(Modifier.weight(1f)) {
                    Txt(stringResource(CHAPTER_NAMES[c]), style = Type.Heading)
                    Txt(
                        if (open) "${levels.first}–${levels.last}" else stringResource(R.string.locked),
                        style = Type.Small,
                    )
                }
                Txt("$earned / ${LEVELS_PER_CHAPTER * 3}", style = Type.Button, color = if (earned > 0) Palette.Gold else Palette.Muted)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ChapterScreen(store: Store, chapter: Int, onBack: () -> Unit, onOpen: (LevelId) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(stringResource(CHAPTER_NAMES[chapter]), onBack)
        Txt(stringResource(R.string.chapter_number, chapter + 1).uppercase(), style = Type.Eyebrow)
        for (row in chapterLevels(chapter).chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) {
                    val earned = store.stars["$n"]
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.9f)
                            .pressable(enabled = store.unlocked(n)) { onOpen(LevelId.Campaign(n)) }
                            .panel(if (earned != null) Palette.GoldDim else Palette.Line),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Txt("$n", style = Type.Number)
                        Stars(earned ?: 0, size = 9.dp)
                    }
                }
            }
        }
    }
}
