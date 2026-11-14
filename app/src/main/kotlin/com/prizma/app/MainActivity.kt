package com.prizma.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private var sfx: Sfx? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val store = Store(getSharedPreferences("prizma", Context.MODE_PRIVATE))
        val sound = Sfx { store.sound }.also { sfx = it }
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent { PrizmaApp(store, sound, version) }
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx?.release()
    }
}
