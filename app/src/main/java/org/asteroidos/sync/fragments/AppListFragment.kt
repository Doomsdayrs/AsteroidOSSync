package org.asteroidos.sync.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import org.asteroidos.sync.MainActivity
import org.asteroidos.sync.R
import org.asteroidos.sync.adapters.AppInfoAdapter

class AppListFragment : Fragment(R.layout.fragment_app_list) {
	var adapter: AppInfoAdapter? = null

	override fun onAttach(context: Context) {
		super.onAttach(context)
		adapter = AppInfoAdapter(context, R.layout.app_list_item, MainActivity.appInfoList)
		adapter!!.restoreFilter()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		listView.setAdapter(adapter)
		adapter!!.filter.filter("") { count ->
			no_notification_placeholder.setVisibility(if (count == 0) VISIBLE else INVISIBLE)
		}
	}
}