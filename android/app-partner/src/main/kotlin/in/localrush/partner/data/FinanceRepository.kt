package `in`.localrush.partner.data

import android.content.Context
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.AppDispatchers
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.toAppError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val api: PartnerApi,
    private val dispatchers: AppDispatchers,
    @ApplicationContext private val context: Context,
) {
    suspend fun summary(dateFrom: String?, dateTo: String?): AppResult<FinanceSummaryDto> = apiCall { api.financeSummary(dateFrom, dateTo) }
    suspend fun lines(unsettledOnly: Boolean, settlementId: String? = null, page: Int? = null): AppResult<PageDto<SettlementLineDto>> =
        apiCall { api.financeLines(unsettled = if (unsettledOnly) 1 else null, settlement = settlementId, page = page) }
    suspend fun settlements(page: Int? = null): AppResult<PageDto<SettlementDto>> = apiCall { api.settlements(page) }

    /** Downloads the CSV statement into `cache/statements/` and returns the file (shared through FileProvider). */
    suspend fun downloadStatement(dateFrom: String?, dateTo: String?): AppResult<File> = withContext(dispatchers.io) {
        try {
            val body = api.statementCsv(dateFrom, dateTo)
            val dir = File(context.cacheDir, "statements").apply { mkdirs() }
            val file = File(dir, "statement-${dateFrom ?: "all"}-${dateTo ?: "all"}.csv")
            body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            Result.Ok(file)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.Err<AppError>(t.toAppError())
        }
    }
}
