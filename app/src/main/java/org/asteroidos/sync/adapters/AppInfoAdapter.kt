package org.asteroidos.sync.adapters

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import org.asteroidos.sync.NotificationPreferences
import org.asteroidos.sync.NotificationPreferences.NotificationOption
import org.asteroidos.sync.R
import org.asteroidos.sync.utils.AppInfo
import java.util.*

// copied from https://github.com/jensstein/oandbackup, used under MIT license
class AppInfoAdapter constructor(context: Context, layout: Int, items: ArrayList<AppInfo>) : ArrayAdapter<AppInfo?>(context, layout, (items)!!) {

	private val items: ArrayList<AppInfo?> = ArrayList(items)
	private var iconSize: Int = 0
	private val layout: Int = layout

	init {
		iconSize = try {
			val metrics = DisplayMetrics()
			(context as Activity).windowManager.defaultDisplay.getMetrics(metrics)
			32 * metrics.density.toInt()
		} catch (e: ClassCastException) {
			32
		}
	}

	override fun add(appInfo: AppInfo?) {
		items.add(appInfo)
	}

	override fun getItem(pos: Int): AppInfo? = items[pos]

	override fun getCount(): Int = items.size

	override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
		var convertView: View? = convertView
		val viewHolder: ViewHolder
		if (convertView == null) {
			val inflater: LayoutInflater? = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater?
			convertView = inflater!!.inflate(layout, parent, false)
			viewHolder = ViewHolder()
			viewHolder.label = convertView.findViewById(R.id.label)
			viewHolder.icon = convertView.findViewById(R.id.icon)
			viewHolder.spinner = convertView.findViewById(R.id.notification_spinner)
			convertView.tag = viewHolder
		} else {
			viewHolder = convertView.tag as ViewHolder
		}
		val appInfo: AppInfo? = getItem(pos)
		if (appInfo != null) {
			if (appInfo.icon != null) {
				viewHolder.icon!!.visibility = View.VISIBLE // to cancel View.GONE if it was set
				viewHolder.icon!!.setImageBitmap(appInfo.icon)
				val lp: LinearLayout.LayoutParams = viewHolder.icon!!.layoutParams as LinearLayout.LayoutParams
				lp.width = iconSize
				lp.height = lp.width
				viewHolder.icon!!.layoutParams = lp
			} else {
				viewHolder.icon!!.visibility = View.GONE
			}
			viewHolder.label?.text = appInfo.label
			convertView!!.tag = viewHolder
			val adapter: ArrayAdapter<CharSequence> = ArrayAdapter(context, android.R.layout.simple_spinner_item, android.R.id.text1)
			adapter.add(context.resources.getString(R.string.notification_type_default))
			adapter.add(context.resources.getString(R.string.notification_type_no_notif))
			adapter.add(context.resources.getString(R.string.notification_type_silent))
			adapter.add(context.resources.getString(R.string.notification_type_vibra))
			adapter.add(context.resources.getString(R.string.notification_type_strong_vibra))
			adapter.add(context.resources.getString(R.string.notification_type_ringtone))
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
			viewHolder.spinner!!.adapter = adapter
			val position: NotificationOption? = NotificationPreferences.getNotificationPreferenceForApp(context, appInfo.packageName)
			viewHolder.spinner!!.setSelection(position!!.asInt())
			viewHolder.spinner!!.onItemSelectedListener = object : MyOnItemSelectedListener(appInfo.packageName) {
				override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
					NotificationPreferences.saveNotificationPreferenceForApp(context, packageName, i)
				}

				override fun onNothingSelected(adapterView: AdapterView<*>?) {
					Log.i(TAG, "nothing selected")
				}
			}
		}
		return (convertView)!!
	}

	override fun getFilter(): Filter =
			SeenPackagesFilter(NotificationPreferences.seenPackageNames(context))

	fun restoreFilter() = filter.filter(null)

	internal abstract class MyOnItemSelectedListener constructor(var packageName: String) : OnItemSelectedListener

	private class ViewHolder {
		var label: TextView? = null
		var icon: ImageView? = null
		var spinner: Spinner? = null
	}

	private inner class SeenPackagesFilter(private val seenPackages: List<String>) : Filter() {
		override fun performFiltering(ignored: CharSequence): FilterResults {
			val results: FilterResults = FilterResults()
			val newValues: ArrayList<AppInfo?> = ArrayList()
			for (value: AppInfo? in items) {
				val packageName: String = value?.packageName?.toLowerCase()!!
				if (seenPackages!!.contains(packageName)) newValues.add(value)
			}
			results.values = newValues
			results.count = newValues.size
			return results
		}

		override fun publishResults(constraint: CharSequence, results: FilterResults) {
			if (results.count > 0) {
				items.clear()
				for (value: AppInfo? in results.values as ArrayList<AppInfo?>) add(value)
				notifyDataSetChanged()
			} else {
				items.clear()
				notifyDataSetInvalidated()
			}
		}
	}

	companion object {
		private val TAG: String = AppInfoAdapter::class.java.simpleName
	}
}