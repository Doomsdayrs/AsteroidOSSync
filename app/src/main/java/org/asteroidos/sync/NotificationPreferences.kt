package org.asteroidos.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.asteroidos.sync.NotificationPreferences.NotificationOption
import org.asteroidos.sync.NotificationPreferences.getOptionMap
import java.lang.reflect.Type
import java.util.*

object NotificationPreferences {
	private const val PREFS_NAME: String = "NotificationPreferences"
	private const val PREFS_NOTIFICATIONS: String = "notifications"
	private const val PREFS_SEEN_PACKAGES: String = "seenPackages"

	private fun getOptionMap(context: Context): MutableMap<String, NotificationOption> {
		val prefs: SharedPreferences = getPrefs(context)
		val notificationPrefsAsString: String? = prefs.getString(PREFS_NOTIFICATIONS, "{}")
		val gson = Gson()
		val notificationPrefs: Type = object : TypeToken<Map<String, NotificationOption>>() {}.type
		return gson.fromJson(notificationPrefsAsString, notificationPrefs)
	}

	private fun getPrefs(context: Context): SharedPreferences {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	fun getNotificationPreferenceForApp(context: Context, packageName: String?): NotificationOption {
		return getOptionMap(context)[packageName] ?: NotificationOption.DEFAULT
	}

	fun saveNotificationPreferenceForApp(context: Context, packageName: String, value: Int) {
		val map: MutableMap<String, NotificationOption> = getOptionMap(context)
		val option: NotificationOption = NotificationOption.fromInt(value)

		// this function gets fired a lot on scroll, don't save defaults if there's nothing set
		if (map[packageName] == null && option == NotificationOption.DEFAULT) return
		map[packageName] = option
		val editor: SharedPreferences.Editor = getPrefs(context).edit()
		val jsonString: String = Gson().toJson(map)
		editor.putString(PREFS_NOTIFICATIONS, jsonString)
		editor.apply()
	}

	fun seenPackageNames(context: Context): List<String> {
		val asString: String? = getPrefs(context).getString(PREFS_SEEN_PACKAGES, "[]")
		val asArray: Array<String> = Gson().fromJson(asString, Array<String>::class.java)
		return listOf(*asArray)
	}

	fun putPackageToSeen(context: Context, packageName: String) {
		val list: List<String> = seenPackageNames(context)
		if (list.contains(packageName)) return
		val array: ArrayList<String> = ArrayList(list)
		array.add(packageName)
		val editor: SharedPreferences.Editor = getPrefs(context).edit()
		editor.putString(PREFS_SEEN_PACKAGES, Gson().toJson(array))
		editor.apply()
	}

	enum class NotificationOption(private val value: Int) {
		DEFAULT(0), NO_NOTIFICATIONS(1), SILENT_NOTIFICATION(2), NORMAL_VIBRATION(3), STRONG_VIBRATION(4), RINGTONE_VIBRATION(5);

		fun asInt(): Int {
			return value
		}

		companion object {
			fun fromInt(x: Int): NotificationOption {
				when (x) {
					0 -> return DEFAULT
					1 -> return NO_NOTIFICATIONS
					2 -> return SILENT_NOTIFICATION
					3 -> return NORMAL_VIBRATION
					4 -> return STRONG_VIBRATION
					5 -> return RINGTONE_VIBRATION
				}
				throw IllegalArgumentException("No such NotificationOption: " + x)
			}
		}
	}
}