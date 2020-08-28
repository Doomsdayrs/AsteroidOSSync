package org.asteroidos.sync.utils

import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable

// copied from https://github.com/jensstein/oandbackup, used under MIT license
class AppInfo : Comparable<AppInfo>, Parcelable {
	var label: String
		private set
	var packageName: String
		private set
	var isSystem: Boolean
		private set
	private var installed: Boolean
	private var checked = false
	var isDisabled = false
	var icon: Bitmap? = null

	internal constructor(packageName: String, label: String, system: Boolean, installed: Boolean) {
		this.label = label
		this.packageName = packageName
		isSystem = system
		this.installed = installed
	}

	override fun compareTo(other: AppInfo): Int = label.compareTo(
			other.label,
			ignoreCase = true
	)

	override fun toString(): String = "$label : $packageName"

	override fun describeContents(): Int = 0

	override fun writeToParcel(out: Parcel, flags: Int) {
		out.writeString(label)
		out.writeString(packageName)
		out.writeBooleanArray(booleanArrayOf(isSystem, installed, checked))
		out.writeParcelable(icon, flags)
	}

	private constructor(`in`: Parcel) {
		label = `in`.readString()!!
		packageName = `in`.readString()!!
		val bools = BooleanArray(4)
		`in`.readBooleanArray(bools)
		isSystem = bools[0]
		installed = bools[1]
		checked = bools[2]
		icon = `in`.readParcelable(javaClass.classLoader)
	}

	companion object {
		val CREATOR: Parcelable.Creator<AppInfo> = object : Parcelable.Creator<AppInfo> {
			override fun createFromParcel(`in`: Parcel): AppInfo {
				return AppInfo(`in`)
			}

			override fun newArray(size: Int): Array<AppInfo?> {
				return arrayOfNulls(size)
			}
		}
	}
}