package org.asteroidos.sync

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.*
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.idevicesinc.sweetblue.BleManager
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener.DiscoveryEvent
import com.idevicesinc.sweetblue.BleManagerState
import com.idevicesinc.sweetblue.ManagerStateListener
import com.idevicesinc.sweetblue.utils.BluetoothEnabler
import com.idevicesinc.sweetblue.utils.Interval
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.asteroidos.sync.fragments.AppListFragment
import org.asteroidos.sync.fragments.DeviceDetailFragment
import org.asteroidos.sync.fragments.DeviceDetailFragment.*
import org.asteroidos.sync.fragments.DeviceListFragment
import org.asteroidos.sync.fragments.DeviceListFragment.OnDefaultDeviceSelectedListener
import org.asteroidos.sync.fragments.DeviceListFragment.OnScanRequestedListener
import org.asteroidos.sync.fragments.PositionPickerFragment
import org.asteroidos.sync.services.SynchronizationService
import org.asteroidos.sync.utils.AppInfo
import org.asteroidos.sync.utils.AppInfoHelper
import java.util.*

// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated StateListener
class MainActivity
	: AppCompatActivity(R.layout.activity_main), OnDefaultDeviceSelectedListener,
		OnScanRequestedListener, OnDefaultDeviceUnselectedListener,
		OnConnectRequestedListener, BleManager.DiscoveryListener,
		OnAppSettingsClickedListener, OnLocationSettingsClickedListener, OnUpdateListener {

	private val mBleMngr: BleManager by lazy {
		BleManager.get(application)
	}
	private var mListFragment: DeviceListFragment? = null
	private var mDetailFragment: DeviceDetailFragment? = null
	private var mPreviousFragment: Fragment? = null
	var mSyncServiceMessenger: Messenger? = null
	private var mSyncServiceIntent: Intent? = null
	private val mDeviceDetailMessenger: Messenger = Messenger(SynchronizationHandler(this))
	private var mStatus: Int = SynchronizationService.STATUS_DISCONNECTED
	private val mPrefs: SharedPreferences by lazy {
		getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
	}

	/* Synchronization service events handling */
	private val mConnection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(className: ComponentName,
		                                service: IBinder) {
			mSyncServiceMessenger = Messenger(service)
			onUpdateRequested()
		}

		override fun onServiceDisconnected(className: ComponentName) {
			mSyncServiceMessenger = null
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val defaultDevMacAddr: String? = mPrefs.getString(PREFS_DEFAULT_MAC_ADDR, "")
		GlobalScope.launch {
			appInfoList = AppInfoHelper.getPackageInfo(this@MainActivity)
		}

		/* Start and/or attach to the Synchronization Service */mSyncServiceIntent = Intent(this, SynchronizationService::class.java)
		startService(mSyncServiceIntent)
		BluetoothEnabler.start(this)
		mBleMngr.setListener_State(ManagerStateListener { event ->
			if (event.didExit(BleManagerState.SCANNING)) {
				if (mListFragment != null) mListFragment!!.scanningStopped() else if (mDetailFragment != null) mDetailFragment!!.scanningStopped()
			} else if (event.didEnter(BleManagerState.SCANNING)) {
				if (mListFragment != null) mListFragment!!.scanningStarted() else if (mDetailFragment != null) mDetailFragment!!.scanningStarted()
			}
		})
		mBleMngr.listener_Discovery = this


		if (savedInstanceState == null) {
			val f: Fragment
			if (defaultDevMacAddr!!.isEmpty()) {
				mListFragment = DeviceListFragment()
				f = mListFragment!!
				onScanRequested()
			} else {
				title = mPrefs.getString(PREFS_DEFAULT_LOC_NAME, "")
				mDetailFragment = DeviceDetailFragment()
				f = mDetailFragment!!
			}
			val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
			ft.add(R.id.flContainer, f)
			ft.commit()
		}
	}

	public override fun onDestroy() {
		super.onDestroy()
		if (mStatus != SynchronizationService.Companion.STATUS_CONNECTED) stopService(mSyncServiceIntent)
	}

	/* Fragments switching */
	override fun onDefaultDeviceSelected(macAddress: String?) {
		mDetailFragment = DeviceDetailFragment()
		supportFragmentManager
				.beginTransaction()
				.replace(R.id.flContainer, mDetailFragment!!)
				.commit()
		try {
			val msg: Message = Message.obtain(null, SynchronizationService.MSG_SET_DEVICE)
			msg.obj = macAddress
			msg.replyTo = mDeviceDetailMessenger
			mSyncServiceMessenger!!.send(msg)
		} catch (ignored: RemoteException) {
		}
		onConnectRequested()
		mListFragment = null
	}

	override fun onDefaultDeviceUnselected() {
		mListFragment = DeviceListFragment()
		supportFragmentManager
				.beginTransaction()
				.replace(R.id.flContainer, mListFragment!!)
				.commit()
		try {
			val msg: Message = Message.obtain(null, SynchronizationService.MSG_SET_DEVICE)
			msg.obj = ""
			msg.replyTo = mDeviceDetailMessenger
			mSyncServiceMessenger!!.send(msg)
		} catch (ignored: RemoteException) {
		}
		mDetailFragment = null
		setTitle(R.string.app_name)
	}

	override fun onUpdateRequested() {
		try {
			val msg: Message = Message.obtain(null, SynchronizationService.MSG_UPDATE)
			msg.replyTo = mDeviceDetailMessenger
			if (mSyncServiceMessenger != null) mSyncServiceMessenger!!.send(msg)
		} catch (ignored: RemoteException) {
		}
	}

	override fun onConnectRequested() {
		try {
			val msg: Message = Message.obtain(null, SynchronizationService.MSG_CONNECT)
			msg.replyTo = mDeviceDetailMessenger
			mSyncServiceMessenger!!.send(msg)
		} catch (ignored: RemoteException) {
		}
	}

	override fun onDisconnectRequested() {
		try {
			val msg: Message = Message.obtain(null, SynchronizationService.MSG_DISCONNECT)
			msg.replyTo = mDeviceDetailMessenger
			mSyncServiceMessenger!!.send(msg)
		} catch (ignored: RemoteException) {
		}
	}

	override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
		if (menuItem.itemId == android.R.id.home) onBackPressed()
		return (super.onOptionsItemSelected(menuItem))
	}

	override fun onBackPressed() {
		val fm: FragmentManager = supportFragmentManager
		if (fm.backStackEntryCount > 0) {
			fm.popBackStack()
			title = mPrefs.getString(PREFS_DEFAULT_LOC_NAME, "")
			supportActionBar?.setDisplayHomeAsUpEnabled(false)
		} else finish()
		try {
			mDetailFragment = mPreviousFragment as DeviceDetailFragment?
		} catch (ignored1: ClassCastException) {
			try {
				mListFragment = mPreviousFragment as DeviceListFragment?
			} catch (ignored2: ClassCastException) {
			}
		}
	}

	override fun onAppSettingsClicked() {
		val f: Fragment = AppListFragment()
		val fm: FragmentManager = supportFragmentManager
		val ft: FragmentTransaction = fm.beginTransaction()
		if (mDetailFragment != null) {
			mPreviousFragment = mDetailFragment
			mDetailFragment = null
		}
		if (mListFragment != null) {
			mPreviousFragment = mListFragment
			mListFragment = null
		}
		ft.replace(R.id.flContainer, f)
		ft.addToBackStack(null)
		ft.commit()
		title = getString(R.string.notifications_settings)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
	}

	override fun onLocationSettingsClicked() {
		val f: Fragment = PositionPickerFragment()
		val fm: FragmentManager = supportFragmentManager
		val ft: FragmentTransaction = fm.beginTransaction()
		if (mDetailFragment != null) {
			mPreviousFragment = mDetailFragment
			mDetailFragment = null
		}
		if (mListFragment != null) {
			mPreviousFragment = mListFragment
			mListFragment = null
		}
		ft.replace(R.id.flContainer, f)
		ft.addToBackStack(null)
		ft.commit()
		title = getString(R.string.weather_settings)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
	}

	private fun handleSetLocalName(name: String) {
		if (mDetailFragment != null) mDetailFragment!!.setLocalName(name)
	}

	private fun handleSetStatus(status: Int) {
		if (mDetailFragment != null) {
			mDetailFragment!!.setStatus(status)
			if (status == SynchronizationService.STATUS_CONNECTED) {
				try {
					val batteryMsg: Message = Message.obtain(null, SynchronizationService.MSG_REQUEST_BATTERY_LIFE)
					batteryMsg.replyTo = mDeviceDetailMessenger
					mSyncServiceMessenger!!.send(batteryMsg)
				} catch (ignored: RemoteException) {
				}
			}
			mStatus = status
		}
	}

	private fun handleBatteryPercentage(percentage: Int) {
		if (mDetailFragment != null) mDetailFragment!!.setBatteryPercentage(percentage)
	}

	override fun onEvent(event: DiscoveryEvent) {
		if (mListFragment == null) return
		if (event.was(BleManager.DiscoveryListener.LifeCycle.DISCOVERED)) mListFragment!!.deviceDiscovered(event.device()) else if (event.was(BleManager.DiscoveryListener.LifeCycle.UNDISCOVERED)) mListFragment!!.deviceUndiscovered(event.device())
	}

	override fun onScanRequested() {
		mBleMngr.turnOn()
		mBleMngr.undiscoverAll()
		mBleMngr.startScan(Interval.secs(10.0))
	}

	override fun onResume() {
		super.onResume()
		mBleMngr.onResume()
		bindService(mSyncServiceIntent, mConnection, BIND_AUTO_CREATE)
	}

	override fun onPause() {
		super.onPause()
		mBleMngr.onPause()
		unbindService(mConnection)
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		delegate.onConfigurationChanged(newConfig)
		when (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
			Configuration.UI_MODE_NIGHT_NO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
			Configuration.UI_MODE_NIGHT_YES -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
		}
		finish()
		overridePendingTransition(0, 0)
		startActivity(intent)
	}

	private class SynchronizationHandler(private val mActivity: MainActivity) : Handler() {
		override fun handleMessage(msg: Message) {
			when (msg.what) {
				SynchronizationService.MSG_SET_LOCAL_NAME -> mActivity.handleSetLocalName(msg.obj as String)
				SynchronizationService.MSG_SET_STATUS -> mActivity.handleSetStatus(msg.arg1)
				SynchronizationService.MSG_SET_BATTERY_PERCENTAGE -> mActivity.handleBatteryPercentage(msg.arg1)
				else -> super.handleMessage(msg)
			}
		}
	}

	companion object {
		lateinit var appInfoList: ArrayList<AppInfo>
		const val PREFS_NAME: String = "MainPreferences"
		const val PREFS_DEFAULT_MAC_ADDR: String = "defaultMacAddress"
		const val PREFS_DEFAULT_LOC_NAME: String = "defaultLocalName"
	}
}