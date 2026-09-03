package com.example.toolkit

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.toolkit.ui.NexusApp
import com.example.toolkit.ui.lock.AppLockGate
import com.example.toolkit.ui.theme.ToolkitTheme
import com.example.toolkit.ui.theme.VoidBlack

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots and hide captured traffic/credentials from the
        // recent-apps thumbnail and screen recordings.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            ToolkitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VoidBlack
                ) {
                    AppLockGate {
                        NexusApp()
                    }
                }
            }
        }
    }
}
