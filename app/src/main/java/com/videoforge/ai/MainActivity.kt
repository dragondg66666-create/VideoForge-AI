package com.videoforge.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.videoforge.ai.ui.VideoForgeApp
import com.videoforge.ai.ui.VideoForgeViewModel
import com.videoforge.ai.ui.theme.VideoForgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VideoForgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoForgeTheme {
                VideoForgeApp(viewModel = viewModel)
            }
        }
    }
}
