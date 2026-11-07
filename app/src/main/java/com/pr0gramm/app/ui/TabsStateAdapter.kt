package com.pr0gramm.app.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Tabs with stable ids, so replacing the tabs keeps or recreates the right fragments.
 */
class TabsStateAdapter : FragmentStateAdapter {
    constructor(fragment: Fragment) : super(fragment)

    constructor(activity: FragmentActivity) : super(activity)

    private val tabs: MutableList<TabInfo> = mutableListOf()

    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun createFragment(position: Int): Fragment {
        val info = tabs[position]

        return info.fragmentConstructor().also { fr ->
            if (info.args != null) {
                val existing = fr.arguments
                if (existing != null) {
                    existing.putAll(info.args)
                } else {
                    fr.arguments = Bundle(info.args)
                }
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].itemId
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.itemId == itemId }
    }

    fun addTab(title: String, args: Bundle? = null, id: Any? = null, fragmentConstructor: () -> Fragment) {
        tabs += TabInfo(title, fragmentConstructor, args, id)
        notifyItemInserted(tabs.size - 1)
    }

    fun updateTabTitle(id: Any, title: String) {
        for ((idx, tab) in tabs.withIndex()) {
            if (tab.id == id && tab.title != title) {
                tabs[idx] = tab.copy(title = title)
                notifyItemChanged(idx)
            }
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return tabs[position].title
    }

    fun replaceTabs(tabs: List<TabInfo>) {
        this.tabs.clear()
        this.tabs += tabs
        notifyDataSetChanged()
    }

    /**
     * Sets this adapter on the pager and shows the tab titles in the tab layout.
     */
    fun attach(tabLayout: TabLayout, pager: ViewPager2) {
        pager.adapter = this
        TabLayoutMediator(tabLayout, pager) { tab, position -> tab.text = getPageTitle(position) }.attach()
    }

    data class TabInfo(val title: String, val fragmentConstructor: () -> Fragment, val args: Bundle? = null, val id: Any? = null) {
        // shortcut: hash based id, a collision would reuse the wrong fragment. Use real ids if that ever happens.
        val itemId: Long get() = (id ?: title).hashCode().toLong()
    }
}
