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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.core.os.postDelayed
import com.idevicesinc.sweetblue.BleDevice
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.ReadWriteEvent
import java.util.*

// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated ReadWriteListener
class TimeService(
		private val mCtx: Context,
		private val mDevice: BleDevice?
) : BleDevice.ReadWriteListener, OnSharedPreferenceChangeListener {
	private val mTimeSyncSettings: SharedPreferences
	private var mSReceiver: TimeSyncReqReceiver? = null
	private var alarmPendingIntent: PendingIntent? = null
	private var alarmMgr: AlarmManager? = null

	fun sync() {
		Handler().postDelayed(500) { updateTime() }

		// Register a broadcast handler to use for the alarm Intent
		// Also listen for TIME_CHANGED and TIMEZONE_CHANGED events
		mSReceiver = TimeSyncReqReceiver()

		mCtx.registerReceiver(mSReceiver, IntentFilter().apply {
			addAction(TIME_SYNC_INTENT)
			addAction(Intent.ACTION_TIME_CHANGED)
			addAction(Intent.ACTION_TIMEZONE_CHANGED)
		})

		// register an alarm to sync the time once a day
		val alarmIntent = Intent(TIME_SYNC_INTENT)
		alarmPendingIntent = PendingIntent.getBroadcast(mCtx, 0, alarmIntent, 0)
		alarmMgr = mCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
		alarmMgr!!.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_DAY,
				AlarmManager.INTERVAL_DAY, alarmPendingIntent)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) =
			updateTime()

	private fun updateTime() {
		if (mTimeSyncSettings.getBoolean(PREFS_SYNC_TIME, PREFS_SYNC_TIME_DEFAULT)) {
			val data = ByteArray(6)
			val c = Calendar.getInstance()
			data[0] = (c[Calendar.YEAR] - 1900).toByte()
			data[1] = c[Calendar.MONTH].toByte()
			data[2] = c[Calendar.DAY_OF_MONTH].toByte()
			data[3] = c[Calendar.HOUR_OF_DAY].toByte()
			data[4] = c[Calendar.MINUTE].toByte()
			data[5] = c[Calendar.SECOND].toByte()
			mDevice!!.write(timeSetCharac, data, this@TimeService)
		}
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

	override fun onEvent(e: ReadWriteEvent) {
		if (!e.wasSuccess()) Log.e("TimeService", e.status().toString())
	}

	internal inner class TimeSyncReqReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			updateTime()
		}
	}

	companion object {
		private val timeSetCharac = UUID.fromString("00005001-0000-0000-0000-00a57e401d05")
		const val PREFS_NAME = "TimePreference"
		const val PREFS_SYNC_TIME = "syncTime"
		const val PREFS_SYNC_TIME_DEFAULT = true
		const val TIME_SYNC_INTENT = "org.asteroidos.sync.TIME_SYNC_REQUEST_LISTENER"
	}

	init {
		mTimeSyncSettings = mCtx.getSharedPreferences(PREFS_NAME, 0)
		mTimeSyncSettings.registerOnSharedPreferenceChangeListener(this)
	}
}