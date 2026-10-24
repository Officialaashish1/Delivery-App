package `in`.localrush.customer.feature.restaurant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.RatingBadge
import `in`.localrush.core.designsystem.component.RatingBar
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.component.shimmer
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.customer.R
import `in`.localrush.customer.data.dto.RestaurantDetailDto
import `in`.localrush.customer.feature.restaurant.vm.GroupError
import `in`.localrush.customer.feature.restaurant.vm.ItemSheetState
import `in`.localrush.customer.feature.restaurant.vm.RestaurantEvent
import `in`.localrush.customer.feature.restaurant.vm.RestaurantViewModel
import `in`.localrush.customer.ui.common.LoginRequiredDialog
import `in`.localrush.customer.ui.common.MenuItemRow
import `in`.localrush.customer.ui.common.QuantityStepper
import `in`.localrush.customer.ui.common.rating
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantScreen(onBack: () -> Unit, onCart: () -> Unit, onLogin: () -> Unit, viewModel: RestaurantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var loginPrompt by remember { mutableStateOf(false) }
    var reviewsOpen by remember { mutableStateOf(false) }
    val addedMsg = stringResource(R.string.cart_added)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                RestaurantEvent.LoginRequired -> loginPrompt = true
                is RestaurantEvent.Message -> snackbar.showSnackbar(e.text)
                RestaurantEvent.Added -> snackbar.showSnackbar(addedMsg)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.loading -> Column { Spacer(Modifier.fillMaxWidth().height(220.dp).shimmer(RoundedCornerShape(0.dp))); Spacer(Modifier.height(24.dp)); ShimmerListPlaceholder(5, thumbnail = 110.dp) }
            state.error != null -> Column { Spacer(Modifier.statusBarsPadding()); IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }; ErrorState(state.error!!, onRetry = viewModel::load) }
            state.restaurant != null -> Content(state.restaurant!!, state, viewModel, onBack, onReviews = { reviewsOpen = true; viewModel.loadReviews() })
        }
        // Floating "View cart" bar for this restaurant.
        val cart = state.cart
        AnimatedVisibility(cart != null && !cart.isEmpty && state.cartForThisRestaurant, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Surface(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onCart), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.cart_items_count, cart?.itemCount ?: 0, cart?.itemCount ?: 0), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text(cart?.pricing?.itemTotal?.toMoney()?.format() ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.cart_view), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ShoppingBag, null, tint = Color.White)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    state.sheet?.let { sheet -> ItemSheet(sheet, viewModel) }
    state.conflict?.let { c ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text(stringResource(R.string.cart_replace_title)) },
            text = { Text(stringResource(R.string.cart_replace_body, c.currentRestaurant ?: stringResource(R.string.cart_another_restaurant))) },
            confirmButton = { TextButton(onClick = viewModel::confirmReplaceCart) { Text(stringResource(R.string.cart_replace_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConflict) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (loginPrompt) LoginRequiredDialog(onLogin = { loginPrompt = false; onLogin() }, onDismiss = { loginPrompt = false })
    if (reviewsOpen) {
        LRBottomSheet(onDismiss = { reviewsOpen = false }, title = stringResource(R.string.reviews_title)) {
            when {
                state.reviewsLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.reviewsError != null -> ErrorState(state.reviewsError!!, onRetry = viewModel::loadReviews, modifier = Modifier.height(260.dp))
                state.reviews?.results.isNullOrEmpty() -> EmptyState(stringResource(R.string.reviews_empty), modifier = Modifier.height(260.dp))
                else -> LazyColumn(Modifier.fillMaxWidth().height(480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.reviews!!.results, key = { it.id }) { r ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.customerName.ifBlank { stringResource(R.string.reviews_anonymous) }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                RatingBar(r.restaurantRating.toFloat(), starSize = 14.dp)
                            }
                            r.createdAt.toInstantOrNull()?.let { Text(DateTimeUtils.formatDate(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (r.restaurantComment.isNotBlank()) Text(r.restaurantComment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            if (!r.partnerReply.isNullOrBlank()) {
                                Text(stringResource(R.string.reviews_partner_reply, r.partnerReply!!), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(8.dp))
                            }
                            HorizontalDivider(Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Content(r: RestaurantDetailDto, state: `in`.localrush.customer.feature.restaurant.vm.RestaurantUiState, vm: RestaurantViewModel, onBack: () -> Unit, onReviews: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val categories = r.menu.filter { it.items.isNotEmpty() }
    // Index of the first list item of each category (header rows). Layout: [0]=cover, [1]=info, [2]=sticky chips, then per category: header + items.
    val categoryStart = remember(categories) {
        var idx = 3
        categories.map { c -> idx.also { idx += 1 + c.items.size } }
    }
    val activeCategory by remember { derivedStateOf { val first = listState.firstVisibleItemIndex; categoryStart.indexOfLast { it <= first }.coerceAtLeast(0) } }
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }

    Box {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "cover") {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    LRImage(r.coverImage ?: r.logo, r.name, Modifier.fillMaxSize(), radius = 0.dp)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Black.copy(alpha = 0.3f)))))
                    LRImage(r.logo, null, Modifier.align(Alignment.BottomStart).padding(16.dp).size(72.dp).border(3.dp, Color.White, RoundedCornerShape(18.dp)), radius = 18.dp)
                }
            }
            item(key = "info") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.isPureVeg) { VegNonVegDot("veg"); Spacer(Modifier.width(8.dp)) }
                        Text(r.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        if (!r.isOpenNow) StatusChip(stringResource(R.string.restaurant_closed), ChipTone.Error)
                    }
                    Text(r.cuisines.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clickable(onClick = onReviews), verticalAlignment = Alignment.CenterVertically) {
                        RatingBadge(r.ratingAvg.rating())
                        Spacer(Modifier.width(8.dp))
                        Text(pluralStringResource(R.plurals.reviews_count, r.ratingCount, r.ratingCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" " + stringResource(R.string.restaurant_eta_min, r.deliveryTimeMin), style = MaterialTheme.typography.bodyMedium)
                        r.distanceKm?.let { Text("  ·  " + Geo.formatDistanceKm(it), style = MaterialTheme.typography.bodyMedium) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoCell(stringResource(R.string.restaurant_info_fee), r.deliveryFee.toMoney().let { if (it.isZero) stringResource(R.string.restaurant_free) else it.format() })
                        InfoCell(stringResource(R.string.restaurant_info_min_order), r.minOrderAmount.toMoney().format())
                        InfoCell(stringResource(R.string.restaurant_info_cost_for_two), r.costForTwo.toMoney().format())
                        InfoCell(stringResource(R.string.restaurant_info_hours), todayHours(r))
                    }
                    if (!r.offerText.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(r.offerText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(listOf(r.addressLine1, r.addressLine2, r.landmark, r.city).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            stickyHeader(key = "chips") {
                if (categories.isNotEmpty()) {
                    LazyRow(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories.size) { i ->
                            FilterChip(selected = activeCategory == i, onClick = { scope.launch { listState.animateScrollToItem(categoryStart[i]) } }, label = { Text(categories[i].name) })
                        }
                    }
                }
            }
            if (categories.isEmpty()) item { EmptyState(stringResource(R.string.menu_empty_title), description = stringResource(R.string.menu_empty_body), icon = Icons.Outlined.RestaurantMenu, modifier = Modifier.height(320.dp)) }
            categories.forEach { cat ->
                item(key = "cat-${cat.id}") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                        Text("${cat.name} (${cat.items.size})", style = MaterialTheme.typography.titleMedium)
                        if (cat.description.isNotBlank()) Text(cat.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(cat.items, key = { it.id }) { item ->
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        MenuItemRow(item, quantityInCart = state.quantityOf(item.id), onAdd = { vm.quickAdd(item) }, onIncrement = { vm.increment(item) }, onDecrement = { vm.decrement(item) }, onClick = { vm.openItem(item) })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        // Back button / collapsed title bar.
        Surface(Modifier.fillMaxWidth(), color = if (collapsed) MaterialTheme.colorScheme.surface else Color.Transparent, shadowElevation = if (collapsed) 4.dp else 0.dp) {
            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(if (collapsed) Color.Transparent else Color.White.copy(alpha = 0.9f), CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onSurface)
                }
                if (collapsed) { Spacer(Modifier.width(8.dp)); Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun todayHours(r: RestaurantDetailDto): String {
    val weekday = (java.time.LocalDate.now().dayOfWeek.value + 6) % 7 // Monday=0 like Python's weekday()
    val h = r.hours.firstOrNull { it.weekday == weekday } ?: return "—"
    if (h.isClosed) return stringResource(R.string.restaurant_closed_today)
    val open = DateTimeUtils.parseTime(h.openTime)?.let { DateTimeUtils.formatTime(it) } ?: return "—"
    val close = DateTimeUtils.parseTime(h.closeTime)?.let { DateTimeUtils.formatTime(it) } ?: return "—"
    return "$open – $close"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(sheet: ItemSheetState, vm: RestaurantViewModel) {
    val item = sheet.item
    LRBottomSheet(onDismiss = vm::closeItem, showClose = true) {
        LazyColumn(Modifier.fillMaxWidth().heightInSheet(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (!item.image.isNullOrBlank()) LRImage(item.image, item.name, Modifier.fillMaxWidth().height(180.dp), radius = 16.dp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { VegNonVegDot(item.foodType); Spacer(Modifier.width(8.dp)); Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)) }
                if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.effectivePrice.toMoney().format(), style = MaterialTheme.typography.titleMedium)
                    if (item.ratingCount > 0) Text("   ★ ${item.ratingAvg} (${item.ratingCount})", style = MaterialTheme.typography.labelMedium, color = LRTheme.colors.success)
                    if (item.preparationMinutes > 0) Text("   ⏱ ${item.preparationMinutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item.variantGroups.forEach { g ->
                item(key = "vg-${g.id}") {
                    GroupCard(g.name, if (g.isRequired) stringResource(R.string.menu_required) else stringResource(R.string.menu_optional), sheet.groupErrors[g.id]) {
                        g.options.forEach { o ->
                            Row(Modifier.fillMaxWidth().selectable(selected = sheet.variantId == o.id, enabled = o.isAvailable, onClick = { vm.selectVariant(o.id) }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = sheet.variantId == o.id, onClick = null, enabled = o.isAvailable)
                                Spacer(Modifier.width(8.dp))
                                Text(o.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (o.isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(o.price.toMoney().format(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item.addonGroups.forEach { g ->
                item(key = "ag-${g.id}") {
                    val hint = when {
                        g.minSelect > 0 && g.maxSelect > g.minSelect -> stringResource(R.string.menu_choose_between, g.minSelect, g.maxSelect)
                        g.minSelect > 0 -> stringResource(R.string.menu_choose_exactly, g.minSelect)
                        else -> stringResource(R.string.menu_choose_up_to, g.maxSelect)
                    }
                    GroupCard(g.name, hint, sheet.groupErrors[g.id]) {
                        g.options.forEach { o ->
                            val checked = o.id in sheet.addonIds
                            Row(Modifier.fillMaxWidth().selectable(selected = checked, enabled = o.isAvailable, onClick = { vm.toggleAddon(g.id, o.id) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = o.isAvailable)
                                Spacer(Modifier.width(8.dp))
                                VegNonVegDot(o.foodType, size = 12.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(o.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text("+ " + o.price.toMoney().format(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item(key = "note") {
                LRTextField(value = sheet.note, onValueChange = vm::setNote, label = stringResource(R.string.menu_special_instructions), placeholder = stringResource(R.string.menu_special_instructions_hint), singleLine = false, maxLines = 3)
            }
            item(key = "cta") {
                sheet.error?.let { InlineError(it, Modifier.padding(bottom = 8.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(sheet.quantity, onIncrement = { vm.setQuantity(sheet.quantity + 1) }, onDecrement = { vm.setQuantity(sheet.quantity - 1) }, enabled = sheet.quantity > 1 || true)
                    Spacer(Modifier.width(12.dp))
                    LRButton(stringResource(R.string.menu_add_for, sheet.total.format()), onClick = vm::addFromSheet, modifier = Modifier.weight(1f), enabled = sheet.canAdd, loading = sheet.adding)
                }
            }
        }
    }
}

@Composable
private fun GroupCard(title: String, hint: String, error: GroupError?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (error == null) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = LRTheme.colors.success)
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Keep the sheet's list from growing past the screen; the sheet itself scrolls. */
private fun Modifier.heightInSheet(): Modifier = this.then(Modifier.height(560.dp))
