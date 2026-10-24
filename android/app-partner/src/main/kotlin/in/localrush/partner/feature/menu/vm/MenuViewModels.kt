package `in`.localrush.partner.feature.menu.vm

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.MenuCategoryDto
import `in`.localrush.partner.data.MenuItemDto
import `in`.localrush.partner.data.MenuRepository
import `in`.localrush.partner.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ categories

data class CategoriesData(val categories: List<MenuCategoryDto>, val busy: Boolean = false)

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: MenuRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<CategoriesData>>(UiState.Loading)
    val state: StateFlow<UiState<CategoriesData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = repo.categories()) {
                is Result.Ok -> _state.value = if (r.value.isEmpty()) UiState.Empty else UiState.Success(CategoriesData(r.value))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    private fun busy(b: Boolean) = _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(busy = b)) else s }
    private fun mutate(block: suspend () -> Result<*, AppError>) = viewModelScope.launch {
        busy(true)
        block().onFailure { _messages.tryEmit(it.message) }
        busy(false)
        load()
    }

    fun create(name: String, description: String) = mutate { repo.createCategory(name.trim(), description.trim()) }
    fun rename(id: String, name: String, description: String) = mutate { repo.updateCategory(id, name = name.trim(), description = description.trim()) }
    fun setActive(id: String, active: Boolean) = mutate { repo.updateCategory(id, isActive = active) }
    fun delete(id: String) = mutate { repo.deleteCategory(id) }

    /** Move a category one position up/down and persist the full order. */
    fun move(id: String, delta: Int) {
        val current = (_state.value as? UiState.Success)?.data?.categories ?: return
        val idx = current.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target < 0 || target >= current.size) return
        val reordered = current.toMutableList().apply { add(target, removeAt(idx)) }
        _state.value = UiState.Success(CategoriesData(reordered, busy = true))
        viewModelScope.launch {
            repo.reorderCategories(reordered.map { it.id }).onFailure { _messages.tryEmit(it.message) }
            load()
        }
    }
}

// ------------------------------------------------------------------ items list

enum class AvailabilityFilter { ALL, AVAILABLE, UNAVAILABLE }

data class ItemsData(
    val items: List<MenuItemDto>,
    val categories: List<MenuCategoryDto>,
    val query: String = "",
    val categoryId: String? = null,
    val filter: AvailabilityFilter = AvailabilityFilter.ALL,
    val selected: Set<String> = emptySet(),
    val busyIds: Set<String> = emptySet(),
    val bulkBusy: Boolean = false,
    val refreshing: Boolean = false,
) {
    val visible: List<MenuItemDto> get() = items.filter { i ->
        (query.isBlank() || i.name.contains(query, ignoreCase = true)) &&
            (categoryId == null || i.category == categoryId) &&
            when (filter) { AvailabilityFilter.ALL -> true; AvailabilityFilter.AVAILABLE -> i.isAvailable; AvailabilityFilter.UNAVAILABLE -> !i.isAvailable }
    }
    val selectionMode: Boolean get() = selected.isNotEmpty()
}

@HiltViewModel
class MenuItemsViewModel @Inject constructor(private val repo: MenuRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ItemsData>>(UiState.Loading)
    val state: StateFlow<UiState<ItemsData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var initialCategory: String? = null
    private var started = false

    fun start(categoryId: String?) {
        if (started) return
        started = true
        initialCategory = categoryId
        load()
    }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            val items = repo.allItems(); val cats = repo.categories()
            when {
                items is Result.Ok && cats is Result.Ok -> _state.update { s ->
                    val prev = (s as? UiState.Success)?.data
                    UiState.Success(ItemsData(items.value, cats.value, query = prev?.query ?: "", categoryId = prev?.categoryId ?: initialCategory, filter = prev?.filter ?: AvailabilityFilter.ALL))
                }
                else -> {
                    val err = (items as? Result.Err)?.error ?: (cats as Result.Err).error
                    _state.update { s -> if (s is UiState.Success) { _messages.tryEmit(err.message); s.copy(data = s.data.copy(refreshing = false)) } else UiState.Error(err) }
                }
            }
        }
    }

    private inline fun edit(block: (ItemsData) -> ItemsData) = _state.update { s -> if (s is UiState.Success) s.copy(data = block(s.data)) else s }

    fun setQuery(q: String) = edit { it.copy(query = q) }
    fun setCategory(id: String?) = edit { it.copy(categoryId = id) }
    fun setFilter(f: AvailabilityFilter) = edit { it.copy(filter = f) }
    fun toggleSelect(id: String) = edit { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
    fun clearSelection() = edit { it.copy(selected = emptySet()) }
    fun selectAllVisible() = edit { d -> d.copy(selected = d.visible.map { it.id }.toSet()) }

    fun setAvailability(id: String, available: Boolean) {
        edit { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            when (val r = repo.setAvailability(id, available)) {
                is Result.Ok -> edit { d -> d.copy(items = d.items.map { if (it.id == id) r.value else it }, busyIds = d.busyIds - id) }
                is Result.Err -> { _messages.tryEmit(r.error.message); edit { it.copy(busyIds = it.busyIds - id) } }
            }
        }
    }

    fun bulkAvailability(available: Boolean) {
        val ids = (_state.value as? UiState.Success)?.data?.selected?.toList() ?: return
        if (ids.isEmpty()) return
        edit { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            when (val r = repo.bulkAvailability(ids, available)) {
                is Result.Ok -> edit { d -> d.copy(items = d.items.map { if (it.id in ids) it.copy(isAvailable = available) else it }, selected = emptySet(), bulkBusy = false) }
                is Result.Err -> { _messages.tryEmit(r.error.message); edit { it.copy(bulkBusy = false) } }
            }
        }
    }

    fun delete(id: String) {
        edit { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            when (val r = repo.deleteItem(id)) {
                is Result.Ok -> edit { d -> d.copy(items = d.items.filterNot { it.id == id }, busyIds = d.busyIds - id, selected = d.selected - id) }
                is Result.Err -> { _messages.tryEmit(r.error.message); edit { it.copy(busyIds = it.busyIds - id) } }
            }
        }
    }
}

