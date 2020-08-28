package org.asteroidos.sync.fragments

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.asteroidos.sync.R
import org.asteroidos.sync.ble.WeatherService
import org.osmdroid.api.IGeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class PositionPickerFragment : Fragment() {
	private var mMapView: MapView? = null
	private var mSettings: SharedPreferences? = null
	private var mButton: Button? = null
	private var mWeatherSyncSettings: SharedPreferences? = null
	private var mWeatherSyncCheckBox: CheckBox? = null
	override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View? {
		mSettings = context!!.getSharedPreferences(WeatherService.Companion.PREFS_NAME, 0)
		return inflater.inflate(R.layout.fragment_position_picker, parent, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val latitude: Float = mSettings!!.getFloat(WeatherService.Companion.PREFS_LATITUDE, WeatherService.Companion.PREFS_LATITUDE_DEFAULT)
		val longitude: Float = mSettings!!.getFloat(WeatherService.Companion.PREFS_LONGITUDE, WeatherService.Companion.PREFS_LONGITUDE_DEFAULT)
		val zoom: Float = mSettings!!.getFloat(WeatherService.Companion.PREFS_ZOOM, WeatherService.Companion.PREFS_ZOOM_DEFAULT)
		mMapView = view.findViewById(R.id.map)
		mMapView.setTileSource(TileSourceFactory.MAPNIK)
		mMapView.setMultiTouchControls(true)
		mMapView.setBuiltInZoomControls(false)
		mMapView.setZoomRounding(true)
		mMapView.setMaxZoomLevel(13.0)
		mMapView.setMinZoomLevel(5.0)
		mMapView.getController().setZoom(zoom.toDouble())
		mMapView.getController().setCenter(GeoPoint(latitude, longitude))
		if ((ContextCompat.checkSelfPermission((activity)!!,
						Manifest.permission.ACCESS_FINE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED)) {
			ActivityCompat.requestPermissions((activity)!!, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
					WEATHER_LOCATION_PERMISSION_REQUEST)
		} else {
			val mLocationOverlay: MyLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mMapView)
			mLocationOverlay.enableMyLocation()
			mMapView.getOverlays().add(mLocationOverlay)
		}
		mButton = view.findViewById(R.id.positionPickerButton)
		mButton.setOnClickListener(object : View.OnClickListener {
			override fun onClick(v: View) {
				val center: IGeoPoint = mMapView.getMapCenter()
				val latitude: Float = center.latitude.toFloat()
				val longitude: Float = center.longitude.toFloat()
				val zoom: Float = mMapView.getZoomLevelDouble().toFloat()
				val editor: SharedPreferences.Editor = mSettings!!.edit()
				editor.putFloat(WeatherService.Companion.PREFS_LATITUDE, latitude)
				editor.putFloat(WeatherService.Companion.PREFS_LONGITUDE, longitude)
				editor.putFloat(WeatherService.Companion.PREFS_ZOOM, zoom)
				editor.apply()

				// Update the Weather after changing it
				activity!!.sendBroadcast(Intent(WeatherService.Companion.WEATHER_SYNC_INTENT))
				activity!!.onBackPressed()
			}
		})
		mWeatherSyncSettings = activity!!.getSharedPreferences(WeatherService.Companion.PREFS_NAME, 0)
		mWeatherSyncCheckBox = view.findViewById(R.id.autoLocationPickerButton)
		mWeatherSyncCheckBox.setChecked(mWeatherSyncSettings.getBoolean(WeatherService.Companion.PREFS_SYNC_WEATHER, WeatherService.Companion.PREFS_SYNC_WEATHER_DEFAULT))
		mWeatherSyncCheckBox.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
			override fun onCheckedChanged(ignored: CompoundButton, checked: Boolean) {
				if ((ContextCompat.checkSelfPermission((activity)!!,
								Manifest.permission.ACCESS_FINE_LOCATION)
								!= PackageManager.PERMISSION_GRANTED)) {
					ActivityCompat.requestPermissions((activity)!!, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
							WEATHER_LOCATION_SYNC_PERMISSION_REQUEST)
				} else {
					handleLocationToggle(mWeatherSyncCheckBox.isChecked())
				}
			}
		})
		mButton.setVisibility(if (mWeatherSyncCheckBox.isChecked()) View.INVISIBLE else View.VISIBLE)
	}

	override fun onResume() {
		super.onResume()
		mMapView!!.onResume()
	}

	override fun onPause() {
		super.onPause()
		mMapView!!.onPause()
	}

	private fun handleLocationToggle(enable: Boolean) {
		val editor: SharedPreferences.Editor = mWeatherSyncSettings!!.edit()
		editor.putBoolean(WeatherService.Companion.PREFS_SYNC_WEATHER, enable)
		editor.apply()
		mButton!!.visibility = if (enable) View.INVISIBLE else View.VISIBLE
		activity!!.sendBroadcast(Intent(WeatherService.Companion.WEATHER_SYNC_INTENT))
	}

	override fun onRequestPermissionsResult(requestCode: Int,
	                                               permissions: Array<String>, grantResults: IntArray) {
		when (requestCode) {
			WEATHER_LOCATION_SYNC_PERMISSION_REQUEST -> {

				// If request is cancelled, the result arrays are empty.
				if ((grantResults.size > 0
								&& grantResults.get(0) == PackageManager.PERMISSION_GRANTED)) {
					handleLocationToggle(mWeatherSyncCheckBox!!.isChecked)
				} else {
					handleLocationToggle(false)
					mWeatherSyncCheckBox!!.isChecked = false
				}
			}
			WEATHER_LOCATION_PERMISSION_REQUEST -> {
				if ((grantResults.size > 0
								&& grantResults.get(0) == PackageManager.PERMISSION_GRANTED)) {
					val mLocationOverlay: MyLocationNewOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mMapView)
					mLocationOverlay.enableMyLocation()
					mMapView!!.overlays.add(mLocationOverlay)
				}
			}
		}
	}

	companion object {
		val WEATHER_LOCATION_SYNC_PERMISSION_REQUEST: Int = 1
		val WEATHER_LOCATION_PERMISSION_REQUEST: Int = 2
	}
}