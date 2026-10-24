package com.example.privkeyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

class PrivKeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var emojiPanel: EmojiPanel
    private lateinit var clipboardPanel: ClipboardPanel

    private var language = Language.ENGLISH

    /** Roman letters typed for the Bangla word currently being composed. */
    private val roman = StringBuilder()

    /** True while typing into a password field or an incognito field: never record clips. */
    private var privateField = false

    private var clipboardManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private val prefs by lazy { getSharedPreferences("kb_state", MODE_PRIVATE) }

    private val themeListener: () -> Unit = { updatePanelHeights() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager = cm
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        ThemeStore.removeListener(themeListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val kv = KeyboardView(this)
        kv.listener = this
        keyboardView = kv

        val ep = EmojiPanel(this, object : EmojiPanel.Callbacks {
            override fun onEmoji(emoji: String) {
                flushComposing()
                currentInputConnection?.commitText(emoji, 1)
            }

            override fun onBackspace() {
                onDelete()
            }

            override fun onClose() {
                hidePanels()
            }
        })
        ep.visibility = View.GONE
        emojiPanel = ep

        val cp = ClipboardPanel(this, object : ClipboardPanel.Callbacks {
            override fun onPick(text: String) {
                flushComposing()
                currentInputConnection?.commitText(text, 1)
                hidePanels()
            }

            override fun onClose() {
                hidePanels()
            }

            override fun onManage() {
                val i = Intent(this@PrivKeyboardService, MainActivity::class.java)
                i.putExtra(MainActivity.EXTRA_PAGE, MainActivity.PAGE_CLIPBOARD)
                i.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                startActivity(i)
            }
        })
        cp.visibility = View.GONE
        clipboardPanel = cp

        val frame = FrameLayout(this)
        frame.addView(
            kv,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        frame.addView(ep, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kv.preferredHeight))
        frame.addView(cp, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kv.preferredHeight))

        // slim glass toolbar above the keys
        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        val clipButton = ImageView(this)
        clipButton.setImageResource(R.drawable.ic_clipboard)
        clipButton.tag = ThemeRole.PRIMARY
        clipButton.setPadding(dp(8), dp(8), dp(8), dp(8))
        clipButton.isClickable = true
        clipButton.background = GlassDrawable(clipButton)
        clipButton.setOnClickListener {
            if (clipboardPanel.visibility == View.VISIBLE) hidePanels() else showClipboard()
        }
        val btnLp = LinearLayout.LayoutParams(dp(40), dp(36))
        btnLp.setMargins(dp(10), dp(2), dp(4), dp(2))
        toolbar.addView(clipButton, btnLp)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        column.addView(
            frame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val root = BackdropLayout(this)
        root.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        ThemeStore.addListener(themeListener)
        return root
    }

    /** Key-height changes alter the keyboard height; keep the panels the same size. */
    private fun updatePanelHeights() {
        if (!::keyboardView.isInitialized) return
        val h = keyboardView.preferredHeight
        for (p in listOf<View>(emojiPanel, clipboardPanel)) {
            val lp = p.layoutParams
            if (lp != null && lp.height != h) {
                lp.height = h
                p.layoutParams = lp
            }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::keyboardView.isInitialized) return

        roman.setLength(0)
        hidePanels()
        keyboardView.reset()

        privateField = isPasswordField(info) ||
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        val saved = Language.fromId(prefs.getString("lang", null))
        language = if (isLatinOnlyField(info)) Language.ENGLISH else saved
        if (!restarting || keyboardView.language != language) {
            keyboardView.applyLanguage(language)
        }
        if (!restarting) {
            val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
            val numeric = cls == InputType.TYPE_CLASS_NUMBER ||
                cls == InputType.TYPE_CLASS_PHONE ||
                cls == InputType.TYPE_CLASS_DATETIME
            keyboardView.switchLayer(if (numeric) Layer.SYMBOLS1 else Layer.LETTERS)
        }
        keyboardView.enterLabel = enterLabelFor(info)
        keyboardView.applyShift(0)
        updateAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        flushComposing()
        privateField = false
        if (::keyboardView.isInitialized) keyboardView.reset()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // User moved the cursor away from the word being composed: stop composing.
        if (roman.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            roman.setLength(0)
            currentInputConnection?.finishComposingText()
        }
    }

    // ---------------------------------------------------------------- clipboard capture

    private fun captureClip() {
        if (privateField || !ClipSettings.isEnabled(this)) return
        try {
            val clip = clipboardManager?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val desc = clip.description
            if (!desc.hasMimeType("text/*")) return
            // Password managers flag their clips as sensitive.
            if (desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isBlank() || text.length > 200_000) return
            val store = ClipStore.get(this)
            store.background { store.add(text) }
        } catch (e: Exception) {
            // Clipboard access can be denied by the OS; just skip.
        }
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        val c = if (text.length == 1) text[0] else ' '
        val isAsciiLetter = c in 'a'..'z' || c in 'A'..'Z'
        if (language == Language.BANGLA && isAsciiLetter) {
            roman.append(text)
            ic.setComposingText(BanglaPhonetic.convert(roman.toString()), 1)
        } else {
            flushComposing()
            ic.commitText(text, 1)
        }
        updateAutoCaps()
    }

    override fun onDelete() {
        val ic = currentInputConnection
        if (roman.isNotEmpty() && ic != null) {
            roman.deleteCharAt(roman.length - 1)
            if (roman.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } else {
                ic.setComposingText(BanglaPhonetic.convert(roman.toString()), 1)
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateAutoCaps()
    }

    override fun onEnter() {
        flushComposing()
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val options = ei?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        if (ei?.actionLabel != null) {
            ic.performEditorAction(ei.actionId)
        } else if (!noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateAutoCaps()
    }

    override fun onNextLanguage() {
        flushComposing()
        val all = Language.values()
        val next = all[(language.ordinal + 1) % all.size]
        language = next
        prefs.edit().putString("lang", next.id).apply()
        keyboardView.applyLanguage(next)
        keyboardView.applyShift(0)
        updateAutoCaps()
    }

    override fun onEmojiKey() {
        flushComposing()
        keyboardView.reset()
        clipboardPanel.visibility = View.GONE
        keyboardView.visibility = View.INVISIBLE
        emojiPanel.show()
        emojiPanel.visibility = View.VISIBLE
    }

    override fun onShowImePicker() {
        flushComposing()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------------------------------------------------------------- panels

    private fun showClipboard() {
        flushComposing()
        keyboardView.reset()
        emojiPanel.visibility = View.GONE
        keyboardView.visibility = View.INVISIBLE
        clipboardPanel.refresh()
        clipboardPanel.visibility = View.VISIBLE
    }

    private fun hidePanels() {
        if (!::emojiPanel.isInitialized || !::clipboardPanel.isInitialized || !::keyboardView.isInitialized) return
        emojiPanel.visibility = View.GONE
        clipboardPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- helpers

    /** Commit whatever Bangla word is being composed (it is already shown in the field). */
    private fun flushComposing() {
        if (roman.isNotEmpty()) {
            roman.setLength(0)
            currentInputConnection?.finishComposingText()
        }
    }

    private fun isPasswordField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        if (cls != InputType.TYPE_CLASS_TEXT) return false
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    /** Password / e-mail / URL / number fields always use the English layout. */
    private fun isLatinOnlyField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val cls = type and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) return true
        if (cls != InputType.TYPE_CLASS_TEXT) return false
        return when (type and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> true
            else -> false
        }
    }

    private fun enterLabelFor(info: EditorInfo?): String? {
        if (info == null) return null
        info.actionLabel?.let { return it.toString() }
        val options = info.imeOptions
        if ((options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        return when (options and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> null
        }
    }

    /** Sentence/word auto-capitalisation (English only; Bangla is case-sensitive phonetic). */
    private fun updateAutoCaps() {
        if (!::keyboardView.isInitialized || keyboardView.shiftState == 2) return
        if (language == Language.BANGLA) return
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        if (ei.inputType == InputType.TYPE_NULL) return
        val caps = ic.getCursorCapsMode(ei.inputType)
        keyboardView.applyShift(if (caps != 0) 1 else 0)
    }
}
