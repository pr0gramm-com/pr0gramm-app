package com.pr0gramm.app.ui.fragments.conversation

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.ItemDateDividerBinding
import com.pr0gramm.app.ui.AdapterDelegate
import com.pr0gramm.app.ui.AdapterDelegateManager
import com.pr0gramm.app.ui.Adapters
import com.pr0gramm.app.ui.adapters2.DelegatingPagingDataAdapter
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.inflateDetachedChild
import com.pr0gramm.app.util.setTextFuture
import com.pr0gramm.app.util.sp
import java.text.SimpleDateFormat
import java.util.Locale

// messages of the same sender within this time are shown as one group
private const val GROUP_WINDOW_MILLIS = 5 * 60 * 1000L

sealed class ConversationItem {
    class Message(val message: Api.ConversationMessage) : ConversationItem()
    class Divider(val text: String) : ConversationItem()
}

class ConversationAdapter : DelegatingPagingDataAdapter<ConversationItem>(ItemCallback) {

    override val manager = AdapterDelegateManager(listOf(
            conversationItemMessageViewHolder(true),
            conversationItemMessageViewHolder(false),
            conversationItemDividerAdapter(),
    ))

    val messages: List<Api.ConversationMessage>
        get() = items.mapNotNull { item -> (item as? ConversationItem.Message)?.message }

    init {
        // a message is styled by the item before it, so rebind it when that item changes
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                rebind(positionStart + itemCount)
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                rebind(positionStart)
            }
        })
    }

    private fun rebind(position: Int) {
        if (position in 0 until itemCount) {
            notifyItemChanged(position)
        }
    }

    private object ItemCallback : DiffUtil.ItemCallback<ConversationItem>() {
        override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            return when {
                oldItem is ConversationItem.Message && newItem is ConversationItem.Message ->
                    oldItem.message.id == newItem.message.id

                oldItem is ConversationItem.Divider && newItem is ConversationItem.Divider ->
                    oldItem.text == newItem.text

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            // if the items are the same, the contents is also equal.
            return true
        }
    }
}

private class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val bubble = itemView.find<View>(R.id.bubble)
    val message = itemView.find<AppCompatTextView>(R.id.message)
    val time = itemView.find<TextView>(R.id.time)
}

fun conversationItemMessageViewHolder(forSent: Boolean): AdapterDelegate<ConversationItem, RecyclerView.ViewHolder> {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())

    return object : AdapterDelegate<ConversationItem, RecyclerView.ViewHolder> {
        override fun isForViewType(values: List<ConversationItem>, idx: Int): Boolean {
            return (values[idx] as? ConversationItem.Message)?.message?.sent == forSent
        }

        override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val layout = if (forSent) R.layout.item_message_sent else R.layout.item_message_received
            return MessageViewHolder(parent.inflateDetachedChild(layout))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, values: List<ConversationItem>, idx: Int) {
            holder as MessageViewHolder

            val context = holder.itemView.context
            val message = (values[idx] as ConversationItem.Message).message

            val prev = (values.getOrNull(idx - 1) as? ConversationItem.Message)?.message
            val grouped = prev != null && prev.sent == message.sent
                    && message.creationTime.millis - prev.creationTime.millis < GROUP_WINDOW_MILLIS

            holder.bubble.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = context.dp(if (grouped) 2 else 8)
            }

            // the sender side has small corners, the top one only within a group
            val large = context.dp(18f)
            val small = context.dp(4f)
            val top = if (grouped) small else large

            // corner order is top-left, top-right, bottom-right, bottom-left
            val corners = if (forSent) {
                floatArrayOf(large, large, top, top, small, small, large, large)
            } else {
                floatArrayOf(top, top, large, large, large, large, small, small)
            }

            when (val background = holder.bubble.background.mutate()) {
                is LayerDrawable -> repeat(background.numberOfLayers) { idx ->
                    (background.getDrawable(idx) as GradientDrawable).cornerRadii = corners
                }

                else -> (background as GradientDrawable).cornerRadii = corners
            }

            val text = buildSpannedString {
                append(Linkify.linkify(context, message.messageText))

                inSpans(SpaceSpan(context.sp(32f).toInt())) {
                    append(" ")
                }
            }

            holder.message.setTextFuture(text)
            holder.time.text = message.creationTime.toString(format)
        }
    }
}

fun conversationItemDividerAdapter(): AdapterDelegate<ConversationItem, RecyclerView.ViewHolder> {
    val itemAdapter = Adapters.ForViewBindings(ItemDateDividerBinding::inflate) { (views), item: ConversationItem.Divider ->
        views.text.text = item.text
    }

    return Adapters.adapt(itemAdapter) { value: ConversationItem -> value as? ConversationItem.Divider }
}
