package `in`.localrush.partner.data

import android.content.Context
import android.net.Uri
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.result.flatMap
import `in`.localrush.core.network.dto.IdsRequest
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepository @Inject constructor(
    private val api: PartnerApi,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    suspend fun categories(): AppResult<List<MenuCategoryDto>> = apiCall { api.menuCategories() }.map { it.sortedBy { c -> c.sortOrder } }
    suspend fun createCategory(name: String, description: String): AppResult<MenuCategoryDto> =
        apiCall { api.createCategory(MenuCategoryWriteDto(name = name, description = description)) }
    suspend fun updateCategory(id: String, name: String? = null, description: String? = null, isActive: Boolean? = null): AppResult<MenuCategoryDto> =
        apiCall { api.updateCategory(id, MenuCategoryWriteDto(name = name, description = description, isActive = isActive)) }
    suspend fun deleteCategory(id: String): EmptyResult = apiCallUnit { api.deleteCategory(id) }
    suspend fun reorderCategories(ids: List<String>): AppResult<List<MenuCategoryDto>> = apiCall { api.reorderCategories(IdsRequest(ids)) }

    /** Loads every page (the partner menu is small; page_size 100). */
    suspend fun allItems(query: String? = null, category: String? = null, isAvailable: Boolean? = null): AppResult<List<MenuItemDto>> {
        val out = mutableListOf<MenuItemDto>()
        var page: Int? = 1
        while (page != null) {
            val r = apiCall { api.menuItems(query = query?.ifBlank { null }, category = category, isAvailable = isAvailable, page = page) }
            when (r) {
                is Result.Ok -> { out += r.value.results; page = r.value.nextPage }
                is Result.Err -> return r
            }
        }
        return Result.Ok(out)
    }

    suspend fun item(id: String): AppResult<MenuItemDto> = apiCall { api.menuItem(id) }

    private fun body(write: MenuItemWriteDto): JsonObject {
        val obj = json.encodeToJsonElement(MenuItemWriteDto.serializer(), write).jsonObject.toMutableMap()
        // explicitNulls=false drops nulls; the backend needs an explicit null to clear a discount / stock limit.
        if (write.discountedPrice == null) obj["discounted_price"] = JsonNull
        if (write.stockQuantity == null) obj["stock_quantity"] = JsonNull
        if (write.calories == null) obj["calories"] = JsonNull
        return JsonObject(obj)
    }

    suspend fun createItem(write: MenuItemWriteDto, image: Uri?): AppResult<MenuItemDto> =
        apiCall { api.createItem(body(write)) }.flatMap { created -> if (image != null) uploadImage(created.id, image) else Result.Ok(created) }

    suspend fun updateItem(id: String, write: MenuItemWriteDto, image: Uri?): AppResult<MenuItemDto> =
        apiCall { api.updateItem(id, body(write)) }.flatMap { updated -> if (image != null) uploadImage(id, image) else Result.Ok(updated) }

    private suspend fun uploadImage(id: String, image: Uri): AppResult<MenuItemDto> =
        apiCall { api.updateItemImage(id, Multipart.uriPart(context, "image", image)) }

    suspend fun deleteItem(id: String): EmptyResult = apiCallUnit { api.deleteItem(id) }
    suspend fun setAvailability(id: String, available: Boolean, stock: Int? = null): AppResult<MenuItemDto> =
        apiCall { api.setAvailability(id, AvailabilityDto(available, stock)) }
    suspend fun bulkAvailability(ids: List<String>, available: Boolean): AppResult<Int> =
        apiCall { api.bulkAvailability(BulkAvailabilityDto(ids, available)) }.map { it.updated }
}
