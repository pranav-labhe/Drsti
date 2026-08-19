package com.pranav.drsti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pranav.drsti.ui.app.DrishtiApp
import com.pranav.drsti.ui.app.DrishtiApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceLocator = (application as DrishtiApplication).serviceLocator

        setContent {
            DrishtiApp(serviceLocator = serviceLocator)
        }
    }
}
