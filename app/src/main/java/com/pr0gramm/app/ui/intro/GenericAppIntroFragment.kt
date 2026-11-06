package com.pr0gramm.app.ui.intro

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pr0gramm.app.R
import com.pr0gramm.app.util.find

class GenericAppIntroFragment : Fragment(R.layout.intro_fragment_generic) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()

        view.setBackgroundColor(args.getInt(ARG_BG_COLOR))
        view.find<TextView>(R.id.title).text = args.getString(ARG_TITLE)
        view.find<TextView>(R.id.description).text = args.getString(ARG_DESC)
        view.find<ImageView>(R.id.image).setImageResource(args.getInt(ARG_DRAWABLE))
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DESC = "desc"
        private const val ARG_DRAWABLE = "drawable"
        private const val ARG_BG_COLOR = "bg_color"

        fun newInstance(title: CharSequence, description: CharSequence, imageDrawable: Int, bgColor: Int): GenericAppIntroFragment {
            return GenericAppIntroFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title.toString())
                    putString(ARG_DESC, description.toString())
                    putInt(ARG_DRAWABLE, imageDrawable)
                    putInt(ARG_BG_COLOR, bgColor)
                }
            }
        }
    }
}
