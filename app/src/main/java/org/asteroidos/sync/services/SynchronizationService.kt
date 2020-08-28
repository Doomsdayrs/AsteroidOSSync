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
package org.asteroidos.sync.services

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import androidx.core.app.NotificationCompat
import com.idevicesinc.sweetblue.*
import com.idevicesinc.sweetblue.BleDeviceConfig.BondFilter.CharacteristicEvent
import com.idevicesinc.sweetblue.BleDeviceConfig.BondFilter.StateChangeEvent
import com.idevicesinc.sweetblue.BleManagerConfig.ScanFilter.ScanEvent
import com.idevicesinc.sweetblue.BleNodeConfig.TaskTimeoutRequestFilter.Please.setTimeoutFor
import com.idevicesinc.sweetblue.BleNodeConfig.TaskTimeoutRequestFilter.TaskTimeoutRequestEvent
import com.idevicesinc.sweetblue.utils.Interval
import com.idevicesinc.sweetblue.utils.Uuids
import org.asteroidos.sync.BuildConfig
import org.asteroidos.sync.MainActivity
import org.asteroidos.sync.R
import org.asteroidos.sync.ble.*
import java.util.*

// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated StateListener
class SynchronizationService : Service(), BleDevice.StateListener {
	private var mNM: NotificationManager? = null
	private val NOTIFICATION: Int = 2725
	private val mBleMngr: BleManager by lazy {
		BleManager.get(application)
	}
	private var mDevice: BleDevice? = null
	private var mState: Int = STATUS_DISCONNECTED
	private var replyTo: Messenger? = null
	private var mScreenshotService: ScreenshotService? = null
	private var mWeatherService: WeatherService? = null
	private var mNotificationService: NotificationService? = null
	private var mMediaService: MediaService? = null
	private var mTimeService: TimeService? = null
	private var silentModeService: SilentModeService? = null
	private val mPrefs: SharedPreferences by lazy {
		getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
	}

	fun handleConnect() {
		if (mDevice == null) return
		if (mState == STATUS_CONNECTED || mState == STATUS_CONNECTING) return
		mDevice!!.setListener_State(this@SynchronizationService)
		mWeatherService = WeatherService(applicationContext, mDevice)
		mNotificationService = NotificationService(applicationContext, mDevice)
		mMediaService = MediaService(applicationContext, mDevice)
		mScreenshotService = ScreenshotService(applicationContext, mDevice)
		mTimeService = TimeService(applicationContext, mDevice)
		silentModeService = SilentModeService(applicationContext)
		mDevice!!.connect()
	}

	fun handleDisconnect() {
		if (mDevice == null) return
		if (mState == STATUS_DISCONNECTED) return
		mScreenshotService!!.unSync()
		mWeatherService!!.unSync()
		mNotificationService!!.unSync()
		mMediaService!!.unSync()
		mTimeService!!.unSync()
		mDevice!!.disconnect()
		silentModeService!!.onDisconnect()
	}

	fun handleReqBattery() {
		if (mDevice == null) return
		if (mState == STATUS_DISCONNECTED) return
		mDevice!!.read(Uuids.BATTERY_LEVEL) { result ->
			if (result.wasSuccess()) try {
				replyTo!!.send(Message.obtain(null, MSG_SET_BATTERY_PERCENTAGE, result.data().get(0).toInt(), 0))
			} catch (ignored: RemoteException) {
			} catch (ignored: NullPointerException) {
			}
		}
	}

	fun handleSetDevice(macAddress: String) {
		val editor: SharedPreferences.Editor = mPrefs.edit()
		editor.putString(MainActivity.PREFS_DEFAULT_MAC_ADDR, macAddress)
		if (macAddress.isEmpty()) {
			if (mState != STATUS_DISCONNECTED) {
				mScreenshotService!!.unSync()
				mWeatherService!!.unSync()
				mNotificationService!!.unSync()
				mMediaService!!.unSync()
				mTimeService!!.unSync()
				mDevice!!.disconnect()
				mDevice!!.unbond()
			}
			mDevice = null
			editor.putString(MainActivity.PREFS_DEFAULT_LOC_NAME, "")
		} else {
			mDevice = mBleMngr.getDevice(macAddress)
			val name: String = mDevice!!.name_normalized
			try {
				val answer: Message = Message.obtain(null, MSG_SET_LOCAL_NAME)
				answer.obj = name
				replyTo!!.send(answer)
				replyTo!!.send(Message.obtain(null, MSG_SET_STATUS, mState, 0))
			} catch (ignored: RemoteException) {
			} catch (ignored: NullPointerException) {
			}
			editor.putString(MainActivity.PREFS_DEFAULT_LOC_NAME, name)
		}
		editor.apply()
	}

