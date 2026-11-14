package com.prizma.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn

sealed interface Screen {
    data object Home : Screen
    data class Chapter(val index: Int) : Screen
    data class Game(val id: LevelId) : Screen
    data object Settings : Screen
}

@Composable
fun PrizmaApp(store: Store, sfx: Sfx, version: String, start: Screen = Screen.Home) {
    val stack = remember { mutableStateListOf(start) }
    val back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }
    Box(
        Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = stack.last(),
            modifier = Modifier.widthIn(max = 520.dp), // a phone-width column, centred on tablets
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 24 }) togetherWith fadeOut(tween(120))
            },
            label = "screen",
        ) { screen ->
            val today = dateNum()
            when (screen) {
                Screen.Home -> HomeScreen(
                    store, today,
                    onOpen = { stack.add(Screen.Game(it)) },
                    onChapter = { stack.add(Screen.Chapter(it)) },
                    onSettings = { stack.add(Screen.Settings) },
                )
                is Screen.Chapter -> ChapterScreen(store, screen.index, onBack = { back() }) { stack.add(Screen.Game(it)) }
                is Screen.Game -> GameScreen(
                    screen.id, store, sfx, today, onBack = { back() },
                    onOpen = { stack[stack.lastIndex] = Screen.Game(it) }, // next puzzle replaces this one
                )
                Screen.Settings -> SettingsScreen(store, version, onBack = { back() })
            }
        }
    }
}
