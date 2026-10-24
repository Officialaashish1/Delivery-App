package `in`.localrush.customer.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.PriceText
import `in`.localrush.core.designsystem.component.ShimmerBox
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.component.shimmer
import `in`.localrush.core.designsystem.icon.LRIcons
import `in`.localrush.core.designsystem.theme.LRColors
import `in`.localrush.customer.R
import `in`.localrush.customer.data.dto.BannerDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.feature.home.vm.HomeRow
import `in`.localrush.customer.feature.home.vm.HomeSectionMapper
import `in`.localrush.customer.feature.home.vm.HomeViewModel
import `in`.localrush.customer.ui.common.RestaurantCard
import `in`.localrush.customer.ui.common.SectionHeader
import kotlinx.coroutines.delay

class HomeActions(
    val onPickLocation: () -> Unit,
    val onNotifications: () -> Unit,
    val onSearch: () -> Unit,
    val onCategory: (slug: String, name: String) -> Unit,
    val onCuisine: (slug: String, name: String) -> Unit,
    val onNearby: () -> Unit,
    val onSection: (key: String, title: String) -> Unit,
    val onRestaurant: (slug: String) -> Unit,
    val onItem: (restaurantSlug: String, itemId: String) -> Unit,
    val onBanner: (BannerDto) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recentTitle = stringResource(R.string.home_recently_ordered)
    val recommendedTitle = stringResource(R.string.home_recommended)
    val rows = state.home?.let { HomeSectionMapper.map(it, state.loggedIn, recentTitle, recommendedTitle) } ?: emptyList()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OrangeHeader(state.location?.title, state.location?.line, state.unread, state.userName, actions.onPickLocation, actions.onNotifications, actions.onSearch)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> HomeSkeleton()
                state.showError -> ErrorState(state.error!!, onRetry = viewModel::refresh)
                HomeSectionMapper.isEmpty(rows) -> EmptyState(
                    title = stringResource(R.string.home_empty_title), description = stringResource(R.string.home_empty_body),
                    icon = Icons.Outlined.Place, actionText = stringResource(R.string.home_change_location), onAction = actions.onPickLocation,
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (state.isStale) item { InlineError(stringResource(R.string.home_stale, state.error?.message ?: ""), Modifier.padding(16.dp)) }
                    rows.forEach { row ->
                        when (row) {
                            is HomeRow.Banners -> item(key = "banners") { BannerCarousel(row.banners, actions.onBanner) }
                            is HomeRow.Categories -> item(key = "categories") {
                                SectionHeader(stringResource(R.string.home_whats_on_your_mind), Modifier.padding(top = 20.dp, bottom = 10.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(row.categories, key = { it.id }) { c ->
                                        Column(Modifier.width(76.dp).clickable { actions.onCategory(c.slug, c.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                            LRImage(c.image, c.name, Modifier.size(76.dp), radius = 20.dp)
                                            Spacer(Modifier.height(6.dp))
                                            Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            HomeRow.NearbyPromo -> item(key = "nearby") { NearbyBanner(actions.onNearby) }
                            is HomeRow.Restaurants -> item(key = "section-${row.key}") {
                                SectionHeader(row.title, Modifier.padding(top = 20.dp, bottom = 10.dp), action = stringResource(R.string.action_see_all), onAction = { actions.onSection(row.key, row.title) })
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(row.restaurants, key = { it.id }) { r -> RestaurantCard(r, onClick = { actions.onRestaurant(r.slug) }) }
                                }
                            }
                            is HomeRow.RecommendedItems -> item(key = "recommended") {
                                SectionHeader(recommendedTitle, Modifier.padding(top = 20.dp, bottom = 10.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(row.items, key = { it.id }) { item -> RecommendedItemCard(item) { actions.onItem(item.restaurantSlug, item.id) } }
                                }
                            }
                            is HomeRow.Cuisines -> item(key = "cuisines") {
                                SectionHeader(stringResource(R.string.home_cuisines), Modifier.padding(top = 20.dp, bottom = 10.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(row.cuisines, key = { it.id }) { c ->
                                        Row(
                                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { actions.onCuisine(c.slug, c.name) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (!c.icon.isNullOrBlank()) { LRImage(c.icon, null, Modifier.size(20.dp), radius = 10.dp); Spacer(Modifier.width(6.dp)) }
                                            Text(c.name, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrangeHeader(title: String?, line: String?, unread: Int, userName: String?, onPickLocation: () -> Unit, onNotifications: () -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(LRColors.Orange, LRColors.OrangeDark))).statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onPickLocation)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(title?.ifBlank { null } ?: stringResource(R.string.home_your_location), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                }
                Text(line?.ifBlank { null } ?: stringResource(R.string.home_set_location), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 22.dp))
            }
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                    Icon(LRIcons.Bell, stringResource(R.string.notifications_title), tint = Color.White)
                }
            }
        }
        if (userName != null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.home_welcome, userName), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(end = 8.dp).height(50.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onSearch).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LRIcons.Search, null, tint = LRColors.Orange)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.home_search_hint), color = LRColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BannerCarousel(banners: List<BannerDto>, onBanner: (BannerDto) -> Unit) {
    val pager = rememberPagerState { banners.size }
    LaunchedEffect(banners.size) {
        if (banners.size > 1) while (true) { delay(4000); pager.animateScrollToPage((pager.currentPage + 1) % banners.size) }
    }
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { page ->
            val b = banners[page]
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).clickable { onBanner(b) }) {
                LRImage(b.image, b.title, Modifier.fillMaxSize(), radius = 20.dp)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                Column(Modifier.align(Alignment.CenterStart).padding(16.dp).fillMaxWidth(0.7f)) {
                    Text(b.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.subtitle.isNotBlank()) Text(b.subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.couponCode.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.home_use_code, b.couponCode), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.background(LRColors.Orange, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
        if (banners.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(banners.size) { i -> Box(Modifier.padding(3.dp).size(if (pager.currentPage == i) 8.dp else 6.dp).background(if (pager.currentPage == i) LRColors.Orange else MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
            }
        }
    }
}

@Composable
private fun NearbyBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp).background(LRColors.OrangeContainer, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_find_nearby), style = MaterialTheme.typography.titleMedium, color = LRColors.OrangeDark)
            Text(stringResource(R.string.home_find_nearby_body), style = MaterialTheme.typography.bodySmall, color = LRColors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            LRButton(stringResource(R.string.home_see_location), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.height(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Icon(LRIcons.RestaurantPin, null, tint = LRColors.Orange, modifier = Modifier.size(64.dp))
    }
}

@Composable
private fun RecommendedItemCard(item: MenuItemDto, onClick: () -> Unit) {
    LRCard(Modifier.width(160.dp), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        LRImage(item.image, item.name, Modifier.fillMaxWidth().height(100.dp), radius = 0.dp)
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { VegNonVegDot(item.foodType, size = 12.dp); Spacer(Modifier.width(6.dp)); Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(item.restaurantName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            PriceText(item.effectivePrice.toMoney(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.fillMaxWidth().height(150.dp).shimmer(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(24.dp))
        ShimmerBox(height = 18.dp, width = 180.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(4) { Spacer(Modifier.size(76.dp).shimmer(RoundedCornerShape(20.dp))) } }
        Spacer(Modifier.height(24.dp))
        ShimmerBox(height = 18.dp, width = 140.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(2) { Spacer(Modifier.width(220.dp).height(200.dp).shimmer(RoundedCornerShape(16.dp))) } }
    }
}
