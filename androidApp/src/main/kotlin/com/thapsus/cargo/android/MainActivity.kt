package com.thapsus.cargo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thapsus.cargo.android.ui.RootScreen
import com.thapsus.cargo.android.ui.theme.ThapsusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ThapsusTheme {
                RootScreen()
            }
        }
    }
}
