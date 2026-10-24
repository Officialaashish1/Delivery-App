package `in`.localrush.rider

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import `in`.localrush.core.designsystem.theme.LocalRushTheme
import `in`.localrush.rider.navigation.AppNavHost
import `in`.localrush.rider.navigation.DeepLinkHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinks: DeepLinkHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            LocalRushTheme {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Offer full-screen intents must show over the lock screen and wake the device. */
    private fun handleIntent(intent: Intent?) {
        deepLinks.offer(intent)
        val isOffer = intent?.data?.host == "offer"
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(isOffer)
            setTurnScreenOn(isOffer)
        }
    }
}
