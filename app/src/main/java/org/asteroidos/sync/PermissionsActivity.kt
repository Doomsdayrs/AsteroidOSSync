package org.asteroidos.sync

import android.Manifest.permission
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.os.bundleOf
import io.github.dreierf.materialintroscreen.MaterialIntroActivity
import io.github.dreierf.materialintroscreen.SlideFragment
import io.github.dreierf.materialintroscreen.SlideFragmentBuilder
import org.asteroidos.sync.services.NLService

class PermissionsActivity : MaterialIntroActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val pm: PackageManager = packageManager
		val hasBLE: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
		val am: ActivityManager = applicationContext.getSystemService(ACTIVITY_SERVICE) as ActivityManager
		val isLowRamDevice: Boolean = am.isLowRamDevice
		if (hasBLE) {
			if (!isLowRamDevice) {
				val welcomeFragment: SlideFragment = SlideFragmentBuilder()
						.backgroundColor(R.color.colorintroslide1)
						.buttonsColor(R.color.colorintroslide1button)
						.image(R.drawable.introslide1icon)
						.title(getString(R.string.intro_slide1_title))
						.description(getString(R.string.intro_slide1_subtitle))
						.build()

				val externalStorageFragment: SlideFragment = SlideFragmentBuilder()
						.backgroundColor(R.color.colorintroslide2)
						.buttonsColor(R.color.colorintroslide2button)
						.neededPermissions(arrayOf(permission.WRITE_EXTERNAL_STORAGE))
						.image(R.drawable.introslide2icon)
						.title(getString(R.string.intro_slide2_title))
						.description(getString(R.string.intro_slide2_subtitle))
						.build()

				val externalStorageFragmentShown: Boolean = (checkSelfPermission(
						this,
						permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
				val localizationFragment: SlideFragment = SlideFragmentBuilder()
						.backgroundColor(R.color.colorintroslide3)
						.buttonsColor(R.color.colorintroslide3button)
						.neededPermissions(arrayOf(permission.ACCESS_FINE_LOCATION))
						.image(R.drawable.introslide3icon)
						.title(getString(R.string.intro_slide3_title))
						.description(getString(R.string.intro_slide3_subtitle))
						.build()

				val localizationFragmentShown: Boolean = (checkSelfPermission(
						this,
						permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
				val notificationFragment = NotificationsSlide(this)
				val notificationFragmentShown: Boolean = notificationFragment.hasAnyPermissionsToGrant()
				val batteryOptimFragment = BatteryOptimSlide(this)
				val batteryOptimFragmentShown: Boolean = batteryOptimFragment.hasAnyPermissionsToGrant()
				val phoneStateFragment: SlideFragment = SlideFragmentBuilder()
						.backgroundColor(R.color.colorintroslide2)
						.buttonsColor(R.color.colorintroslide2button)
						.neededPermissions(arrayOf(permission.READ_PHONE_STATE, permission.READ_CALL_LOG, permission.READ_CONTACTS))
						.image(R.drawable.ic_ring_volume)
						.title(getString(R.string.intro_phonestateslide_title))
						.description(getString(R.string.intro_phonestateslide_subtitle))
						.build()
				val phoneStateFragmentShown: Boolean = ((checkSelfPermission(this,
						permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) ||
						(checkSelfPermission(this,
								permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) ||
						(checkSelfPermission(this,
								permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED))
				if ((externalStorageFragmentShown || localizationFragmentShown ||
								notificationFragmentShown || batteryOptimFragmentShown || phoneStateFragmentShown)) {
					addSlide(welcomeFragment)
					if (externalStorageFragmentShown) addSlide(externalStorageFragment)
					if (localizationFragmentShown) addSlide(localizationFragment)
					if (notificationFragmentShown) addSlide(notificationFragment)
					if (batteryOptimFragmentShown) addSlide(batteryOptimFragment)
					if (phoneStateFragmentShown) addSlide(phoneStateFragment)
				} else startMainActivity()
			} else {
				addSlide(AndroidGoSlide())
			}
		} else {
			addSlide(BLENotSupportedSlide())
		}
	}

	private fun startMainActivity() {
		startActivity(Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
		})
		finish()
	}

	override fun onFinish() {
		startMainActivity()
		super.onFinish()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == BATTERY_OPTIMIZATION_REQUEST || requestCode == NOTIFICATION_REQUEST) updateMessageButtonVisible()
		super.onActivityResult(requestCode, resultCode, data)
	}

	class NotificationsSlide(
			var mCtx: Context
	) : SlideFragment() {
		override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
			val bundle = bundleOf(
					"background_color" to R.color.colorintroslide4,
					"buttons_color" to R.color.colorintroslide4button,
					"image" to R.drawable.introslide4icon,
					"title" to mCtx.getString(R.string.intro_slide4_title),
					"description" to mCtx.getString(R.string.intro_slide4_subtitle)
			)
			arguments = bundle
			return super.onCreateView(inflater, container, savedInstanceState)
		}


		override fun hasAnyPermissionsToGrant(): Boolean {
			val cn = ComponentName((mCtx), NLService::class.java)
			val flat = Settings.Secure.getString(
					mCtx.contentResolver,
					"enabled_notification_listeners"
			)
			return (flat == null || !flat.contains(cn.flattenToString()))
		}

		override fun askForPermissions() = activity!!.startActivityForResult(
				Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
				NOTIFICATION_REQUEST
		)

		override fun canMoveFurther(): Boolean = !hasAnyPermissionsToGrant()
	}

	class BatteryOptimSlide(
			var mCtx: Context
	) : SlideFragment() {
		override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
			arguments = bundleOf(
					"background_color" to R.color.colorintroslide5,
					"buttons_color" to R.color.colorintroslide5button,
					"image" to R.drawable.introslide5icon,
					"title" to mCtx.getString(R.string.intro_slide5_title),
					"description" to mCtx.getString(R.string.intro_slide5_subtitle)
			)
			return super.onCreateView(inflater, container, savedInstanceState)
		}


		override fun hasAnyPermissionsToGrant(): Boolean {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				val packageName: String = mCtx.packageName
				val pm: PowerManager? = mCtx.getSystemService(POWER_SERVICE) as PowerManager?
				return (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))
			}
			return false
		}

		override fun askForPermissions() {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				val packageName: String = mCtx.packageName
				mCtx.getSystemService(POWER_SERVICE) as PowerManager

				activity!!.startActivityForResult(Intent().apply {
					action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
					data = Uri.parse("package:$packageName")
				}, BATTERY_OPTIMIZATION_REQUEST)
			}
		}

		override fun canMoveFurther(): Boolean {
			return !hasAnyPermissionsToGrant()
		}
	}

	class BLENotSupportedSlide : SlideFragment() {
		override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
			arguments = bundleOf(
					"background_color" to R.color.colorintroslideerror,
					"buttons_color" to R.color.colorintroslideerrorbutton,
					"image" to R.drawable.introslidebluetoothicon,
					"title" to inflater.context.getString(R.string.intro_slideerror_title),
					"description" to inflater.context.getString(R.string.intro_slideerror_subtitle)
			)
			return super.onCreateView(inflater, container, savedInstanceState)
		}

		override fun canMoveFurther(): Boolean {
			return false
		}
	}

	class AndroidGoSlide : SlideFragment() {
		override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
			val bundle = Bundle()
			bundleOf(
					"background_color" to R.color.colorintroslideerror,
					"buttons_color" to R.color.colorintroslideerrorbutton,
					"image" to R.drawable.introslidelowramicon,
					"title" to inflater.context.getString(R.string.intro_slideandroidgo_title),
					"description" to inflater.context.getString(R.string.intro_slideandroidgo_subtitle)
			)
			arguments = bundle
			return super.onCreateView(inflater, container, savedInstanceState)
		}

		override fun canMoveFurther(): Boolean {
			return false
		}
	}

	companion object {
		private const val BATTERY_OPTIMIZATION_REQUEST: Int = 0
		private const val NOTIFICATION_REQUEST: Int = 1
	}
}