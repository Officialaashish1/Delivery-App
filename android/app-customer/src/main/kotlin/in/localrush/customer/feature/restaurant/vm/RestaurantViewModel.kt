package `in`.localrush.customer.feature.restaurant.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.ReviewDto
import `in`.localrush.customer.data.dto.AddToCartDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.RestaurantDetailDto
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.RestaurantRepository
import `in`.localrush.customer.data.repo.conflictRestaurantName
import `in`.localrush.customer.data.repo.isCartConflict
import `in`.localrush.customer.data.repo.isUnauthenticated
import `in`.localrush.customer.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Item sheet: variant radio, add-on checkboxes (min/max), quantity, note; price recomputed live. */
data class ItemSheetState(
    val item: MenuItemDto,
    val variantId: String? = null,
    val addonIds: Set<String> = emptySet(),
    val quantity: Int = 1,
    val note: String = "",
    val adding: Boolean = false,
    val error: String? = null,
) {
    val unitPrice: Money get() {
        val base = item.variantGroups.flatMap { it.options }.firstOrNull { it.id == variantId }?.price?.toMoney() ?: item.effectivePrice.toMoney()
        val addons = item.addonGroups.flatMap { it.options }.filter { it.id in addonIds }.fold(Money.ZERO) { acc, a -> acc + a.price.toMoney() }
        return base + addons
    }
    val total: Money get() = unitPrice * quantity
    /** Validation reasons keyed by group id; empty when the selection is valid. */
    val groupErrors: Map<String, GroupError> get() = buildMap {
        item.variantGroups.filter { it.isRequired && variantId !in it.options.map { o -> o.id } }.forEach { put(it.id, GroupError.REQUIRED) }
        item.addonGroups.forEach { g ->
            val n = g.options.count { it.id in addonIds }
            if (n < g.minSelect) put(g.id, GroupError.MIN) else if (n > g.maxSelect) put(g.id, GroupError.MAX)
        }
    }
    val canAdd: Boolean get() = groupErrors.isEmpty() && !adding
}

enum class GroupError { REQUIRED, MIN, MAX }

data class RestaurantUiState(
    val loading: Boolean = true,
    val restaurant: RestaurantDetailDto? = null,
    val error: AppError? = null,
    val cart: CartDto? = null,
    val sheet: ItemSheetState? = null,
    /** Pending add that hit `cart_restaurant_conflict`; shown as "Replace cart?" dialog. */
    val conflict: PendingConflict? = null,
    val reviews: PageDto<ReviewDto>? = null,
    val reviewsLoading: Boolean = false,
    val reviewsError: AppError? = null,
    val loggedIn: Boolean = false,
) {
    fun quantityOf(itemId: String): Int = cart?.items?.filter { it.menuItem == itemId }?.sumOf { it.quantity } ?: 0
    val cartForThisRestaurant: Boolean get() = cart?.restaurant?.id != null && cart.restaurant.id == restaurant?.id
}

data class PendingConflict(val request: AddToCartDto, val currentRestaurant: String?)

sealed interface RestaurantEvent {
    data object LoginRequired : RestaurantEvent
    data class Message(val text: String) : RestaurantEvent
    data object Added : RestaurantEvent
}

