/*
 * Copyright (C) 2019 - Justus Tartz <git@jrtberlin.de>
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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.AudioManager

class SilentModeService(con: Context) : OnSharedPreferenceChangeListener {
	private val prefs: SharedPreferences
	private var notificationPref: Boolean = false
	private val context: Context
	private val am: AudioManager

	fun onConnect() {
		notificationPref = prefs.getBoolean(PREF_RINGER, false)
		if (notificationPref) {
			val editor = prefs.edit()
			editor.putInt(PREF_ORIG_RINGER, am.ringerMode)
			editor.apply()
			am.ringerMode = AudioManager.RINGER_MODE_SILENT
		}
	}

	fun onDisconnect() {
		notificationPref = prefs.getBoolean(PREF_RINGER, false)
		if (notificationPref) {
			val origRingerMode = prefs.getInt(PREF_ORIG_RINGER, AudioManager.RINGER_MODE_NORMAL)
			am.ringerMode = origRingerMode
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
		notificationPref = prefs.getBoolean(PREF_RINGER, false)
		am.ringerMode = if (notificationPref)
			AudioManager.RINGER_MODE_SILENT
		else
			prefs.getInt(PREF_ORIG_RINGER, am.ringerMode)
	}

	companion object {
		const val PREFS_NAME = "AppPreferences"
		const val PREF_RINGER = "PhoneRingModeOnConnection"
		private const val PREF_ORIG_RINGER = "OriginalRingMode"
	}

	init {
		prefs = con.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
		context = con
		prefs.registerOnSharedPreferenceChangeListener(this)
		am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	}
}