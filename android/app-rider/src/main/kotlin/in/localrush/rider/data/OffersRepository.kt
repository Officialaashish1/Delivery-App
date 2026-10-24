package `in`.localrush.rider.data

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.network.dto.ReasonRequest
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import javax.inject.Inject
import javax.inject.Singleton

interface OffersRepository {
    suspend fun pending(): AppResult<List<RiderOfferDto>>
    suspend fun offer(id: String): AppResult<RiderOfferDto>
    /** Returns the accepted order. 409 `offer_expired` / `offer_unavailable` are surfaced as [AppError.Api]. */
    suspend fun accept(id: String): AppResult<RiderOrderDto>
    suspend fun reject(id: String, reason: String): EmptyResult
}

@Singleton
class RemoteOffersRepository @Inject constructor(private val api: RiderApi) : OffersRepository {
    override suspend fun pending(): AppResult<List<RiderOfferDto>> = apiCall { api.offers().results }
    override suspend fun offer(id: String): AppResult<RiderOfferDto> = apiCall { api.offer(id) }
    override suspend fun accept(id: String): AppResult<RiderOrderDto> = apiCall { api.acceptOffer(id) }
    override suspend fun reject(id: String, reason: String): EmptyResult = apiCallUnit { api.rejectOffer(id, ReasonRequest(reason)) }
}
