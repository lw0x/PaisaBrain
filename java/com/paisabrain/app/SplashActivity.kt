package com.paisabrain.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.paisabrain.app.security.AppSecurityGuard
import com.paisabrain.app.ui.theme.NavyDark
import com.paisabrain.app.ui.theme.GoldSecondary
import com.paisabrain.app.ui.theme.OnNavy
import com.paisabrain.app.ui.theme.PaisaBrainTheme

/**
 * SplashActivity — Quick branded splash screen.
 *
 * - Android 12+: Uses native SplashScreen API (instant, smooth)
 * - Android 6-11: Shows a simple branded screen for 1.2 seconds
 * - Performs background security check during splash
 * - Routes to MainActivity after splash completes
 * - Maximum duration: 1.5 seconds (never blocks the user)
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ native splash screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen()
        }

        super.onCreate(savedInstanceState)

        // Background security check (non-blocking)
        Thread {
            AppSecurityGuard.performSecurityCheck(applicationContext)
        }.start()

        // For Android 6-11: show our custom splash
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            setContent {
                PaisaBrainSplash()
            }
        }

        // Navigate to main after 1.2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            // No animation for seamless transition
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }, 1200)
    }
}

@Composable
private fun PaisaBrainSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brain + Rupee emoji as logo placeholder
            Text(
                text = "🧠",
                fontSize = 72.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Paisa Brain",
                color = OnNavy,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Remembers everything. Tells no one.",
                color = GoldSecondary,
                fontSize = 14.sp
            )
        }
    }
}
