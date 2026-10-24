package `in`.localrush.rider.data

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.util.apiCall
import javax.inject.Inject
import javax.inject.Singleton

interface EarningsRepository {
    suspend fun earnings(period: EarningsPeriod): AppResult<RiderEarningsDto>
    suspend fun payouts(page: Int?): AppResult<PageDto<RiderPayoutDto>>
    suspend fun requestPayout(amount: String?): AppResult<RiderPayoutDto>
}

@Singleton
class RemoteEarningsRepository @Inject constructor(private val api: RiderApi) : EarningsRepository {
    override suspend fun earnings(period: EarningsPeriod) = apiCall { api.earnings(period.wire) }
    override suspend fun payouts(page: Int?) = apiCall { api.payouts(page) }
    override suspend fun requestPayout(amount: String?) = apiCall { api.requestPayout(PayoutRequestDto(amount)) }
}
