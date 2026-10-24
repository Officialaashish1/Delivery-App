package `in`.localrush.partner.feature.orders.vm

import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.partner.data.PartnerOrderDto

/** The four board tabs. [PAST] also holds orders that left the kitchen (picked up / on the way). */
enum class OrderTab { NEW, PREPARING, READY, PAST }

/**
 * Pure, testable state of the orders board. The WebSocket feed, push events, REST refreshes and user actions all go
 * through [OrdersBoardReducer]; the ViewModel just exposes the result.
 *
 * @property orders every order we know about, by id.
 * @property unacknowledged ids of `placed` orders that arrived while the board was live and have not been seen/acted on
 *   yet. While non-empty the app plays the loud new-order alert.
 * @property loadedTabs tabs whose REST snapshot has been fetched at least once (drives Loading vs Empty).
 */
data class OrdersBoardState(
    val orders: Map<String, PartnerOrderDto> = emptyMap(),
    val unacknowledged: Set<String> = emptySet(),
    val loadedTabs: Set<OrderTab> = emptySet(),
) {
    val alertActive: Boolean get() = unacknowledged.isNotEmpty()

    fun ordersIn(tab: OrderTab): List<PartnerOrderDto> =
        orders.values.filter { OrdersBoardReducer.tabFor(OrderStatus.fromWire(it.status)) == tab }
            .sortedWith(OrdersBoardReducer.sortFor(tab))

    fun count(tab: OrderTab): Int = orders.values.count { OrdersBoardReducer.tabFor(OrderStatus.fromWire(it.status)) == tab }
}

object OrdersBoardReducer {

    fun tabFor(status: OrderStatus): OrderTab = when (status) {
        OrderStatus.PLACED -> OrderTab.NEW
        OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING -> OrderTab.PREPARING
        OrderStatus.READY_FOR_PICKUP, OrderStatus.RIDER_ASSIGNED -> OrderTab.READY
        else -> OrderTab.PAST
    }

    /** New orders oldest-first (act on the one waiting longest); everything else newest-first. */
    fun sortFor(tab: OrderTab): Comparator<PartnerOrderDto> = when (tab) {
        OrderTab.NEW -> compareBy { it.placedAt ?: it.createdAt ?: "" }
        OrderTab.PREPARING, OrderTab.READY -> compareBy { it.acceptedAt ?: it.placedAt ?: it.createdAt ?: "" }
        OrderTab.PAST -> compareByDescending { it.deliveredAt ?: it.pickedUpAt ?: it.placedAt ?: it.createdAt ?: "" }
    }

    /**
     * Insert/refresh a single order. A `placed` order that is new to the board (or was previously in another state)
     * becomes unacknowledged; any other status clears its alert.
     */
    fun upsert(state: OrdersBoardState, order: PartnerOrderDto, fromLiveEvent: Boolean): OrdersBoardState {
        val status = OrderStatus.fromWire(order.status)
        val previous = state.orders[order.id]
        val becamePlaced = status == OrderStatus.PLACED && (previous == null || OrderStatus.fromWire(previous.status) != OrderStatus.PLACED)
        val unack = when {
            status != OrderStatus.PLACED -> state.unacknowledged - order.id
            fromLiveEvent && becamePlaced -> state.unacknowledged + order.id
            else -> state.unacknowledged
        }
        return state.copy(orders = state.orders + (order.id to order), unacknowledged = unack)
    }

    /**
     * Replace the snapshot for one REST scope. Orders that belong to [tab] but are missing from [fresh] are dropped
     * (they moved elsewhere or were filtered out); unacknowledged ids are kept only while the order is still `placed`.
     */
    fun applySnapshot(state: OrdersBoardState, tab: OrderTab, fresh: List<PartnerOrderDto>): OrdersBoardState {
        val keep = state.orders.filterValues { tabFor(OrderStatus.fromWire(it.status)) != tab }
        val merged = keep + fresh.associateBy { it.id }
        val unack = state.unacknowledged.filter { id -> merged[id]?.let { OrderStatus.fromWire(it.status) == OrderStatus.PLACED } == true }.toSet()
        return state.copy(orders = merged, unacknowledged = unack, loadedTabs = state.loadedTabs + tab)
    }

    fun acknowledge(state: OrdersBoardState, orderId: String): OrdersBoardState = state.copy(unacknowledged = state.unacknowledged - orderId)

    fun acknowledgeAll(state: OrdersBoardState): OrdersBoardState = state.copy(unacknowledged = emptySet())

    fun remove(state: OrdersBoardState, orderId: String): OrdersBoardState =
        state.copy(orders = state.orders - orderId, unacknowledged = state.unacknowledged - orderId)

    /** REST scope that backs each tab. */
    fun scopeFor(tab: OrderTab): String = when (tab) {
        OrderTab.NEW -> "new"
        OrderTab.PREPARING -> "preparing"
        OrderTab.READY -> "ready"
        OrderTab.PAST -> "past"
    }
}
