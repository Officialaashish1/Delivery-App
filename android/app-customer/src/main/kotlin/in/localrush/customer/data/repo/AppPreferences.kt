package `in`.localrush.customer.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import `in`.localrush.core.common.geo.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.customerPrefs: DataStore<Preferences> by preferencesDataStore("customer_prefs")

/** Where the customer wants food delivered right now (chosen on the map or from a saved address). */
data class DeliveryLocation(
    val point: LatLng,
    /** Short title, e.g. "Home" or the street. */
    val title: String,
    /** Full address line shown truncated in the header. */
    val line: String,
    val city: String = "",
    /** Saved address id when this location came from the address book. */
    val addressId: String? = null,
)

/** Small per-install flags & the chosen delivery location (DataStore). */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.customerPrefs

    val onboardingDone: Flow<Boolean> = store.data.map { it[ONBOARDING_DONE] ?: false }
    val termsAccepted: Flow<Boolean> = store.data.map { it[TERMS_ACCEPTED] ?: false }

    val deliveryLocation: Flow<DeliveryLocation?> = store.data.map { p ->
        val lat = p[LOC_LAT]; val lng = p[LOC_LNG]
        if (lat == null || lng == null) null else DeliveryLocation(
            point = LatLng(lat, lng),
            title = p[LOC_TITLE] ?: "",
            line = p[LOC_LINE] ?: "",
            city = p[LOC_CITY] ?: "",
            addressId = p[LOC_ADDRESS_ID],
        )
    }

    suspend fun currentDeliveryLocation(): DeliveryLocation? = deliveryLocation.first()

    suspend fun setOnboardingDone() { store.edit { it[ONBOARDING_DONE] = true } }
    suspend fun setTermsAccepted() { store.edit { it[TERMS_ACCEPTED] = true } }

    suspend fun setDeliveryLocation(loc: DeliveryLocation) {
        store.edit {
            it[LOC_LAT] = loc.point.latitude; it[LOC_LNG] = loc.point.longitude
            it[LOC_TITLE] = loc.title; it[LOC_LINE] = loc.line; it[LOC_CITY] = loc.city
            if (loc.addressId != null) it[LOC_ADDRESS_ID] = loc.addressId else it.remove(LOC_ADDRESS_ID)
        }
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
        val LOC_LAT = doublePreferencesKey("loc_lat")
        val LOC_LNG = doublePreferencesKey("loc_lng")
        val LOC_TITLE = stringPreferencesKey("loc_title")
        val LOC_LINE = stringPreferencesKey("loc_line")
        val LOC_CITY = stringPreferencesKey("loc_city")
        val LOC_ADDRESS_ID = stringPreferencesKey("loc_address_id")
    }
}
