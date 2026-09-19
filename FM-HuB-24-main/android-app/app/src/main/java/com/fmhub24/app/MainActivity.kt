package com.fmhub24.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.fmhub24.app.ui.navigation.NavGraph
import com.fmhub24.app.ui.theme.FMHub24Theme
import com.fmhub24.app.util.CrashLog
import com.fmhub24.app.util.CrashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Everything a launch needs is inside the try: the system splash, Hilt's graph (built by
        // super.onCreate), and the whole Compose tree. A failure in any of them used to mean
        // "the app opened and vanished"; now it means "the app says what threw".
        try {
            val splashScreen = installSplashScreen()
            super.onCreate(savedInstanceState)

            // Keep splash screen for a bit (Compose splash handles real loading)
            splashScreen.setKeepOnScreenCondition { false }

            setContent {
                FMHub24Theme(darkTheme = true) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        NavGraph(navController = navController)
                    }
                }
            }
        } catch (t: Throwable) {
            CrashLog.record("MainActivity.onCreate", t)
            setContentView(CrashScreen.view(this, t))
        }
    }
}
