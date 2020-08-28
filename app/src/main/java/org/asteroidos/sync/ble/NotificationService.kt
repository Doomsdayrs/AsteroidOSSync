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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.idevicesinc.sweetblue.BleDevice
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.ReadWriteEvent
import org.asteroidos.sync.NotificationPreferences
import org.asteroidos.sync.NotificationPreferences.NotificationOption
import java.nio.charset.StandardCharsets
import java.util.*

// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated ReadWriteListener
class NotificationService(private val mCtx: Context, private val mDevice: BleDevice?) : BleDevice.ReadWriteListener {
	private var mNReceiver: NotificationReceiver? = null
	fun sync() {
		mDevice!!.enableNotify(notificationFeedbackCharAc)
		mNReceiver = NotificationReceiver()
		val filter = IntentFilter()
		filter.addAction("org.asteroidos.sync.NOTIFICATION_LISTENER")
		mCtx.registerReceiver(mNReceiver, filter)
		val i = Intent("org.asteroidos.sync.NOTIFICATION_LISTENER_SERVICE")
		i.putExtra("command", "refresh")
		mCtx.sendBroadcast(i)
	}

	fun unSync() {
		mDevice!!.disableNotify(notificationFeedbackCharAc)
		try {
			mCtx.unregisterReceiver(mNReceiver)
		} catch (ignored: IllegalArgumentException) {
		}
	}

	override fun onEvent(e: ReadWriteEvent) {
		if (!e.wasSuccess()) Log.e("NotificationService", e.status().toString())
	}

	internal inner class NotificationReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val event = intent.getStringExtra("event")
			if (event == "posted") {
				val packageName = intent.getStringExtra("packageName")
				NotificationPreferences.putPackageToSeen(context, packageName)
				val notificationOption = NotificationPreferences.getNotificationPreferenceForApp(context, packageName)
				if (notificationOption == NotificationOption.NO_NOTIFICATIONS) return
				val id = intent.getIntExtra("id", 0)
				val appName = intent.getStringExtra("appName")
				val appIcon = intent.getStringExtra("appIcon")
				val summary = intent.getStringExtra("summary")
				val body = intent.getStringExtra("body")
				var vibration: String = when (notificationOption) {
					NotificationOption.SILENT_NOTIFICATION -> "none"
					NotificationOption.NORMAL_VIBRATION, NotificationOption.DEFAULT -> "normal"
					NotificationOption.STRONG_VIBRATION -> "strong"
					NotificationOption.RINGTONE_VIBRATION -> "ringtone"
					else -> throw IllegalArgumentException("Not all options handled")
				}
				if (intent.hasExtra("vibration")) vibration = intent.getStringExtra("vibration")
				var xmlRequest = "<insert><id>$id</id>"
				if (!packageName.isEmpty()) xmlRequest += "<pn>$packageName</pn>"
				if (!vibration.isEmpty()) xmlRequest += "<vb>$vibration</vb>"
				if (!appName.isEmpty()) xmlRequest += "<an>$appName</an>"
				if (!appIcon.isEmpty()) xmlRequest += "<ai>$appIcon</ai>"
				if (!summary.isEmpty()) xmlRequest += "<su>$summary</su>"
				if (!body.isEmpty()) xmlRequest += "<bo>$body</bo>"
				xmlRequest += "</insert>"
				val data = xmlRequest.toByteArray(StandardCharsets.UTF_8)
				mDevice!!.write(notificationUpdateCharAc, data, this@NotificationService)
			} else if (event == "removed") {
				val id = intent.getIntExtra("id", 0)
				val xmlRequest = "<removed>" +
						"<id>" + id + "</id>" +
						"</removed>"
				val data = xmlRequest.toByteArray(StandardCharsets.UTF_8)
				mDevice!!.write(notificationUpdateCharAc, data, this@NotificationService)
			}
		}
	}

	companion object {
		private val notificationUpdateCharAc = UUID.fromString("00009001-0000-0000-0000-00a57e401d05")
		private val notificationFeedbackCharAc = UUID.fromString("00009002-0000-0000-0000-00a57e401d05")
	}
}