package `in`.localrush.rider.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import `in`.localrush.rider.R
import `in`.localrush.rider.feature.dashboard.ui.DashboardScreen
import `in`.localrush.rider.feature.earnings.ui.EarningsScreen
import `in`.localrush.rider.feature.history.ui.HistoryScreen
import `in`.localrush.rider.feature.onboarding.vm.RegStep
import `in`.localrush.rider.feature.profile.ui.ProfileScreen

private enum class Tab(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    EARNINGS(R.string.tab_earnings, Icons.Filled.Payments),
    ORDERS(R.string.tab_orders, Icons.Filled.Receipt),
    PROFILE(R.string.tab_profile, Icons.Filled.Person),
}

/** Bottom-tab shell for approved riders: Home · Earnings · Orders · Profile. */
@Composable
fun MainShell(
    onOpenDelivery: (String) -> Unit,
    onOpenOffer: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenPayouts: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenSupport: () -> Unit,
    onEdit: (RegStep) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, contentDescription = null) }, label = { Text(stringResource(t.labelRes)) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (Tab.entries[tab]) {
                Tab.HOME -> DashboardScreen(onOpenDelivery = onOpenDelivery, onOpenOffer = onOpenOffer, onOpenEarnings = { tab = Tab.EARNINGS.ordinal })
                Tab.EARNINGS -> EarningsScreen(onOpenPayouts = onOpenPayouts)
                Tab.ORDERS -> HistoryScreen(onOpenOrder = onOpenOrder)
                Tab.PROFILE -> ProfileScreen(onEdit = onEdit, onDocuments = onOpenDocuments, onPayouts = onOpenPayouts, onSupport = onOpenSupport)
            }
        }
    }
}
