package `in`.localrush.partner.feature.splash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.localrush.core.designsystem.theme.LRColors
import `in`.localrush.partner.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Animated "LR" logo on orange; calls [onFinished] after ~1.1 s. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(500)) }
        delay(1100)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(LRColors.Orange), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp).scale(scale.value).alpha(alpha.value).background(Color.White, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("LR", color = LRColors.Orange, fontSize = 44.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alpha(alpha.value))
        }
    }
}
