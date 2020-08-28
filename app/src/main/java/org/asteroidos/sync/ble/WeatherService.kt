/*
 * Copyright (C) 2016 - Florent Revest <revestflo@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.asteroidos.sync.ble

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Handler
import android.os.SystemClock
import android.preference.PreferenceManager
import android.util.Log
import com.idevicesinc.sweetblue.BleDevice
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.ReadWriteEvent
import github.vatsal.easyweather.Helper.ForecastCallback
import github.vatsal.easyweather.WeatherMap
import github.vatsal.easyweather.retrofit.models.ForecastResponseModel
import org.asteroidos.sync.services.GPSTracker
import org.osmdroid.config.Configuration
import java.nio.charset.StandardCharsets
import java.util.*

// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated ReadWriteListener
class WeatherService(private val mCtx: Context, private val mDevice: BleDevice?) : BleDevice.ReadWriteListener {
	private val mSettings: SharedPreferences
	private var mSReceiver: WeatherSyncReqReceiver? = null
	private var alarmPendingIntent: PendingIntent? = null
	private var alarmMgr: AlarmManager? = null
	private var mGPS: GPSTracker? = null
	private var mLatitude: Float
	private var mLongitude: Float
	fun sync() {
		updateWeather()

		// Register a broadcast handler to use for the alarm Intent
		mSReceiver = WeatherSyncReqReceiver()
		val filter = IntentFilter()
		filter.addAction(WEATHER_SYNC_INTENT)
		mCtx.registerReceiver(mSReceiver, filter)

		// Fire update intent every 30 Minutes to update Weather
		val alarmIntent = Intent(WEATHER_SYNC_INTENT)
		alarmPendingIntent = PendingIntent.getBroadcast(mCtx, 0, alarmIntent, 0)
		alarmMgr = mCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
		alarmMgr!!.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
				AlarmManager.INTERVAL_HALF_HOUR, alarmPendingIntent)
	}

	fun unSync() {
		try {
			mCtx.unregisterReceiver(mSReceiver)
		} catch (ignored: IllegalArgumentException) {
		}
		if (alarmMgr != null) {
			alarmMgr!!.cancel(alarmPendingIntent)
		}
	}

	private fun updateWeather() {
		if (mSettings.getBoolean(PREFS_SYNC_WEATHER, PREFS_SYNC_WEATHER_DEFAULT)) {
			if (mGPS == null) {
				mGPS = GPSTracker(mCtx)
			}
			// Check if GPS enabled
			mGPS!!.updateLocation()
			if (mGPS!!.canGetLocation()) {
				mLatitude = mGPS!!.latitude.toFloat()
				mLongitude = mGPS!!.longitude.toFloat()
				mGPS!!.gotLocation()
				if (isNearNull(mLatitude) && isNearNull(mLongitude)) {
					// We don't have a valid Location yet
					// Use the old location until we have a new one, recheck in 2 Minutes
					val handler = Handler()
					handler.postDelayed({ updateWeather() }, 1000 * 60 * 2.toLong())
					return
				}
			}
			// } else {
			// Can't get location.
			// GPS or network is available, don't set new Location and reuse the old one.
			// }
		} else {
			if (mGPS != null) {
				mGPS!!.stopUsingGPS()
				mGPS = null
			}
			mLatitude = mSettings.getFloat(PREFS_LATITUDE, PREFS_LATITUDE_DEFAULT)
			mLongitude = mSettings.getFloat(PREFS_LONGITUDE, PREFS_LONGITUDE_DEFAULT)
		}
		updateWeather(mLatitude, mLongitude)
		val editor = mSettings.edit()
		editor.putFloat(PREFS_LATITUDE, mLatitude)
		editor.putFloat(PREFS_LONGITUDE, mLongitude)
		editor.apply()
	}

	private fun isNearNull(coord: Float): Boolean {
		return -0.000001f < coord && coord < 0.000001f
	}

	private fun updateWeather(latitude: Float, longitude: Float) {
		val weatherMap = WeatherMap(mCtx, owmApiKey)
		weatherMap.getLocationForecast(latitude.toString(), longitude.toString(), object : ForecastCallback() {
			override fun success(response: ForecastResponseModel) {
				val l = response.list
				val cityName = response.city.name
				var city = byteArrayOf()
				if (cityName != null) city = cityName.toByteArray(StandardCharsets.UTF_8)
				val ids = ByteArray(10)
				val maxTemps = ByteArray(10)
				val minTemps = ByteArray(10)
				var currentDay: Int
				var i = 0
				try {
					for (j in 0..4) { // For each day of forecast
						currentDay = dayOfTimestamp(l[i].dt.toLong())
						var min = Short.MAX_VALUE
						var max = Short.MIN_VALUE
						var id = 0
						while (i < l.size && dayOfTimestamp(l[i].dt.toLong()) == currentDay) { // For each data point of the day
							// TODO is there a better way to select the most significant ID than the first of the afternoon ?
							if (hourOfTimestamp(l[i].dt.toLong()) >= 12 && id == 0) id = l[i].weather[0].id.toShort().toInt()
							val currentTemp = Math.round(l[i].main.temp.toFloat()).toShort()
							if (currentTemp > max) max = currentTemp
							if (currentTemp < min) min = currentTemp
							currentDay = dayOfTimestamp(l[i].dt.toLong())
							i += 1
						}
						ids[2 * j] = (id shr 8).toByte()
						ids[2 * j + 1] = id.toByte()
						maxTemps[2 * j] = (max.toInt() shr 8).toByte()
						maxTemps[2 * j + 1] = max.toByte()
						minTemps[2 * j] = (min.toInt() shr 8).toByte()
						minTemps[2 * j + 1] = min.toByte()
					}
				} catch (ignored: ArrayIndexOutOfBoundsException) {
				}
				mDevice!!.write(weatherCityCharac, city, this@WeatherService)
				mDevice.write(weatherIdsCharac, ids, this@WeatherService)
				mDevice.write(weatherMaxTempsCharac, maxTemps, this@WeatherService)
				mDevice.write(weatherMinTempsCharac, minTemps, this@WeatherService)
			}

			override fun failure(message: String) {
				Log.e("WeatherService", "Could not get weather from owm")
			}
		})
	}

	private fun dayOfTimestamp(timestamp: Long): Int {
		val cal = Calendar.getInstance()
		cal.timeInMillis = timestamp * 1000
		return cal[Calendar.DAY_OF_WEEK]
	}

	private fun hourOfTimestamp(timestamp: Long): Int {
		val cal = Calendar.getInstance()
		cal.timeInMillis = timestamp * 1000
		return cal[Calendar.HOUR_OF_DAY]
	}

	override fun onEvent(e: ReadWriteEvent) {
		if (!e.wasSuccess()) Log.e("WeatherService", e.status().toString())
	}

	internal inner class WeatherSyncReqReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			updateWeather()
		}
	}

	companion object {
		private val weatherCityCharac = UUID.fromString("00008001-0000-0000-0000-00a57e401d05")
		private val weatherIdsCharac = UUID.fromString("00008002-0000-0000-0000-00a57e401d05")
		private val weatherMinTempsCharac = UUID.fromString("00008003-0000-0000-0000-00a57e401d05")
		private val weatherMaxTempsCharac = UUID.fromString("00008004-0000-0000-0000-00a57e401d05")
		private const val owmApiKey = "ffcb5a7ed134aac3d095fa628bc46c65"
		const val PREFS_NAME = "WeatherPreferences"
		const val PREFS_LATITUDE = "latitude"
		const val PREFS_LATITUDE_DEFAULT = 40.7128.toFloat()
		const val PREFS_LONGITUDE = "longitude"
		const val PREFS_LONGITUDE_DEFAULT = (-74.006).toFloat()
		const val PREFS_ZOOM = "zoom"
		const val PREFS_ZOOM_DEFAULT = 7.0.toFloat()
		const val PREFS_SYNC_WEATHER = "syncWeather"
		const val PREFS_SYNC_WEATHER_DEFAULT = false
		const val WEATHER_SYNC_INTENT = "org.asteroidos.sync.WEATHER_SYNC_REQUEST_LISTENER"
	}

	init {
		Configuration.getInstance().load(mCtx, PreferenceManager.getDefaultSharedPreferences(mCtx))
		mSettings = mCtx.getSharedPreferences(PREFS_NAME, 0)
		mLatitude = mSettings.getFloat(PREFS_LATITUDE, PREFS_LATITUDE_DEFAULT)
		mLongitude = mSettings.getFloat(PREFS_LONGITUDE, PREFS_LONGITUDE_DEFAULT)
	}
}