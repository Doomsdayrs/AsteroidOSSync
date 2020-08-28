/*
 * Copyright (C) 2016 - Florent Revest <revestflo@gmail.com>
 *                      Doug Koellmer <dougkoellmer@hotmail.com>
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
package org.asteroidos.sync.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.widget.CompoundButton
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import org.asteroidos.sync.R
import org.asteroidos.sync.ble.SilentModeService
import org.asteroidos.sync.ble.TimeService
import org.asteroidos.sync.services.PhoneStateReceiver
import org.asteroidos.sync.services.SynchronizationService

class DeviceDetailFragment : Fragment(R.layout.fragment_device_detail) {

	private val mTimeSyncSettings: SharedPreferences by lazy {
		activity!!.getSharedPreferences(TimeService.PREFS_NAME, 0)
	}
	private val mSilenceModeSettings: SharedPreferences by lazy {
		activity!!.getSharedPreferences(SilentModeService.PREFS_NAME, Context.MODE_PRIVATE)
	}
	private val mCallStateSettings: SharedPreferences by lazy {
		activity!!.getSharedPreferences(PhoneStateReceiver.PREFS_NAME, Context.MODE_PRIVATE)
	}
	private var mConnected: Boolean = false
	private var mStatus: Int = SynchronizationService.STATUS_DISCONNECTED
	private var mBatteryPercentage: Int = 100
	private var mDeviceListener: OnDefaultDeviceUnselectedListener? = null
	private var mConnectListener: OnConnectRequestedListener? = null
	private var mAppSettingsListener: OnAppSettingsClickedListener? = null
	private var mLocationSettingsListener: OnLocationSettingsClickedListener? = null
	private var mUpdateListener: OnUpdateListener? = null
	override fun onResume() {
		super.onResume()
		mUpdateListener!!.onUpdateRequested()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setHasOptionsMenu(true)
	}

	@SuppressLint("SetTextI18n")
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		fab.setOnClickListener(View.OnClickListener { if (mConnected) mConnectListener!!.onDisconnectRequested() else mConnectListener!!.onConnectRequested() })
		info_battery.setText("$mBatteryPercentage %")
		card_view1.setOnClickListener { mLocationSettingsListener!!.onLocationSettingsClicked() }
		val findCard: CardView = view.findViewById(R.id.card_view2)
		findCard.setOnClickListener {
			val iremove: Intent = Intent("org.asteroidos.sync.NOTIFICATION_LISTENER")
			iremove.putExtra("event", "removed")
			iremove.putExtra("id", -0x5a81bfe3)
			activity!!.sendBroadcast(iremove)
			activity!!.sendBroadcast(Intent("org.asteroidos.sync.NOTIFICATION_LISTENER").apply {
				putExtra("event", "posted")
				putExtra("packageName", "org.asteroidos.sync.findmywatch")
				putExtra("id", -0x5a81bfe3)
				putExtra("appName", getString(R.string.app_name))
				putExtra("appIcon", "ios-watch-vibrating")
				putExtra("summary", getString(R.string.watch_finder))
				putExtra("body", getString(R.string.phone_is_searching))
			})
		}
		card_view3.setOnClickListener { activity!!.sendBroadcast(Intent("org.asteroidos.sync.SCREENSHOT_REQUEST_LISTENER")) }
		card_view4.setOnClickListener { mAppSettingsListener!!.onAppSettingsClicked() }
		timeSyncCheckBox.setChecked(mTimeSyncSettings.getBoolean(TimeService.PREFS_SYNC_TIME, TimeService.PREFS_SYNC_TIME_DEFAULT))
		timeSyncCheckBox.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
			override fun onCheckedChanged(ignored: CompoundButton, checked: Boolean) {
				val editor: SharedPreferences.Editor = mTimeSyncSettings.edit()
				editor.putBoolean(TimeService.PREFS_SYNC_TIME, timeSyncCheckBox.isChecked())
				editor.apply()
			}
		})
		SilentModeCheckBox.setChecked(mSilenceModeSettings.getBoolean(SilentModeService.PREF_RINGER, false))
		SilentModeCheckBox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
			val editor: SharedPreferences.Editor = mSilenceModeSettings.edit()
			editor.putBoolean(SilentModeService.PREF_RINGER, isChecked)
			editor.apply()
		})
		CallStateServiceCheckBox.setChecked(mCallStateSettings.getBoolean(PhoneStateReceiver.PREF_SEND_CALL_STATE, true))
		CallStateServiceCheckBox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
			val editor: SharedPreferences.Editor = mCallStateSettings.edit()
			editor.putBoolean(PhoneStateReceiver.PREF_SEND_CALL_STATE, isChecked)
			editor.apply()
		})
		setStatus(mStatus)
	}

	override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
		inflater.inflate(R.menu.device_detail_manu, menu)
		super.onCreateOptionsMenu(menu, inflater)
	}

	override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
		if (menuItem.itemId == R.id.unpairButton) mDeviceListener!!.onDefaultDeviceUnselected()
		return (super.onOptionsItemSelected(menuItem))
	}

	fun setLocalName(name: String?) {
		activity!!.title = name
	}

	fun setStatus(status: Int) {
		mStatus = status
		when (status) {
			SynchronizationService.Companion.STATUS_CONNECTED -> {
				device_disconnected_placeholder!!.visibility = View.GONE
				device_connected_content!!.visibility = View.VISIBLE
				fab!!.setImageResource(R.drawable.bluetooth_disconnect)
				mConnected = true
				setMenuVisibility(true)
			}
			SynchronizationService.Companion.STATUS_DISCONNECTED -> {
				device_disconnected_placeholder!!.visibility = View.VISIBLE
				device_connected_content!!.visibility = View.GONE
				info_disconnected!!.setText(R.string.disconnected)
				fab!!.setImageResource(R.drawable.bluetooth_connect)
				mConnected = false
				setMenuVisibility(true)
			}
			SynchronizationService.Companion.STATUS_CONNECTING -> {
				device_disconnected_placeholder!!.visibility = View.VISIBLE
				device_connected_content!!.visibility = View.GONE
				info_disconnected!!.setText(R.string.connecting)
				setMenuVisibility(true)
			}
			else -> setMenuVisibility(false)
		}
	}

	@SuppressLint("SetTextI18n")
	fun setBatteryPercentage(percentage: Int) {
		try {
			info_battery!!.text = percentage.toString() + " %"
			mBatteryPercentage = percentage
		} catch (ignore: IllegalStateException) {
		}
	}

	fun scanningStarted() {
		if (mStatus == SynchronizationService.Companion.STATUS_DISCONNECTED) info_disconnected!!.setText(R.string.scanning)
	}

	fun scanningStopped() {
		if (mStatus == SynchronizationService.Companion.STATUS_DISCONNECTED) info_disconnected!!.setText(R.string.disconnected)
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		if (context is OnDefaultDeviceUnselectedListener) mDeviceListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceDetailFragment.OnDefaultDeviceUnselectedListener"))
		if (context is OnConnectRequestedListener) mConnectListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceDetailFragment.OnConnectRequestedListener"))
		if (context is OnAppSettingsClickedListener) mAppSettingsListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceDetailFragment.OnAppSettingsClickedListener"))
		if (context is OnLocationSettingsClickedListener) mLocationSettingsListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceDetailFragment.OnLocationSettingsClickedListener"))
		if (context is OnUpdateListener) mUpdateListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceDetailFragment.onUpdateListener"))
	}

	/* Notifies MainActivity when a device unpairing is requested */
	interface OnDefaultDeviceUnselectedListener {
		fun onDefaultDeviceUnselected()
	}

	interface OnAppSettingsClickedListener {
		fun onAppSettingsClicked()
	}

	interface OnLocationSettingsClickedListener {
		fun onLocationSettingsClicked()
	}

	interface OnConnectRequestedListener {
		fun onConnectRequested()
		fun onDisconnectRequested()
	}

	interface OnUpdateListener {
		fun onUpdateRequested()
	}
}