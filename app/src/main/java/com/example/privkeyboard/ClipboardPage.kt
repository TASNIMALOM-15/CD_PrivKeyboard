package com.example.privkeyboard

import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Full clipboard manager: search, pin, tag/folder, delete, copy. */
class ClipboardPage(private val activity: Activity) : LinearLayout(activity) {

    private val store = ClipStore.get(activity)
    private val items = ArrayList<Clip>()
    private var pinnedOnly = false
    private var tagFilter: String? = null

    private val countView = Glass.text(activity, "", 13f, ThemeRole.MUTED)
    private val searchBox = Glass.field(activity, "Search clips\u2026")
    private val chipRow = LinearLayout(activity)
    private val listView = ListView(activity)

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            buildRow(items[position], parent)
    }

    private fun dp(v: Int) = Glass.dp(activity, v)

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(4), dp(16), 0)

        val header = Glass.card(activity)
        header.addView(countView)
        header.addView(
            Glass.switchRow(activity, "Save clipboard history", ClipSettings.isEnabled(activity)) {
                ClipSettings.setEnabled(activity, it)
            },
            Glass.lp(activity, t = 6, b = 8)
        )
        header.addView(searchBox)
        chipRow.orientation = HORIZONTAL
        val chipScroll = HorizontalScrollView(activity)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chipRow)
        header.addView(chipScroll, Glass.lp(activity, t = 6))
        header.addView(
            Glass.button(activity, "Clear unpinned clips") { v ->
                GlassDialogs.confirm(v, "Clear history?", "All clips that are not pinned will be deleted.", "Delete") {
                    store.background { store.clearUnpinned() }
                    reload()
                }
            },
            Glass.lp(activity, t = 8)
        )
        addView(header, Glass.lp(activity, b = 10))

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                reload()
            }
        })

        listView.adapter = adapter
        listView.divider = ColorDrawable(Color.TRANSPARENT)
        listView.dividerHeight = dp(8)
        listView.selector = ColorDrawable(Color.TRANSPARENT)
        listView.cacheColorHint = Color.TRANSPARENT
        listView.clipToPadding = false
        listView.setPadding(0, 0, 0, dp(130))
        listView.overScrollMode = View.OVER_SCROLL_NEVER
        addView(listView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun onShow() {
        reload()
    }

    private fun reload() {
        val q = searchBox.text.toString()
        val tag = tagFilter
        val onlyPinned = pinnedOnly
        store.async({
            Pair(store.query(search = q, tag = tag, pinnedOnly = onlyPinned, limit = 1000), store.tags())
        }) { result ->
            items.clear()
            items.addAll(result.first)
            adapter.notifyDataSetChanged()
            countView.text = "${result.first.size} clips"
            renderChips(result.second)
        }
    }

    private fun renderChips(tags: List<String>) {
        chipRow.removeAllViews()
        addChip("All", !pinnedOnly && tagFilter == null) {
            pinnedOnly = false
            tagFilter = null
            reload()
        }
        addChip("\u2605 Pinned", pinnedOnly) {
            pinnedOnly = true
            tagFilter = null
            reload()
        }
        for (t in tags) {
            addChip("#$t", tagFilter == t) {
                pinnedOnly = false
                tagFilter = t
                reload()
            }
        }
        Glass.applyRoles(chipRow)
    }

    private fun addChip(label: String, selected: Boolean, onClick: () -> Unit) {
        val chip = Glass.chip(activity, label, selected) { onClick() }
        chipRow.addView(chip, Glass.lp(activity, Glass.WRAP, Glass.WRAP, 0, 0, 8, 0))
    }

    private fun buildRow(clip: Clip, parent: ViewGroup?): View {
        val row = LinearLayout(activity)
        row.orientation = VERTICAL
        row.setPadding(dp(16), dp(12), dp(16), dp(4))
        row.background = GlassDrawable(row, 22f, 1.2f)

        val body = Glass.text(activity, clip.text, 15f)
        body.maxLines = 4
        body.ellipsize = TextUtils.TruncateAt.END
        body.setOnClickListener { copy(clip) }
        row.addView(body)

        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(clip.used))
        val meta = if (clip.tag.isNullOrEmpty()) date else "$date \u2022 #${clip.tag}"
        row.addView(Glass.text(activity, meta, 11f, ThemeRole.MUTED), Glass.lp(activity, t = 2))

        val actions = LinearLayout(activity)
        actions.orientation = HORIZONTAL
        actions.gravity = Gravity.END
        actions.addView(action("Copy") { copy(clip) })
        actions.addView(
            action(if (clip.pinned) "Unpin" else "Pin") {
                store.background { store.setPinned(clip.id, !clip.pinned) }
                reload()
            }
        )
        actions.addView(action("Tag") { v -> editTag(v, clip) })
        actions.addView(
            action("Delete") {
                store.background { store.delete(clip.id) }
                reload()
            }
        )
        row.addView(actions)

        Glass.applyRoles(row, parent)
        return row
    }

    private fun action(label: String, onClick: (View) -> Unit): View {
        val tv = Glass.text(activity, label, 13f, ThemeRole.ACCENT, true)
        tv.setPadding(dp(12), dp(8), dp(4), dp(8))
        tv.isClickable = true
        tv.setOnClickListener { onClick(it) }
        return tv
    }

    private fun copy(clip: Clip) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("clip", clip.text))
        store.background { store.markUsed(clip.id) }
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun editTag(anchor: View, clip: Clip) {
        GlassDialogs.input(anchor, "Folder / tag", "Empty = none", clip.tag ?: "") { value ->
            store.background { store.setTag(clip.id, value) }
            reload()
        }
    }
}
