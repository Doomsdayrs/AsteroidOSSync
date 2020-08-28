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
package org.asteroidos.sync.services

import android.annotation.TargetApi
import android.app.Notification
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.os.postDelayed
import org.asteroidos.sync.utils.NotificationParser

class NLService : NotificationListenerService() {
	private val nlServiceReceiver: NLServiceReceiver by lazy {
		NLServiceReceiver()
	}
	private val iconFromPackage: MutableMap<String, String> by lazy {
		HashMap()
	}

	override fun onCreate() {
		super.onCreate()
		val filter = IntentFilter()
		filter.addAction("org.asteroidos.sync.NOTIFICATION_LISTENER_SERVICE")
		registerReceiver(nlServiceReceiver, filter)
		iconFromPackage.putAll(mapOf(
				"code.name.monkey.retromusic" to "ios-musical-notes",
				"com.android.chrome" to "logo-chrome",
				"com.android.dialer" to "ios-call",
				"com.android.mms" to "ios-text",
				"com.android.vending" to "md-appstore",
				"com.chrome.beta" to "logo-chrome",
				"com.chrome.dev" to "logo-chrome",
				"com.devhd.feedly" to "logo-rss",
				"com.dropbox.android" to "logo-dropbox",
				"com.facebook.groups" to "logo-facebook",
				"com.facebook.katana" to "logo-facebook",
				"com.facebook.Mentions" to "logo-facebook",
				"com.facebook.orca" to "ios-text",
				"com.facebook.work" to "logo-facebook",
				"com.google.android.apps.docs.editors.docs" to "ios-document",
				"com.google.android.apps.giant" to "md-analytics",
				"com.google.android.apps.maps" to "ios-map",
				"com.google.android.apps.messaging" to "ios-text",
				"com.google.android.apps.photos" to "ios-images",
				"com.google.android.apps.plus" to "logo-googleplus",
				"com.google.android.calendar" to "ios-calendar",
				"com.google.android.contacts" to "ios-contacts",
				"com.google.android.dialer" to "ios-call",
				"com.google.android.gm" to "ios-mail",
				"com.google.android.googlequicksearchbox" to "logo-google",
				"com.google.android.music" to "ios-musical-notes",
				"com.google.android.talk" to "ios-quote",
				"com.google.android.videos" to "ios-film",
				"com.google.android.youtube" to "logo-youtube",
				"com.instagram.android" to "logo-instagram",
				"com.instagram.boomerang" to "logo-instagram",
				"com.instagram.layout" to "logo-instagram",
				"com.jb.gosms" to "ios-text",
				"com.joelapenna.foursquared" to "logo-foursquare",
				"com.keylesspalace.tusky" to "md-mastodon",
				"com.keylesspalace.tusky.test" to "md-mastodon",
				"com.linkedin.android.jobs.jobseeker" to "logo-linkedin",
				"com.linkedin.android.learning" to "logo-linkedin",
				"com.linkedin.android" to "logo-linkedin",
				"com.linkedin.android.salesnavigator" to "logo-linkedin",
				"com.linkedin.Coworkers" to "logo-linkedin",
				"com.linkedin.leap" to "logo-linkedin",
				"com.linkedin.pulse" to "logo-linkedin",
				"com.linkedin.recruiter" to "logo-linkedin",
				"com.mattermost.rnbeta" to "logo-mattermost",
				"com.mattermost.rn" to "logo-mattermost",
				"com.maxfour.music" to "ios-musical-notes",
				"com.microsoft.office.lync15" to "logo-skype",
				"com.microsoft.xboxone.smartglass.beta" to "logo-xbox",
				"com.microsoft.xboxone.smartglass" to "logo-xbox",
				"com.noinnion.android.greader.reader" to "logo-rss",
				"com.pinterest" to "logo-pinterest",
				"com.playstation.mobilemessenger" to "logo-playstation",
				"com.playstation.remoteplay" to "logo-playstation",
				"com.playstation.video" to "logo-playstation",
				"com.reddit.frontpage" to "logo-reddit",
				"com.runtastic.android" to "ios-walk",
				"com.runtastic.android.pro2" to "ios-walk",
				"com.scee.psxandroid" to "logo-playstation",
				"com.sec.android.app.music" to "ios-musical-notes",
				"com.skype.android.access" to "logo-skype",
				"com.skype.raider" to "logo-skype",
				"com.snapchat.android" to "logo-snapchat",
				"com.sonyericsson.conversations" to "ios-text",
				"com.spotify.music" to "ios-musical-notes",
				"com.tinder" to "md-flame",
				"com.tumblr" to "logo-tumblr",
				"com.twitter.android" to "logo-twitter",
				"com.valvesoftware.android.steam.community" to "logo-steam",
				"com.vimeo.android.videoapp" to "logo-vimeo",
				"com.whatsapp" to "logo-whatsapp",
				"com.yahoo.mobile.client.android.atom" to "logo-yahoo",
				"com.yahoo.mobile.client.android.finance" to "logo-yahoo",
				"com.yahoo.mobile.client.android.im" to "logo-yahoo",
				"com.yahoo.mobile.client.android.mail" to "logo-yahoo",
				"com.yahoo.mobile.client.android.search" to "logo-yahoo",
				"com.yahoo.mobile.client.android.sportacular" to "logo-yahoo",
				"com.yahoo.mobile.client.android.weather" to "logo-yahoo",
				"de.number26.android" to "ios-card",
				"flipboard.app" to "logo-rss",
				"net.etuldan.sparss.floss" to "logo-rss",
				"net.frju.flym" to "logo-rss",
				"net.slideshare.mobile" to "logo-linkedin",
				"org.buffer.android" to "logo-buffer",
				"org.kde.kdeconnect_tp" to "md-phone-portrait",
				"org.telegram.messenger" to "ios-paper-plane",
				"org.thoughtcrime.securesms" to "logo-signal",
				"org.thunderdog.challegram" to "ios-paper-plane",
				"org.wordpress.android" to "logo-wordpress",
				"tv.twitch.android.app" to "logo-twitch",
				"ws.xsoh.etar" to "ios-calendar"
		)
		)
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(nlServiceReceiver)
		iconFromPackage.clear()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		val notification: Notification = sbn.notification
		var packageName: String? = sbn.packageName
		val allowedOngoingApps: Array<String> = arrayOf("com.google.android.apps.maps")
		if (((notification.priority < Notification.PRIORITY_DEFAULT) ||
						(((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
								&& !listOf(*allowedOngoingApps).contains(packageName))) ||
						(NotificationCompat.getLocalOnly(notification)) ||
						(NotificationCompat.isGroupSummary(notification)))) return
		val notifParser = NotificationParser(notification)
		var summary: String? = notifParser.summary
		var body: String? = notifParser.body
		val id: Int = sbn.id
		var appIcon: String? = iconFromPackage[packageName]
		var appName: String? = ""
		try {
			val pm: PackageManager = applicationContext.packageManager
			val ai: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
			appName = pm.getApplicationLabel(ai).toString()
		} catch (ignored: PackageManager.NameNotFoundException) {
		}
		summary = summary?.trim { it <= ' ' } ?: ""
		body = body?.trim { it <= ' ' } ?: ""
		if (packageName == null) packageName = ""
		if (appIcon == null) appIcon = ""

		Intent("org.asteroidos.sync.NOTIFICATION_LISTENER").apply {
			putExtra("event", "posted")
			putExtra("packageName", packageName)
			putExtra("id", id)
			putExtra("appName", appName)
			putExtra("appIcon", appIcon)
			putExtra("summary", summary)
			putExtra("body", body)
			sendBroadcast(this)
		}
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {
		Intent("org.asteroidos.sync.NOTIFICATION_LISTENER").apply {
			putExtra("event", "removed")
			putExtra("id", sbn.id)
			sendBroadcast(this)
		}
	}

	@TargetApi(Build.VERSION_CODES.N)
	override fun onListenerDisconnected() {
		// Notification listener disconnected - requesting rebind
		requestRebind(ComponentName(this, NotificationListenerService::class.java))
	}

	internal inner class NLServiceReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if ((intent.getStringExtra("command") == "refresh")) {
				Handler().postDelayed(500) {
					val notifs: Array<StatusBarNotification> = activeNotifications
					for (notif: StatusBarNotification in notifs) onNotificationPosted(notif)
				}
			}
		}
	}
}