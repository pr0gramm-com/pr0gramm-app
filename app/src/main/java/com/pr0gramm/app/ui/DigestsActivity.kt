package com.pr0gramm.app.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.ActivityConversationBinding
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.fragments.DigestsFragment

/**
 * Shows the highlights (digests) of the user.
 */
class DigestsActivity : BaseAppCompatActivity("DigestsActivity") {
    // the conversation layout is just a toolbar and a fragment container
    private val views by bindViews(ActivityConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        setContentView(views)
        setSupportActionBar(views.toolbar)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.content, DigestsFragment())
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> false
        }
    }
}