	fun handleUpdate() {
		if (mDevice != null) {
			try {
				val answer: Message = Message.obtain(null, MSG_SET_LOCAL_NAME)
				answer.obj = mDevice!!.name_normalized
				replyTo!!.send(answer)
				replyTo!!.send(Message.obtain(null, MSG_SET_STATUS, mState, 0))
				mDevice!!.read(Uuids.BATTERY_LEVEL) { result ->
					if (result.wasSuccess()) try {
						replyTo!!.send(Message.obtain(null, MSG_SET_BATTERY_PERCENTAGE, result.data().get(0).toInt(), 0))
					} catch (ignored: RemoteException) {
					} catch (ignored: NullPointerException) {
					}
				}
			} catch (ignored: RemoteException) {
			} catch (ignored: NullPointerException) {
			}
		}
	}

	private class SynchronizationHandler(private val mService: SynchronizationService) : Handler() {
		override fun handleMessage(msg: Message) {
			mService.replyTo = msg.replyTo
			when (msg.what) {
				MSG_CONNECT -> mService.handleConnect()
				MSG_DISCONNECT -> mService.handleDisconnect()
				MSG_REQUEST_BATTERY_LIFE -> mService.handleReqBattery()
				MSG_SET_DEVICE -> mService.handleSetDevice(msg.obj as String)
				MSG_UPDATE -> mService.handleUpdate()
				else -> super.handleMessage(msg)
			}
		}
	}