// ------------------------------------------------------------------ item editor

data class ItemEditorData(
    val form: ItemForm,
    val categories: List<MenuCategoryDto>,
    val existingImage: String? = null,
    val newImage: Uri? = null,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val isNew: Boolean,
)

sealed interface ItemEditorEvent {
    data class Saved(val item: MenuItemDto) : ItemEditorEvent
    data class Message(val text: String) : ItemEditorEvent
}

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: MenuRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val route = savedState.toRoute<Routes.ItemEditor>()
    val itemId: String? = route.itemId
    private val _state = MutableStateFlow<UiState<ItemEditorData>>(UiState.Loading)
    val state: StateFlow<UiState<ItemEditorData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _events = MutableSharedFlow<ItemEditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ItemEditorEvent> = _events.asSharedFlow()
    private var validateJob: Job? = null

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val cats = repo.categories()
            if (cats is Result.Err) { _state.value = UiState.Error(cats.error); return@launch }
            val categories = (cats as Result.Ok).value
            if (itemId == null) {
                _state.value = UiState.Success(ItemEditorData(ItemForm(categoryId = route.categoryId ?: categories.firstOrNull()?.id), categories, isNew = true))
            } else when (val r = repo.item(itemId)) {
                is Result.Ok -> _state.value = UiState.Success(ItemEditorData(ItemForm.from(r.value), categories, existingImage = r.value.image, isNew = false))
                is Result.Err -> _state.value = UiState.Error(r.error)
            }
        }
    }

    fun edit(block: (ItemForm) -> ItemForm) {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(form = block(s.data.form))) else s }
        // Re-validate shortly after typing so the discount / min-max errors update live (only once errors are shown).
        validateJob?.cancel()
        validateJob = viewModelScope.launch {
            delay(250)
            _state.update { s -> if (s is UiState.Success && s.data.errors.isNotEmpty()) s.copy(data = s.data.copy(errors = ItemEditorValidator.validate(s.data.form))) else s }
        }
    }

    fun setImage(uri: Uri?) = _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(newImage = uri)) else s }

    fun save() {
        val s = _state.value as? UiState.Success ?: return
        if (s.data.saving) return
        val errors = ItemEditorValidator.validate(s.data.form)
        if (errors.isNotEmpty()) { _state.value = s.copy(data = s.data.copy(errors = errors)); return }
        _state.value = s.copy(data = s.data.copy(saving = true, errors = emptyMap()))
        viewModelScope.launch {
            val write = ItemEditorValidator.toWrite(s.data.form)
            val r = if (itemId == null) repo.createItem(write, s.data.newImage) else repo.updateItem(itemId, write, s.data.newImage)
            when (r) {
                is Result.Ok -> _events.tryEmit(ItemEditorEvent.Saved(r.value))
                is Result.Err -> {
                    val api = r.error as? AppError.Api
                    _state.update { cur -> if (cur is UiState.Success) cur.copy(data = cur.data.copy(saving = false, errors = api?.fields?.mapValues { it.value.joinToString() } ?: emptyMap())) else cur }
                    _events.tryEmit(ItemEditorEvent.Message(r.error.message))
                }
            }
        }
    }
}
