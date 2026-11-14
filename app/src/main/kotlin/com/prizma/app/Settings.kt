package com.prizma.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
private fun SettingRow(
    title: String,
    desc: String? = null,
    titleColor: Color = Palette.Fg,
    role: Role = Role.Button,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().pressable(role = role, onClick = onClick).panel().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, style = Type.Button.copy(fontSize = Type.Body.fontSize), color = titleColor)
            if (desc != null) Txt(desc, style = Type.Small)
        }
        trailing()
    }
}

@Composable
fun SettingsScreen(store: Store, version: String, onBack: () -> Unit) {
    val clock = rememberFrameMillis()
    val tour = remember { emptyList<Intro>().toMutableStateList() }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(confirmReset) {
        if (confirmReset) {
            delay(4000)
            confirmReset = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopBar(stringResource(R.string.settings), onBack)
            SettingRow(stringResource(R.string.sound), role = Role.Switch, onClick = { store.sound = !store.sound; store.save() }) {
                Toggle(store.sound)
            }
            SettingRow(stringResource(R.string.vibration), role = Role.Switch, onClick = { store.vibration = !store.vibration; store.save() }) {
                Toggle(store.vibration)
            }
            SettingRow(
                stringResource(R.string.color_letters), stringResource(R.string.color_letters_desc), role = Role.Switch,
                onClick = { store.colorLetters = !store.colorLetters; store.save() },
            ) { Toggle(store.colorLetters) }
            SettingRow(stringResource(R.string.how_to_play), stringResource(R.string.how_to_play_desc), onClick = {
                tour.clear()
                tour.addAll(INTROS)
            })
            SettingRow(
                stringResource(if (confirmReset) R.string.reset_confirm else R.string.reset_progress),
                titleColor = Palette.Danger,
                onClick = {
                    if (confirmReset) store.resetProgress()
                    confirmReset = !confirmReset
                },
            )
            Txt(stringResource(R.string.language_note), Modifier.padding(top = 8.dp), style = Type.Small)
            Txt(stringResource(R.string.version, version), style = Type.Small)
        }
        tour.firstOrNull()?.let { intro ->
            IntroCard(intro, clock, colorLetters(store)) { tour.removeAt(0) }
        }
    }
}

/** The colour letters to draw on boards, or null when the setting is off. */
@Composable
fun colorLetters(store: Store): List<String>? =
    if (!store.colorLetters) null
    else listOf(stringResource(R.string.letter_red), stringResource(R.string.letter_green), stringResource(R.string.letter_blue))
