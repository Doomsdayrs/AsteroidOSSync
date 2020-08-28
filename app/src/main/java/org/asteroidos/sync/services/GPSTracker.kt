package org.asteroidos.sync.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.getSystemService
import org.asteroidos.sync.services.GPSTracker

open class GPSTracker constructor(private val mContext: Context) : Service(), LocationListener {
	// Flag for GPS status
	private var mCanGetLocation: Boolean = false

	/**
	 * Function to get mLatitude
	 */
	var latitude // Latitude
			: Double = 0.0

	/**
	 * Function to get mLongitude
	 */
	var longitude // Longitude
			: Double = 0.0

	// Declaring a Location Manager
	private var mLocationManager: LocationManager? = null

	fun updateLocation() {
		try {
			mLocationManager = mContext.getSystemService()
			val provider: String? = mLocationManager!!.getBestProvider(
					Criteria().apply {
						accuracy = Criteria.ACCURACY_LOW
						isSpeedRequired = false
						isAltitudeRequired = false
						isBearingRequired = false
						isCostAllowed = true
						powerRequirement = Criteria.POWER_LOW
					},
					true
			)
			if (provider != null) {
				mLocationManager!!.requestSingleUpdate(provider, this, null)
				mCanGetLocation = true
			}
		} catch (e: SecurityException) {
			e.printStackTrace()
		} catch (e: NullPointerException) {
			e.printStackTrace()
		}
	}

	/**
	 * Stop using GPS listener
	 * Calling this function will stop using GPS in your app.
	 */
	fun stopUsingGPS() {
		mCanGetLocation = false
		if (mLocationManager != null) {
			mLocationManager!!.removeUpdates(this@GPSTracker)
		}
	}

	fun gotLocation() {
		mCanGetLocation = false
	}

	/**
	 * Function to check GPS/Wi-Fi enabled
	 * @return boolean
	 */
	fun canGetLocation(): Boolean = mCanGetLocation

	override fun onLocationChanged(location: Location) {
		latitude = location.latitude
		longitude = location.longitude
	}

	override fun onProviderDisabled(provider: String) {}
	override fun onProviderEnabled(provider: String) {}
	override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
	override fun onBind(arg0: Intent): IBinder? = null

	init {
		updateLocation()
	}
}