	private val mMessenger: Messenger = Messenger(SynchronizationHandler(this))
	override fun onCreate() {
		mNM = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val notificationChannel = NotificationChannel(
					NOTIFICATION_CHANNEL_ID,
					"Synchronization Service",
					NotificationManager.IMPORTANCE_LOW
			)
			notificationChannel.description = "Connection status"
			notificationChannel.vibrationPattern = longArrayOf(0L)
			notificationChannel.setShowBadge(false)
			mNM!!.createNotificationChannel(notificationChannel)
		}
		mBleMngr.setConfig(BleManagerConfig().apply {
			forceBondDialog = true
			taskTimeoutRequestFilter = TaskTimeoutRequestFilter()
			defaultScanFilter = WatchesFilter()
			enableCrashResolver = true
			bondFilter = BondFilter()
			alwaysUseAutoConnect = true
			useLeTransportForBonding = true
			if (BuildConfig.DEBUG) loggingEnabled = true
		}
		)
		val defaultDevMacAddr: String? = mPrefs.getString(MainActivity.PREFS_DEFAULT_MAC_ADDR, "")
		val defaultLocalName: String? = mPrefs.getString(MainActivity.PREFS_DEFAULT_LOC_NAME, "")
		if (!defaultDevMacAddr!!.isEmpty()) {
			if (!mBleMngr.hasDevice(defaultDevMacAddr)) mBleMngr.newDevice(defaultDevMacAddr, defaultLocalName)
			mDevice = mBleMngr.getDevice(defaultDevMacAddr)
			mDevice!!.setListener_State(this@SynchronizationService)
			mWeatherService = WeatherService(applicationContext, mDevice)
			mNotificationService = NotificationService(applicationContext, mDevice)
			mMediaService = MediaService(applicationContext, mDevice)
			mScreenshotService = ScreenshotService(applicationContext, mDevice)
			mTimeService = TimeService(applicationContext, mDevice)
			silentModeService = SilentModeService(applicationContext)
			mDevice!!.connect()
		}
		updateNotification()
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
		return START_STICKY
	}

	private fun updateNotification() {
		var status: String? = getString(R.string.disconnected)
		if (mDevice != null) {
			if (mState == STATUS_CONNECTING) status = getString(R.string.connecting_formatted, mDevice!!.name_normalized)
			else if (mState == STATUS_CONNECTED) status = getString(R.string.connected_formatted, mDevice!!.name_normalized)
		}
		if (mDevice != null) {
			val contentIntent: PendingIntent = PendingIntent.getActivity(
					this,
					0,
					Intent(this, MainActivity::class.java),
					PendingIntent.FLAG_UPDATE_CURRENT
			)
			val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
					.setSmallIcon(R.drawable.ic_stat_name)
					.setContentTitle(getText(R.string.app_name))
					.setContentText(status)
					.setContentIntent(contentIntent)
					.setOngoing(true)
					.setPriority(Notification.PRIORITY_MIN)
					.setShowWhen(false)
					.build()
			mNM!!.notify(NOTIFICATION, notification)
			startForeground(NOTIFICATION, notification)
		}
	}

	override fun onDestroy() {
		if (mDevice != null) mDevice!!.disconnect()
		mNM!!.cancel(NOTIFICATION)
	}

	override fun onBind(intent: Intent): IBinder? = mMessenger.binder

	/* Bluetooth events handling */
	override fun onEvent(event: BleDevice.StateListener.StateEvent) {
		if (event.didEnter(BleDeviceState.INITIALIZED)) {
			mState = STATUS_CONNECTED
			updateNotification()
			try {
				replyTo!!.send(Message.obtain(null, MSG_SET_STATUS, STATUS_CONNECTED, 0))
			} catch (ignored: RemoteException) {
			} catch (ignored: NullPointerException) {
			}
			mDevice!!.mtu = 256
			event.device().enableNotify(Uuids.BATTERY_LEVEL) { e ->
				try {
					if (e.isNotification && (e.charUuid() == Uuids.BATTERY_LEVEL)) {
						val data: ByteArray = e.data()
						replyTo!!.send(Message.obtain(null, MSG_SET_BATTERY_PERCENTAGE, data.get(0).toInt(), 0))
					}
				} catch (ignored: RemoteException) {
				} catch (ignored: NullPointerException) {
				}
			}
			if (mScreenshotService != null) mScreenshotService!!.sync()
			if (mWeatherService != null) mWeatherService!!.sync()
			if (mNotificationService != null) mNotificationService!!.sync()
			if (mMediaService != null) mMediaService!!.sync()
			if (mTimeService != null) mTimeService!!.sync()
			if (silentModeService != null) silentModeService!!.onConnect()
		} else if (event.didEnter(BleDeviceState.DISCONNECTED)) {
			mState = STATUS_DISCONNECTED
			updateNotification()
			try {
				replyTo!!.send(Message.obtain(null, MSG_SET_STATUS, STATUS_DISCONNECTED, 0))
			} catch (ignored: RemoteException) {
			} catch (ignored: NullPointerException) {
			}
			if (mScreenshotService != null) mScreenshotService!!.sync()
			if (mWeatherService != null) mWeatherService!!.unSync()
			if (mNotificationService != null) mNotificationService!!.unSync()
			if (mMediaService != null) mMediaService!!.unSync()
			if (mTimeService != null) mTimeService!!.unSync()
			if (silentModeService != null) silentModeService!!.onDisconnect()
		} else if (event.didEnter(BleDeviceState.CONNECTING)) {
			mState = STATUS_CONNECTING
			updateNotification()
			try {
				replyTo!!.send(Message.obtain(null, MSG_SET_STATUS, STATUS_CONNECTING, 0))
			} catch (ignored: RemoteException) {
			} catch (ignored: NullPointerException) {
			}
		}
	}

	private class WatchesFilter : BleManagerConfig.ScanFilter {
		override fun onEvent(e: ScanEvent): BleManagerConfig.ScanFilter.Please {
			return BleManagerConfig.ScanFilter.Please.acknowledgeIf(e.advertisedServices().contains(UUID.fromString("00000000-0000-0000-0000-00a57e401d05")))
		}
	}

	private class TaskTimeoutRequestFilter : BleNodeConfig.TaskTimeoutRequestFilter {
		override fun onEvent(e: TaskTimeoutRequestEvent): BleNodeConfig.TaskTimeoutRequestFilter.Please =
				when {
					e.task() == BleTask.RESOLVE_CRASHES ->
						setTimeoutFor(Interval.secs(DEFAULT_CRASH_RESOLVER_TIMEOUT))
					e.task() == BleTask.BOND ->
						setTimeoutFor(Interval.secs(BOND_TASK_TIMEOUT))
					else -> DEFAULT_RETURN_VALUE
				}

		companion object {
			const val DEFAULT_TASK_TIMEOUT: Double = 12.5
			const val BOND_TASK_TIMEOUT: Double = 60.0
			const val DEFAULT_CRASH_RESOLVER_TIMEOUT: Double = 50.0
			private val DEFAULT_RETURN_VALUE: BleNodeConfig.TaskTimeoutRequestFilter.Please =
					setTimeoutFor(Interval.secs(DEFAULT_TASK_TIMEOUT))
		}
	}

	private class BondFilter : BleDeviceConfig.BondFilter {
		override fun onEvent(e: StateChangeEvent): BleDeviceConfig.BondFilter.Please {
			return BleDeviceConfig.BondFilter.Please.doNothing()
		}

		override fun onEvent(e: CharacteristicEvent): BleDeviceConfig.BondFilter.Please {
			return BleDeviceConfig.BondFilter.Please.doNothing()
		}
	}

	companion object {
		private const val NOTIFICATION_CHANNEL_ID: String = "synchronizationservice_channel_id_01"
		const val MSG_CONNECT: Int = 1
		const val MSG_DISCONNECT: Int = 2
		const val MSG_SET_LOCAL_NAME: Int = 3
		const val MSG_SET_STATUS: Int = 4
		const val MSG_SET_BATTERY_PERCENTAGE: Int = 5
		const val MSG_REQUEST_BATTERY_LIFE: Int = 6
		const val MSG_SET_DEVICE: Int = 7
		const val MSG_UPDATE: Int = 8
		const val STATUS_CONNECTED: Int = 1
		const val STATUS_DISCONNECTED: Int = 2
		const val STATUS_CONNECTING: Int = 3
	}
}