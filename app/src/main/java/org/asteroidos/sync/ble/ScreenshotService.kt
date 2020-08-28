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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.idevicesinc.sweetblue.BleDevice
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.ReadWriteEvent
import org.asteroidos.sync.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.experimental.and

// For clarity, we prefer having NOTIFICATION as a top level field
// Before upgrading to SweetBlue 3.0, we don't have an alternative to the deprecated ReadWriteListener
class ScreenshotService(private val mCtx: Context, private val mDevice: BleDevice?) : BleDevice.ReadWriteListener {
	private var mSReceiver: ScreenshotReqReceiver? = null
	private var mFirstNotify = true
	private var mDownloading = false
	private val mNM: NotificationManager =
			mCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	fun sync() {
		mDevice!!.enableNotify(screenshotContentCharac, contentListener)
		mSReceiver = ScreenshotReqReceiver()
		mCtx.registerReceiver(mSReceiver, IntentFilter().apply {
			addAction("org.asteroidos.sync.SCREENSHOT_REQUEST_LISTENER")
		})
		mDownloading = false
	}

	fun unSync() {
		mDevice!!.disableNotify(screenshotContentCharac)
		try {
			mCtx.unregisterReceiver(mSReceiver)
		} catch (ignored: IllegalArgumentException) {
		}
	}

	private val contentListener: BleDevice.ReadWriteListener = object : BleDevice.ReadWriteListener {
		private var progress = 0
		private var size = 0
		private var totalData: ByteArray = ByteArray(0)
		private var processUpdate: ScheduledExecutorService? = null
		override fun onEvent(e: ReadWriteEvent) {
			if (e.isNotification && e.charUuid() == screenshotContentCharac) {
				val data = e.data()
				if (mFirstNotify) {
					size = bytesToInt(data)
					totalData = ByteArray(size)
					mFirstNotify = false
					progress = 0
					processUpdate = Executors.newSingleThreadScheduledExecutor()
					processUpdate!!.scheduleWithFixedDelay({
						val notificationBuilder = NotificationCompat.Builder(mCtx, NOTIFICATION_CHANNEL_ID)
								.setContentTitle(mCtx.getText(R.string.screenshot))
								.setLocalOnly(true)
						notificationBuilder.setContentText(mCtx.getText(R.string.downloading))
						notificationBuilder.setSmallIcon(R.drawable.image_white)
						notificationBuilder.setProgress(size, progress, false)
						val notification = notificationBuilder.build()
						mNM.notify(NOTIFICATION, notification)
					}, 0, 1, TimeUnit.SECONDS)
				} else {
					if (data.size + progress <= totalData.size) System.arraycopy(
							data,
							0,
							totalData,
							progress,
							data.size
					)
					progress += data.size
					if (size == progress) {
						processUpdate!!.shutdown()
						val notificationBuilder = NotificationCompat.Builder(mCtx, NOTIFICATION_CHANNEL_ID)
								.setContentTitle(mCtx.getText(R.string.screenshot))
								.setLocalOnly(true)
						var fileName: Uri? = null
						try {
							fileName = createFile(totalData)
						} catch (ex: IOException) {
							ex.printStackTrace()
						}
						notificationBuilder.setContentText(mCtx.getText(R.string.downloaded))
						notificationBuilder.setLargeIcon(BitmapFactory.decodeByteArray(totalData, 0, size))
						notificationBuilder.setSmallIcon(R.drawable.image_white)
						val notificationIntent = Intent()
						notificationIntent.action = Intent.ACTION_VIEW
						notificationIntent.setDataAndType(fileName, "image/*")
						notificationIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
						val contentIntent = PendingIntent.getActivity(mCtx, 0, notificationIntent, 0)
						notificationBuilder.setContentIntent(contentIntent)
						mDownloading = false
						val notification = notificationBuilder.build()
						mNM.notify(NOTIFICATION, notification)
					}
				}
			}
		}
	}

	override fun onEvent(e: ReadWriteEvent) {
		if (!e.wasSuccess()) Log.e("ScreenshotService", e.status().toString())
	}

	@Throws(IOException::class)
	private fun createFile(totalData: ByteArray): Uri? {
		val dirStr = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/AsteroidOSSync"
		val uri: Uri?
		val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
		val fileName = File("$dirStr/Screenshot_$timeStamp.jpg")
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val resolver = mCtx.contentResolver
			val metaInfo = ContentValues()
			metaInfo.put(MediaStore.MediaColumns.DISPLAY_NAME, "Screenshot_$timeStamp.jpg")
			metaInfo.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
			metaInfo.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AsteroidOSSync")
			metaInfo.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
			metaInfo.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
			val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, metaInfo)!!
			val out = resolver.openOutputStream(imageUri)!!
			try {
				out.write(totalData)
			} catch (ioe: IOException) {
				ioe.printStackTrace()
			} finally {
				out.close()
				uri = imageUri
			}
		} else {
			val directory = File(dirStr)
			if (!directory.exists()) directory.mkdirs()
			try {
				val out = FileOutputStream(fileName)
				out.write(totalData)
				out.close()
				doMediaScan(fileName)
			} catch (e: IOException) {
				e.printStackTrace()
			}
			uri = FileProvider.getUriForFile(mCtx, mCtx.applicationContext.packageName + ".fileprovider", fileName)
		}
		return uri
	}

	private fun doMediaScan(file: File) {
		MediaScannerConnection.scanFile(mCtx, arrayOf(file.toString()), null
		) { path, uri ->
			Log.i("ExternalStorage", "Scanned $path:")
			Log.i("ExternalStorage", "-> uri=$uri")
		}
	}

	internal inner class ScreenshotReqReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (!mDownloading) {
				mFirstNotify = true
				mDownloading = true
				val data = ByteArray(1)
				data[0] = 0x0
				mDevice!!.write(screenshotRequestCharac, data, this@ScreenshotService)
			}
		}
	}

	companion object {
		private const val NOTIFICATION_CHANNEL_ID = "screenshotservice_channel_id_01"
		private val screenshotRequestCharac = UUID.fromString("00006001-0000-0000-0000-00a57e401d05")
		private val screenshotContentCharac = UUID.fromString("00006002-0000-0000-0000-00a57e401d05")
		private const val NOTIFICATION = 2726

		private fun bytesToInt(b: ByteArray): Int {
			var result = 0
			for (i in 3 downTo 0) {
				result = result shl 8
				result = result or ((b[i] and 0xFF.toByte()).toInt())
			}
			return result
		}
	}

	init {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Screenshot Service", NotificationManager.IMPORTANCE_MIN)
			notificationChannel.description = "Screenshot download"
			notificationChannel.vibrationPattern = longArrayOf(0L)
			mNM.createNotificationChannel(notificationChannel)
		}
	}
}