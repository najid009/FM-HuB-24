package com.fmhub24.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fmhub24.app.presentation.AppRoot
import com.fmhub24.app.ui.FMHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FMHub24App
        setContent { FMHubTheme { AppRoot(app.container) } }
    }
}
