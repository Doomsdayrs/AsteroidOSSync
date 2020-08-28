package org.asteroidos.sync.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.asteroidos.sync.MainActivity
import java.util.*

class AutoStartService : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (Objects.equals(intent.action, Intent.ACTION_BOOT_COMPLETED)) {
			val prefs: SharedPreferences = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
			val defaultDevMacAddr: String? = prefs.getString(MainActivity.PREFS_DEFAULT_MAC_ADDR, "")
			if (defaultDevMacAddr!!.isNotEmpty()) {
				context.startService(Intent(context, SynchronizationService::class.java))
			}
		}
	}
}