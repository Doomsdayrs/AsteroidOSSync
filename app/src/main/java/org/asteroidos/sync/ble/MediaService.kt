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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState
import android.util.Log
import com.idevicesinc.sweetblue.BleDevice
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.ReadWriteEvent
import com.maxmpz.poweramp.player.PowerampAPI
import com.maxmpz.poweramp.player.PowerampAPIHelper
import org.asteroidos.sync.services.NLService
import java.nio.charset.StandardCharsets
import java.util.*

// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated ReadWriteListener
class MediaService(private val mCtx: Context, private val mDevice: BleDevice?) : BleDevice.ReadWriteListener, OnActiveSessionsChangedListener {
	private val mSettings: SharedPreferences by lazy {
		mCtx.getSharedPreferences(PREFS_NAME, 0)
	}
	private var mMediaController: MediaController? = null
	private var mMediaSessionManager: MediaSessionManager? = null
	fun sync() {
		mDevice!!.enableNotify(mediaCommandsCharac, commandsListener)
		try {
			mMediaSessionManager = mCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
			val controllers = mMediaSessionManager!!.getActiveSessions(ComponentName(mCtx, NLService::class.java))
			onActiveSessionsChanged(controllers)
			mMediaSessionManager!!.addOnActiveSessionsChangedListener(this, ComponentName(mCtx, NLService::class.java))
		} catch (e: SecurityException) {
			Log.w("MediaService", "No Notification Access")
		}
	}

	fun unSync() {
		mDevice!!.disableNotify(mediaCommandsCharac)
		if (mMediaSessionManager != null) mMediaSessionManager!!.removeOnActiveSessionsChangedListener(this)
		if (mMediaController != null) {
			try {
				mMediaController!!.unregisterCallback(mMediaCallback)
			} catch (ignored: IllegalArgumentException) {
			}
			Log.d("MediaService", "MediaController removed")
		}
	}

