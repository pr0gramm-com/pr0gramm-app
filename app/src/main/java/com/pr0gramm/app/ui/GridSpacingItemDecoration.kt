package com.pr0gramm.app.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.util.dp

/**
 * Puts a gap between the cells of a grid, but not at the outer edges.
 * Only items at positions accepted by [isGridItem] get the spacing.
 */
class GridSpacingItemDecoration(
    private val spacingDp: Int,
    private val isGridItem: (position: Int) -> Boolean,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.setEmpty()

        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION || !isGridItem(position)) {
            return
        }

        val lm = parent.layoutManager as? GridLayoutManager ?: return
        val lp = view.layoutParams as? GridLayoutManager.LayoutParams ?: return

        val spacing = parent.context.dp(spacingDp)
        val spanCount = lm.spanCount

        // every cell gives up the same width, so all cells stay equally sized
        outRect.left = lp.spanIndex * spacing / spanCount
        outRect.right = spacing - (lp.spanIndex + lp.spanSize) * spacing / spanCount
        outRect.bottom = spacing

        // the first row of a grid block also gets a gap above it
        var rowStart = position
        while (rowStart > 0 && lm.spanSizeLookup.getSpanIndex(rowStart, spanCount) > 0) {
            rowStart--
        }

        if (rowStart == 0 || !isGridItem(rowStart - 1)) {
            outRect.top = spacing
        }
    }
}
