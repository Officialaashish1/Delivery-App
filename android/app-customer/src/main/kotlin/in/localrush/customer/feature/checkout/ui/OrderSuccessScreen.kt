package `in`.localrush.customer.feature.checkout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.orders.vm.OrderDetailViewModel

/** Animated tick + order number + ETA, then Track / Home. Reuses OrderDetailViewModel (same route arg name). */
@Composable
fun OrderSuccessScreen(onTrack: (String) -> Unit, onHome: () -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
    val order = state.order
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(120.dp).scale(scale.value).background(LRTheme.colors.success, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.success_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (order != null) stringResource(R.string.success_order_number, order.orderNumber) else stringResource(R.string.success_body),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        order?.estimatedDeliveryAt.toInstantOrNull()?.let {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.success_eta, DateTimeUtils.formatTime(it)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(40.dp))
        LRButton(stringResource(R.string.success_track), onClick = { onTrack(viewModel.orderId) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        LRButton(stringResource(R.string.success_home), onClick = onHome, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined)
    }
}
