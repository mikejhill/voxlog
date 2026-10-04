package com.mikejhill.voxlog.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.mikejhill.voxlog.core.model.GeoLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Obtains the device location for tagging notes. */
interface LocationCapture {
    /** Returns the current location, or null when unavailable, not permitted or too slow. */
    suspend fun currentLocation(isPrecise: Boolean): GeoLocation?
}

/**
 * [LocationCapture] built on the platform [LocationManager] (no Google Play services, so it works
 * on de-Googled devices). Reverse geocoding is best-effort; raw coordinates are kept when it fails.
 */
@Singleton
class PlatformLocationCapture @Inject constructor(@param:ApplicationContext private val context: Context) : LocationCapture {
    private val executor = Executors.newSingleThreadExecutor()

    override suspend fun currentLocation(isPrecise: Boolean): GeoLocation? {
        if (!hasPermission(isPrecise)) return null
        val location = withTimeoutOrNull(TIMEOUT_MILLIS) { requestLocation(isPrecise) } ?: return null
        return GeoLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            placeName = reverseGeocode(location),
        )
    }

    private fun hasPermission(isPrecise: Boolean): Boolean {
        val permission = if (isPrecise) Manifest.permission.ACCESS_FINE_LOCATION else Manifest.permission.ACCESS_COARSE_LOCATION
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // Checked in hasPermission.
    private suspend fun requestLocation(isPrecise: Boolean): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = if (isPrecise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }
        if (!manager.isProviderEnabled(provider)) return manager.getLastKnownLocation(provider)
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, executor) { location -> continuation.resume(location) }
        }
    }

    private suspend fun reverseGeocode(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION") // The async overload needs API 33; minSdk is 30.
                Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { address -> listOfNotNull(address.locality, address.adminArea).joinToString(", ").ifBlank { null } }
            } catch (_: IOException) {
                null
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
