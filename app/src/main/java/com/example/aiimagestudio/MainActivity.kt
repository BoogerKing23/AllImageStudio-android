package com.example.aiimagestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.aiimagestudio.ui.ChatScreen
import com.example.aiimagestudio.ui.theme.AIImageStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIImageStudioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen()
                }
            }
        }
    }
}
