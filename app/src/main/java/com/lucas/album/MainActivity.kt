package com.lucas.album

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lucas.album.ui.AlbumApp
import com.lucas.album.ui.theme.AlbumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = (application as AlbumApplication).container

        setContent {
            AlbumTheme {
                AlbumApp(container = container)
            }
        }
    }
}
