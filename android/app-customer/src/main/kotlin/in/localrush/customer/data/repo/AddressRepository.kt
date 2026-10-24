package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.network.api.AddressApi
import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.AddressWriteDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddressRepository @Inject constructor(private val api: AddressApi) {
    private val _addresses = MutableStateFlow<List<AddressDto>?>(null)
    /** Last loaded address book (null until first load). */
    val addresses: StateFlow<List<AddressDto>?> = _addresses

    suspend fun load(): AppResult<List<AddressDto>> = apiCall { api.list(pageSize = 100).results }.onSuccess { _addresses.value = it }
    suspend fun create(body: AddressWriteDto): AppResult<AddressDto> = apiCall { api.create(body) }.onSuccess { load() }
    suspend fun update(id: String, body: AddressWriteDto): AppResult<AddressDto> = apiCall { api.update(id, body) }.onSuccess { load() }
    suspend fun delete(id: String): EmptyResult = apiCallUnit { api.delete(id) }.onSuccess { load() }
    suspend fun setDefault(id: String): AppResult<AddressDto> = update(id, AddressWriteDto(isDefault = true))
}