@HiltViewModel
class RestaurantViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RestaurantRepository,
    private val cart: CartRepository,
    private val prefs: AppPreferences,
    private val session: SessionManager,
) : ViewModel() {
    private val route = savedState.toRoute<Routes.Restaurant>()
    private val _state = MutableStateFlow(RestaurantUiState())
    val state: StateFlow<RestaurantUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<RestaurantEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RestaurantEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch { cart.cart.collect { c -> _state.update { it.copy(cart = c) } } }
        viewModelScope.launch { session.state.collect { s -> _state.update { it.copy(loggedIn = s is `in`.localrush.core.auth.SessionState.LoggedIn) } } }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val loc = prefs.deliveryLocation.first()
            repo.detail(route.slug, loc?.point?.latitude, loc?.point?.longitude).fold(
                onOk = { r ->
                    _state.update { it.copy(loading = false, restaurant = r) }
                    // Deep link straight to an item (search result / recommended).
                    route.itemId?.let { id -> r.menu.flatMap { it.items }.firstOrNull { it.id == id }?.let { openItem(it) } }
                },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

    // ---- item sheet ----
    fun openItem(item: MenuItemDto) {
        val defaultVariant = item.variantGroups.firstOrNull()?.options?.firstOrNull { it.isDefault && it.isAvailable }?.id
        _state.update { it.copy(sheet = ItemSheetState(item, variantId = defaultVariant)) }
    }
    fun closeItem() = _state.update { it.copy(sheet = null) }
    fun selectVariant(id: String) = _state.update { it.copy(sheet = it.sheet?.copy(variantId = id, error = null)) }
    fun toggleAddon(groupId: String, id: String) = _state.update { s ->
        val sheet = s.sheet ?: return@update s
        val group = sheet.item.addonGroups.firstOrNull { it.id == groupId } ?: return@update s
        val ids = sheet.addonIds.toMutableSet()
        if (id in ids) ids -= id else {
            // Single-select behaviour when max_select == 1: replace the current choice.
            if (group.maxSelect == 1) ids -= group.options.map { it.id }.toSet()
            ids += id
        }
        s.copy(sheet = sheet.copy(addonIds = ids, error = null))
    }
    fun setQuantity(q: Int) = _state.update { it.copy(sheet = it.sheet?.copy(quantity = q.coerceIn(1, 20))) }
    fun setNote(n: String) = _state.update { it.copy(sheet = it.sheet?.copy(note = n.take(255))) }

    fun addFromSheet() {
        val sheet = _state.value.sheet ?: return
        if (!sheet.canAdd) return
        add(AddToCartDto(sheet.item.id, sheet.quantity, sheet.variantId, sheet.addonIds.toList(), sheet.note.trim()), fromSheet = true)
    }

    /** Quick add from the row (no customisation groups). */
    fun quickAdd(item: MenuItemDto) {
        if (item.variantGroups.isNotEmpty() || item.addonGroups.isNotEmpty()) { openItem(item); return }
        add(AddToCartDto(item.id, 1), fromSheet = false)
    }

    fun increment(item: MenuItemDto) {
        val line = _state.value.cart?.items?.firstOrNull { it.menuItem == item.id } ?: return quickAdd(item)
        viewModelScope.launch { cart.updateQuantity(line.id, line.quantity + 1).onFailure { e -> _events.tryEmit(RestaurantEvent.Message(e.message)) } }
    }

    fun decrement(item: MenuItemDto) {
        val line = _state.value.cart?.items?.firstOrNull { it.menuItem == item.id } ?: return
        viewModelScope.launch { cart.updateQuantity(line.id, line.quantity - 1).onFailure { e -> _events.tryEmit(RestaurantEvent.Message(e.message)) } }
    }

    private fun add(request: AddToCartDto, fromSheet: Boolean) {
        if (!session.isLoggedIn) { _events.tryEmit(RestaurantEvent.LoginRequired); return }
        if (fromSheet) _state.update { it.copy(sheet = it.sheet?.copy(adding = true, error = null)) }
        viewModelScope.launch {
            cart.add(request).fold(
                onOk = { _state.update { it.copy(sheet = null) }; _events.tryEmit(RestaurantEvent.Added) },
                onErr = { e ->
                    when {
                        e.isCartConflict -> _state.update { it.copy(sheet = it.sheet?.copy(adding = false), conflict = PendingConflict(request, e.conflictRestaurantName)) }
                        e.isUnauthenticated -> { _state.update { it.copy(sheet = it.sheet?.copy(adding = false)) }; _events.tryEmit(RestaurantEvent.LoginRequired) }
                        fromSheet -> _state.update { it.copy(sheet = it.sheet?.copy(adding = false, error = e.message)) }
                        else -> _events.tryEmit(RestaurantEvent.Message(e.message))
                    }
                },
            )
        }
    }

    fun confirmReplaceCart() {
        val pending = _state.value.conflict ?: return
        _state.update { it.copy(conflict = null) }
        add(pending.request.copy(replaceCart = true), fromSheet = _state.value.sheet != null)
    }
    fun dismissConflict() = _state.update { it.copy(conflict = null) }

    // ---- reviews sheet ----
    fun loadReviews() {
        val id = _state.value.restaurant?.id ?: return
        _state.update { it.copy(reviewsLoading = true, reviewsError = null) }
        viewModelScope.launch {
            repo.reviews(id).fold(
                onOk = { p -> _state.update { it.copy(reviews = p, reviewsLoading = false) } },
                onErr = { e -> _state.update { it.copy(reviewsLoading = false, reviewsError = e) } },
            )
        }
    }
}