	private val commandsListener = BleDevice.ReadWriteListener { e ->
		if (e.isNotification && e.charUuid() == mediaCommandsCharac) {
			if (mMediaController != null) {
				val data = e.data()
				val isPoweramp = (mSettings.getString(PREFS_MEDIA_CONTROLLER_PACKAGE, PREFS_MEDIA_CONTROLLER_PACKAGE_DEFAULT)
						== PowerampAPI.PACKAGE_NAME)
				when (data[0]) {
					MEDIA_COMMAND_PREVIOUS -> if (isPoweramp) {
						PowerampAPIHelper.startPAService(mCtx, Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.PREVIOUS))
					} else {
						mMediaController!!.transportControls.skipToPrevious()
					}
					MEDIA_COMMAND_NEXT -> if (isPoweramp) {
						PowerampAPIHelper.startPAService(mCtx, Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT))
					} else {
						mMediaController!!.transportControls.skipToNext()
					}
					MEDIA_COMMAND_PLAY -> if (isPoweramp) {
						PowerampAPIHelper.startPAService(mCtx, Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.RESUME))
					} else {
						mMediaController!!.transportControls.play()
					}
					MEDIA_COMMAND_PAUSE -> if (isPoweramp) {
						PowerampAPIHelper.startPAService(mCtx, Intent(PowerampAPI.ACTION_API_COMMAND)
								.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.PAUSE))
					} else {
						mMediaController!!.transportControls.pause()
					}
				}
			} else {
				val mediaIntent = Intent(Intent.ACTION_MAIN)
				mediaIntent.addCategory(Intent.CATEGORY_APP_MUSIC)
				mediaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				mCtx.startActivity(mediaIntent)
			}
		}
	}

	override fun onEvent(e: ReadWriteEvent) {
		if (!e.wasSuccess()) Log.e("MediaService", e.status().toString())
	}

	/**
	 * Callback for the MediaController.
	 */
	private val mMediaCallback: MediaController.Callback = object : MediaController.Callback() {

		/**
		 * Helper method to safely get a text value from a [MediaMetadata] as a byte array
		 * (UTF-8 encoded).
		 *
		 *
		 * If the field is null, a zero length byte array will be returned.
		 *
		 * @param metadata the MediaMetadata (assumed to be non-null)
		 * @param fieldName the field name
		 * @return the field value as a byte array
		 */
		private fun getTextAsBytes(metadata: MediaMetadata, fieldName: String): ByteArray {
			val result: ByteArray
			val text = metadata.getText(fieldName)
			result = text?.toString()?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf(0)
			return result
		}

		override fun onMetadataChanged(metadata: MediaMetadata?) {
			super.onMetadataChanged(metadata)
			if (metadata != null) {
				mDevice!!.write(mediaArtistCharac,
						getTextAsBytes(metadata, MediaMetadata.METADATA_KEY_ARTIST),
						this@MediaService)
				mDevice.write(mediaAlbumCharac,
						getTextAsBytes(metadata, MediaMetadata.METADATA_KEY_ALBUM),
						this@MediaService)
				mDevice.write(mediaTitleCharac,
						getTextAsBytes(metadata, MediaMetadata.METADATA_KEY_TITLE),
						this@MediaService)
			}
		}

		override fun onPlaybackStateChanged(state: PlaybackState?) {
			super.onPlaybackStateChanged(state)
			val data = ByteArray(1)
			data[0] = (if (state?.state == PlaybackState.STATE_PLAYING) 1 else 0).toByte()
			mDevice!!.write(mediaPlayingCharac, data, this@MediaService)
		}

	}

	override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
		if (controllers!!.isNotEmpty()) {
			if (mMediaController != null && controllers[0].sessionToken != mMediaController!!.sessionToken) {
				// Detach current controller
				mMediaController!!.unregisterCallback(mMediaCallback)
				Log.d("MediaService", "MediaController removed")
				mMediaController = null
			}
			if (mMediaController == null) {
				// Attach new controller
				mMediaController = controllers[0]
				mMediaController!!.registerCallback(mMediaCallback)
				mMediaCallback.onMetadataChanged(mMediaController!!.metadata)
				if (mMediaController!!.playbackState != null) mMediaCallback.onPlaybackStateChanged(mMediaController!!.playbackState)
				Log.d("MediaService", "MediaController set: " + mMediaController!!.packageName)
				val editor = mSettings.edit()
				editor.putString(PREFS_MEDIA_CONTROLLER_PACKAGE, mMediaController!!.packageName)
				editor.apply()
			}
		} else {
			val data = byteArrayOf(0)
			mDevice!!.write(mediaArtistCharac, data, this@MediaService)
			mDevice.write(mediaAlbumCharac, data, this@MediaService)
			mDevice.write(mediaTitleCharac, data, this@MediaService)
		}
	}

	companion object {
		private val mediaTitleCharac = UUID.fromString("00007001-0000-0000-0000-00A57E401D05")
		private val mediaAlbumCharac = UUID.fromString("00007002-0000-0000-0000-00A57E401D05")
		private val mediaArtistCharac = UUID.fromString("00007003-0000-0000-0000-00A57E401D05")
		private val mediaPlayingCharac = UUID.fromString("00007004-0000-0000-0000-00A57E401D05")
		private val mediaCommandsCharac = UUID.fromString("00007005-0000-0000-0000-00A57E401D05")
		private const val MEDIA_COMMAND_PREVIOUS: Byte = 0x0
		private const val MEDIA_COMMAND_NEXT: Byte = 0x1
		private const val MEDIA_COMMAND_PLAY: Byte = 0x2
		private const val MEDIA_COMMAND_PAUSE: Byte = 0x3
		const val PREFS_NAME = "MediaPreferences"
		const val PREFS_MEDIA_CONTROLLER_PACKAGE = "media_controller_package"
		const val PREFS_MEDIA_CONTROLLER_PACKAGE_DEFAULT = "default"
	}

}