package com.blackvueeventos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.blackvueeventos.app.ui.BlackvueTheme
import com.blackvueeventos.app.ui.HomeScreen
import com.blackvueeventos.app.ui.LiveScreen
import com.blackvueeventos.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf("home") }
            BlackvueTheme {
                BackHandler(enabled = screen != "home") {
                    if (screen == "settings") viewModel.flushSettings()
                    screen = "home"
                }
                when (screen) {
                    "settings" -> SettingsScreen(viewModel, onBack = { screen = "home" })
                    "live" -> LiveScreen(viewModel, onBack = { screen = "home" })
                    else -> HomeScreen(
                        viewModel,
                        onSettings = { screen = "settings" },
                        onLive = { screen = "live" },
                    )
                }
            }
        }
    }

    override fun onStop() {
        viewModel.flushSettings()
        super.onStop()
    }
}
