package `in`.localrush.customer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * True only while rendering screenshots on the JVM (Paparazzi/layoutlib), where MapLibre has no GL surface:
 * map composables then draw [MapPlaceholder] instead of `LRMap`. Never provided by the app itself.
 */
val LocalMapPlaceholder = staticCompositionLocalOf { false }

/** Flat stand-in for the map area in screenshot tests. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFFE3E8EE)), contentAlignment = Alignment.Center) {
        Text("Map", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A94A6))
    }
}
