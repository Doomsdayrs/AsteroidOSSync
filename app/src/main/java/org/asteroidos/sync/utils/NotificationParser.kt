package org.asteroidos.sync.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.util.*

// Originally from https://github.com/matejdro/PebbleNotificationCenter-Android written by Matej Drobnič under the terms of the GPLv3
class NotificationParser(notification: Notification) {

	var summary: String? = null
	var body = ""

	init {
		if (tryParseNatively(notification)) throw  Exception("")
		getExtraBigData(notification)
	}

	@TargetApi(value = Build.VERSION_CODES.JELLY_BEAN)
	private fun tryParseNatively(notification: Notification): Boolean {
		val extras = notification.extras ?: return false
		if (parseMessageStyleNotification(notification, extras)) return true
		val textLinesSequence = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
		if (!textLinesSequence.isNullOrEmpty()) if (parseInboxNotification(extras)) return true
		if ((extras[Notification.EXTRA_TEXT] == null) && (extras[Notification.EXTRA_TEXT_LINES] == null) && (extras[Notification.EXTRA_BIG_TEXT] == null))
			return false
		val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
		val title = extras.getCharSequence(Notification.EXTRA_TITLE)
		if (bigTitle != null && (bigTitle.length < 40 || extras[Notification.EXTRA_TITLE] == null))
			summary = bigTitle.toString() else if (title != null) summary = title.toString()
		when {
			extras[Notification.EXTRA_TEXT_LINES] != null -> {
				val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
				val sb = StringBuilder()
				sb.append(body)
				if (lines != null) {
					for (line: CharSequence in lines) {
						sb.append(formatCharSequence(line))
						sb.append("\n\n")
					}
				}
				body = sb.toString().trim { it <= ' ' }
			}
			extras[Notification.EXTRA_BIG_TEXT] != null -> body = formatCharSequence(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
			else -> body = formatCharSequence(extras.getCharSequence(Notification.EXTRA_TEXT))
		}
		return true
	}

	private fun parseMessageStyleNotification(notification: Notification, extras: Bundle): Boolean {
		val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
				?: return false
		summary = formatCharSequence(messagingStyle.conversationTitle)
		if (summary!!.isEmpty()) summary = formatCharSequence(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
		if (summary!!.isEmpty()) summary = formatCharSequence(extras.getCharSequence(Notification.EXTRA_TITLE))
		if (summary == null) summary = ""
		val messagesDescending: List<NotificationCompat.MessagingStyle.Message> = ArrayList(messagingStyle.messages)
		messagesDescending.sortedWith { m1, m2 ->
			(m2.timestamp - m1.timestamp).toInt()
		}
		val sb = StringBuilder()
		body = ""
		for (message: NotificationCompat.MessagingStyle.Message in messagesDescending) {
			var sender: String
			if (message.sender == null) sender = formatCharSequence(messagingStyle.userDisplayName) else sender = formatCharSequence(message.sender)
			sb.append(sender)
			sb.append(": ")
			sb.append(message.text)
			sb.append("\n")
		}
		body = sb.toString().trim { it <= ' ' }
		return true
	}

	@TargetApi(value = Build.VERSION_CODES.JELLY_BEAN)
	private fun parseInboxNotification(extras: Bundle): Boolean {
		val summaryTextSequence = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
		val subTextSequence = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
		val titleSequence = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
		if (summaryTextSequence != null) summary = summaryTextSequence.toString() else if (subTextSequence != null) summary = subTextSequence.toString() else if (titleSequence != null) summary = titleSequence.toString() else return false
		val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
		val sb = StringBuilder()
		sb.append(body)
		if (lines != null) {
			for (line: CharSequence in lines) {
				sb.append(formatCharSequence(line))
				sb.append("\n\n")
			}
		}
		body = sb.toString().trim { it <= ' ' }
		return true
	}

	private fun formatCharSequence(sequence: CharSequence?): String {
		if (sequence == null) return ""
		if (sequence !is SpannableString) return sequence.toString()
		val spannableString = sequence
		var text = spannableString.toString()
		val spans = spannableString.getSpans(0, spannableString.length, StyleSpan::class.java)
		var amountOfBoldspans = 0
		for (i in spans.indices.reversed()) {
			val span = spans[i]
			if (span.style == Typeface.BOLD) amountOfBoldspans++
		}
		if (amountOfBoldspans == 1) {
			for (i in spans.indices.reversed()) {
				val span = spans[i]
				if (span.style == Typeface.BOLD) {
					text = insertString(text, spannableString.getSpanEnd(span))
					break
				}
			}
		}
		return text
	}

	private fun getExtraData(notification: Notification) {
		val views = notification.contentView ?: return
		parseRemoteView(views)
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private fun getExtraBigData(notification: Notification) {
		val views: RemoteViews?
		try {
			views = notification.bigContentView
		} catch (e: NoSuchFieldError) {
			getExtraData(notification)
			return
		}
		if (views == null) {
			getExtraData(notification)
			return
		}
		parseRemoteView(views)
	}

	@SuppressLint("PrivateApi")
	private fun parseRemoteView(views: RemoteViews) {
		try {
			val remoteViewsClass: Class<*> = RemoteViews::class.java
			val baseActionClass = Class.forName("android.widget.RemoteViews\$Action")
			val actionsField = remoteViewsClass.getDeclaredField("mActions")
			actionsField.isAccessible = true
			val actions = actionsField[views] as ArrayList<Any>
			val sb = StringBuilder()
			sb.append(body)
			for (action: Any in actions) {
				if (!action.javaClass.name.contains("\$ReflectionAction")) continue
				val typeField = action.javaClass.getDeclaredField("type")
				typeField.isAccessible = true
				val type = typeField.getInt(action)
				if (type != 9 && type != 10) continue
				var viewId = -1
				try {
					val idField = baseActionClass.getDeclaredField("viewId")
					idField.isAccessible = true
					viewId = idField.getInt(action)
				} catch (ignored: NoSuchFieldException) {
				}
				val valueField = action.javaClass.getDeclaredField("value")
				valueField.isAccessible = true
				val value: CharSequence? = valueField[action] as CharSequence
				if (((value == null) || (value == "...") ||
								isInteger(value.toString()) ||
								body.contains(value))) {
					continue
				}
				if (viewId == android.R.id.title) {
					if (summary == null || summary!!.length < value.length) summary = value.toString()
				} else {
					sb.append(formatCharSequence(value))
					sb.append("\n\n")
				}
			}
			body = sb.toString().trim { it <= ' ' }
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	companion object {
		private fun insertString(text: String, pos: Int): String =
				text.substring(
						0,
						pos
				).trim { it <= ' ' } + "\n".trim { it <= ' ' } +
						text.substring(pos).trim { it <= ' ' }

		private fun isInteger(input: String): Boolean {
			return try {
				input.toInt()
				true
			} catch (e: Exception) {
				false
			}
		}
	}
}