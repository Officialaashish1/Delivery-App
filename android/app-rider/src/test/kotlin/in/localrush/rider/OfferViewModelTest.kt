package `in`.localrush.rider

import app.cash.turbine.test
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.common.result.Result
import `in`.localrush.rider.data.OfferInbox
import `in`.localrush.rider.data.OffersRepository
import `in`.localrush.rider.data.RiderOfferDto
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.feature.offer.vm.GoneReason
import `in`.localrush.rider.feature.offer.vm.OfferUiState
import `in`.localrush.rider.feature.offer.vm.OfferViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfferViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private class FakeOffers(
        var offer: AppResult<RiderOfferDto>,
        var acceptResult: AppResult<RiderOrderDto> = Result.Ok(ORDER),
        var rejectResult: EmptyResult = Result.Ok(Unit),
    ) : OffersRepository {
        val rejected = mutableListOf<String>()
        override suspend fun pending() = Result.Ok(emptyList<RiderOfferDto>())
        override suspend fun offer(id: String) = offer
        override suspend fun accept(id: String) = acceptResult
        override suspend fun reject(id: String, reason: String): EmptyResult { rejected += reason; return rejectResult }
    }

    private fun vm(repo: FakeOffers, inbox: OfferInbox = OfferInbox()) = OfferViewModel(repo, inbox).also { it.load("a1") }

    @Test fun `counts down from seconds_left and expires at zero`() = runTest {
        val vm = vm(FakeOffers(Result.Ok(offer(secondsLeft = 3))))
        runCurrent()
        assertEquals(3, (vm.state.value as OfferUiState.Ready).secondsLeft)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(2, (vm.state.value as OfferUiState.Ready).secondsLeft)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(OfferUiState.Gone(GoneReason.TIMED_OUT), vm.state.value)
    }

    @Test fun `progress shrinks with the countdown`() = runTest {
        val vm = vm(FakeOffers(Result.Ok(offer(secondsLeft = 20))))
        runCurrent()
        assertEquals(1f, (vm.state.value as OfferUiState.Ready).progress)
        advanceTimeBy(10_001); runCurrent()
        assertEquals(0.5f, (vm.state.value as OfferUiState.Ready).progress, 0.001f)
    }

    @Test fun `accept moves to Accepted with the order and stops the ticker`() = runTest {
        val vm = vm(FakeOffers(Result.Ok(offer(secondsLeft = 30))))
        runCurrent()
        vm.accept()
        assertTrue((vm.state.value as OfferUiState.Ready).accepting)
        runCurrent()
        assertEquals(OfferUiState.Accepted(ORDER), vm.state.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(OfferUiState.Accepted(ORDER), vm.state.value)
    }

    @Test fun `accept 409 offer_expired becomes Gone TIMED_OUT, offer_unavailable becomes UNAVAILABLE`() = runTest {
        val repo = FakeOffers(Result.Ok(offer(30)), acceptResult = Result.Err(AppError.Api(ErrorCodes.OFFER_EXPIRED, "expired", 409)))
        val vm = vm(repo)
        runCurrent(); vm.accept(); runCurrent()
        assertEquals(OfferUiState.Gone(GoneReason.TIMED_OUT), vm.state.value)

        val repo2 = FakeOffers(Result.Ok(offer(30)), acceptResult = Result.Err(AppError.Api(ErrorCodes.OFFER_UNAVAILABLE, "taken", 409)))
        val vm2 = vm(repo2)
        runCurrent(); vm2.accept(); runCurrent()
        assertEquals(OfferUiState.Gone(GoneReason.UNAVAILABLE), vm2.state.value)
    }

    @Test fun `accept failure for other reasons keeps the offer with an error and countdown running`() = runTest {
        val repo = FakeOffers(Result.Ok(offer(30)), acceptResult = Result.Err(AppError.Api("rider_offline", "Go online", 400)))
        val vm = vm(repo)
        runCurrent(); vm.accept(); runCurrent()
        val s = vm.state.value as OfferUiState.Ready
        assertFalse(s.accepting)
        assertEquals("Go online", s.actionError?.message)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(29, (vm.state.value as OfferUiState.Ready).secondsLeft)
    }

    @Test fun `reject sends the reason and ends in Rejected`() = runTest {
        val repo = FakeOffers(Result.Ok(offer(30)))
        val vm = vm(repo)
        runCurrent()
        vm.openReject()
        assertTrue((vm.state.value as OfferUiState.Ready).showRejectSheet)
        vm.reject("too_far"); runCurrent()
        assertEquals(listOf("too_far"), repo.rejected)
        assertEquals(OfferUiState.Rejected, vm.state.value)
    }

    @Test fun `loading an already handled or missing offer is Gone and consumes the inbox`() = runTest {
        val inbox = OfferInbox()
        inbox.post("a1")
        val vm = OfferViewModel(FakeOffers(Result.Err(AppError.Api(ErrorCodes.NOT_FOUND, "nf", 404))), inbox)
        vm.load("a1"); runCurrent()
        assertEquals(OfferUiState.Gone(GoneReason.ALREADY_HANDLED), vm.state.value)
        assertNull(inbox.pending.value)

        val vm2 = vm(FakeOffers(Result.Ok(offer(30).copy(status = "expired"))))
        runCurrent()
        assertEquals(OfferUiState.Gone(GoneReason.ALREADY_HANDLED), vm2.state.value)
    }

    @Test fun `network error exposes Error state and retry reloads`() = runTest {
        val repo = FakeOffers(Result.Err(AppError.Network()))
        val vm = vm(repo)
        runCurrent()
        assertTrue(vm.state.value is OfferUiState.Error)
        repo.offer = Result.Ok(offer(15))
        vm.retry()
        vm.state.test {
            assertTrue(awaitItem() is OfferUiState.Loading || awaitItem() is OfferUiState.Ready)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
    }

    companion object {
        val ORDER = RiderOrderDto(id = "o1", orderNumber = "LR1", status = "rider_assigned", riderPayout = "45.00")
        fun offer(secondsLeft: Int) = RiderOfferDto(id = "a1", order = ORDER, status = "offered", secondsLeft = secondsLeft)
    }
}
