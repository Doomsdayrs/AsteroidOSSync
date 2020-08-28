package org.asteroidos.sync.services

import android.app.Activity
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import org.asteroidos.sync.R
import java.util.*

class PhoneStateReceiver : BroadcastReceiver() {
	private var telephony: TelephonyManager? = null

	override fun onReceive(context: Context, intent: Intent) {
		if (Objects.equals(intent.action, TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
			val callStateService = CallStateService(context)
			telephony = context.getSystemService()
			telephony!!.listen(callStateService, PhoneStateListener.LISTEN_CALL_STATE)
		}
	}

	internal class CallStateService constructor(private val context: Context) : PhoneStateListener() {
		private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
		override fun onCallStateChanged(state: Int, incomingNumber: String) {
			when (state) {
				TelephonyManager.CALL_STATE_IDLE, TelephonyManager.CALL_STATE_OFFHOOK -> stopRinging()
				TelephonyManager.CALL_STATE_RINGING -> startRinging(incomingNumber)
			}
		}

		private fun getContact(number: String): String? {
			var contact: String? = null
			val cr: ContentResolver = context.contentResolver
			val uri: Uri = Uri.withAppendedPath(
					ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
					Uri.encode(number)
			)
			val cursor: Cursor? = cr.query(
					uri,
					arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
					null,
					null,
					null
			)
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					contact = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
				}
				cursor.close()
			}
			return contact
		}

		private fun startRinging(number: String) {
			val notificationPref: Boolean = prefs.getBoolean(PREF_SEND_CALL_STATE, true)
			if (notificationPref) {
				var contact: String? = getContact(number)
				if (contact == null) {
					contact = number
				}
				context.sendBroadcast(Intent("org.asteroidos.sync.NOTIFICATION_LISTENER").apply {
					putExtra("event", "posted")
					putExtra("packageName", "org.asteroidos.generic.dialer")
					putExtra("id", 56345)
					putExtra("appName", context.resources.getString(R.string.dialer))
					putExtra("appIcon", "ios-call")
					putExtra("summary", contact)
					putExtra("body", number)
					putExtra("vibration", "ringtone")
				})
			}
		}

		private fun stopRinging() {
			context.sendBroadcast(Intent("org.asteroidos.sync.NOTIFICATION_LISTENER").apply {
				putExtra("event", "removed")
				putExtra("id", 56345)
			})
		}

	}

	companion object {
		const val PREFS_NAME: String = "PhoneStatePreference"
		const val PREF_SEND_CALL_STATE: String = "PhoneCallNotificationForwarding"
	}
}