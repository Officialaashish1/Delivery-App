package `in`.localrush.customer.feature.onboarding.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.theme.LRColors
import `in`.localrush.customer.R
import kotlinx.coroutines.launch

private data class Slide(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int, val body: Int)

/** Three welcome slides with skip; [onDone] persists the flag and continues to location pick. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val slides = listOf(
        Slide(Icons.Outlined.Restaurant, R.string.onb_1_title, R.string.onb_1_body),
        Slide(Icons.Outlined.DeliveryDining, R.string.onb_2_title, R.string.onb_2_body),
        Slide(Icons.Outlined.TrackChanges, R.string.onb_3_title, R.string.onb_3_body),
    )
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_skip)) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val s = slides[page]
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(200.dp).background(LRColors.OrangeContainer, RoundedCornerShape(48.dp)), contentAlignment = Alignment.Center) {
                    Icon(s.icon, null, tint = LRColors.Orange, modifier = Modifier.size(96.dp))
                }
                Spacer(Modifier.height(40.dp))
                Text(stringResource(s.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(s.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 24.dp else 8.dp, label = "dot")
                Box(Modifier.padding(4.dp).height(8.dp).width(w).background(if (pager.currentPage == i) LRColors.Orange else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Spacer(Modifier.height(24.dp))
        LRButton(
            stringResource(if (pager.currentPage == slides.lastIndex) R.string.action_get_started else R.string.action_next),
            onClick = { if (pager.currentPage == slides.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

