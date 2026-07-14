package com.tuananh.bothost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuananh.bothost.ui.BotHostApp
import com.tuananh.bothost.ui.theme.BotHostTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BotHostTheme {
                val vm: MainViewModel = viewModel()
                BotHostApp(vm)
            }
        }
    }
}
