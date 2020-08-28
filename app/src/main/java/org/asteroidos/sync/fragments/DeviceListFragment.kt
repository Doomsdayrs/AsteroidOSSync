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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.idevicesinc.sweetblue.BleDevice
import org.asteroidos.sync.R
import java.util.*

class DeviceListFragment : Fragment(R.layout.fragment_device_list), View.OnClickListener, OnItemClickListener {
	private var mLeDeviceListAdapter: LeDeviceListAdapter? = null
	private var mDeviceListener: OnDefaultDeviceSelectedListener? = null
	private var mScanListener: OnScanRequestedListener? = null

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		fab.setOnClickListener(this)
		mLeDeviceListAdapter = LeDeviceListAdapter()
		device_list.adapter = mLeDeviceListAdapter
		device_list.onItemClickListener = this
	}

	/* Fab events */
	override fun onClick(view: View) {
		mScanListener!!.onScanRequested()
	}

	/* Device selection */
	override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
		val device: BleDevice? = mLeDeviceListAdapter!!.getDevice(i)
		if (device != null) mDeviceListener!!.onDefaultDeviceSelected(device.macAddress)
	}

	/* Scanning events handling */
	fun scanningStarted() {
		content!!.startRippleAnimation()
		searchingText!!.setText(R.string.searching)
	}

	fun scanningStopped() {
		content!!.stopRippleAnimation()
		val deviceCount: Int = mLeDeviceListAdapter!!.count
		if (deviceCount == 0) searchingText!!.setText(R.string.nothing_found) else if (deviceCount == 1) searchingText!!.setText(R.string.one_found) else searchingText!!.text = getString(R.string.n_found, deviceCount)
	}

	fun deviceDiscovered(dev: BleDevice) {
		mLeDeviceListAdapter!!.addDevice(dev)
		mLeDeviceListAdapter!!.notifyDataSetChanged()
	}

	fun deviceUndiscovered(dev: BleDevice) {
		mLeDeviceListAdapter!!.removeDevice(dev)
		mLeDeviceListAdapter!!.notifyDataSetChanged()
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		if (context is OnDefaultDeviceSelectedListener) mDeviceListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceListFragment.OnDeviceSelectedListener"))
		if (context is OnScanRequestedListener) mScanListener = context else throw ClassCastException((context.toString()
				+ " does not implement DeviceListFragment.OnScanRequestedListener"))
	}

	/* Adapter for holding devices found through scanning */
	private inner class LeDeviceListAdapter : BaseAdapter() {
		private val mLeDevices: ArrayList<BleDevice> = ArrayList()
		private val mInflator: LayoutInflater = activity!!.layoutInflater
		fun addDevice(device: BleDevice) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(device)
			}
		}

		fun removeDevice(device: BleDevice) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.remove(device)
			}
		}

		fun getDevice(position: Int): BleDevice {
			return mLeDevices.get(position)
		}

		fun clear() {
			mLeDevices.clear()
		}

		override fun getCount(): Int {
			return mLeDevices.size
		}

		override fun getItem(i: Int): Any {
			return mLeDevices.get(i)
		}

		override fun getItemId(i: Int): Long {
			return i.toLong()
		}

		override fun getView(i: Int, view: View, viewGroup: ViewGroup): View {
			var view: View = view
			val viewHolder: ViewHolder
			if (view == null) {
				view = mInflator.inflate(R.layout.device_list_item, viewGroup, false)
				viewHolder = ViewHolder()
				viewHolder.deviceName = view.findViewById(R.id.content)
				view.tag = viewHolder
			} else {
				viewHolder = view.tag as ViewHolder
			}
			val device: BleDevice = mLeDevices.get(i)
			val deviceName: String? = device.name_normalized
			if (!deviceName.isNullOrEmpty()) viewHolder.deviceName!!.text = deviceName else viewHolder.deviceName!!.setText(R.string.unknown_device)
			return view
		}

	}

	private class ViewHolder {
		var deviceName: TextView? = null
	}

	/* Notifies MainActivity when a device pairing or scanning is requested */
	interface OnDefaultDeviceSelectedListener {
		fun onDefaultDeviceSelected(macAddress: String?)
	}

	interface OnScanRequestedListener {
		fun onScanRequested()
	}
}