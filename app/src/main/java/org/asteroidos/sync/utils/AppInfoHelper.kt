package org.asteroidos.sync.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import org.asteroidos.sync.logID
import java.util.*

// copied from https://github.com/jensstein/oandbackup, used under MIT license
object AppInfoHelper {
	private val pInfoPackageNameComparator = Comparator<PackageInfo> { p1, p2 ->
		p1.packageName.compareTo(p2.packageName, ignoreCase = true)
	}

	fun getPackageInfo(context: Context): ArrayList<AppInfo> {
		val list = ArrayList<AppInfo>()
		val pm = context.packageManager
		val pInfoList = pm.getInstalledPackages(0)
		Collections.sort(pInfoList, pInfoPackageNameComparator)
		// list seemingly starts scrambled on 4.3
		for (pInfo in pInfoList) {
			var isSystem = false
			if (pInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
				isSystem = true
			}
			var icon: Bitmap? = null
			val apkIcon = pm.getApplicationIcon(pInfo.applicationInfo)
			try {
				if (apkIcon is BitmapDrawable) {
					// getApplicationIcon gives a Drawable which is then cast as a BitmapDrawable
					val src = apkIcon.bitmap
					if (src.width > 0 && src.height > 0) {
						icon = Bitmap.createScaledBitmap(src,
								src.width, src.height, true)
					} else {
						Log.d(logID(), String.format(
								"icon for %s had invalid height or width (h: %d w: %d)",
								pInfo.packageName, src.height, src.width))
					}
				} else {
					icon = Bitmap.createBitmap(
							apkIcon.intrinsicWidth,
							apkIcon.intrinsicHeight,
							Bitmap.Config.ARGB_8888
					)
					val canvas = Canvas(icon)
					apkIcon.setBounds(0, 0, canvas.width, canvas.height)
					apkIcon.draw(canvas)
				}
			} catch (ignored: ClassCastException) {
			}
			val appInfo = AppInfo(pInfo.packageName,
					pInfo.applicationInfo.loadLabel(pm).toString(),
					isSystem,
					true)
			appInfo.icon = icon
			list.add(appInfo)
		}
		return list
	}
}