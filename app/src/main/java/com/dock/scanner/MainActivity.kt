package com.dock.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dock.scanner.ui.DockNavHost
import com.dock.scanner.ui.theme.DockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DockTheme {
                // Gives every screen the theme's background and on-surface text color.
                Surface(color = MaterialTheme.colorScheme.surface) {
                    DockNavHost()
                }
            }
        }
    }
}
