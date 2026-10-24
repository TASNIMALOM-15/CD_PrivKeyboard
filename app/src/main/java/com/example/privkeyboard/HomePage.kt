package com.example.privkeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup steps, a test field and usage tips. */
class HomePage(private val activity: Activity) : ScrollView(activity) {

    private val enableStatus = status()
    private val selectStatus = status()

    private fun status(): TextView {
        val tv = Glass.text(activity, "", 14f, ThemeRole.MUTED)
        tv.tag = null
        return tv
    }

    init {
        clipToPadding = false
        setPadding(Glass.dp(activity, 16), Glass.dp(activity, 4), Glass.dp(activity, 16), Glass.dp(activity, 130))
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL

        val hero = Glass.card(activity)
        hero.addView(Glass.text(activity, "PrivKeyboard", 28f, ThemeRole.PRIMARY, true))
        hero.addView(Glass.text(activity, "Private, encrypted and fully customisable.", 14f, ThemeRole.MUTED))
        col.addView(hero, Glass.lp(activity, b = 12))

        val setup = Glass.card(activity)
        setup.addView(Glass.text(activity, "1. Enable the keyboard", 17f, ThemeRole.PRIMARY, true))
        setup.addView(enableStatus)
        setup.addView(
            Glass.button(activity, "Open keyboard settings") {
                activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
            Glass.lp(activity, t = 8, b = 14)
        )
        setup.addView(Glass.text(activity, "2. Switch to PrivKeyboard", 17f, ThemeRole.PRIMARY, true))
        setup.addView(selectStatus)
        setup.addView(
            Glass.button(activity, "Choose keyboard") {
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            },
            Glass.lp(activity, t = 8)
        )
        col.addView(setup, Glass.lp(activity, b = 12))

        val tryCard = Glass.card(activity)
        tryCard.addView(Glass.text(activity, "3. Try it", 17f, ThemeRole.PRIMARY, true))
        val field = Glass.field(activity, "Type here to test")
        field.maxLines = 4
        tryCard.addView(field, Glass.lp(activity, t = 8))
        col.addView(tryCard, Glass.lp(activity, b = 12))

        val tips = Glass.card(activity)
        tips.addView(Glass.text(activity, "Tips", 17f, ThemeRole.PRIMARY, true))
        tips.addView(
            Glass.text(
                activity,
                "• Globe key: English ↔ বাংলা (phonetic: ami → আমি)\n" +
                    "• Long-press keys for digits, accents and symbols\n" +
                    "• Long-press space to switch keyboards\n" +
                    "• Clipboard icon above the keys opens your history\n" +
                    "• Theme tab: collection, photo backgrounds, blur, glass strength, key height\n" +
                    "• Effects tab: key animations, swipe trail, sound packs, haptics\n" +
                    "• No keystrokes are logged or sent anywhere; this build has no internet permission.",
                13f, ThemeRole.MUTED
            ),
            Glass.lp(activity, t = 6)
        )
        col.addView(tips)

        addView(col)
    }

    fun onShow() {
        val green = Color.parseColor("#4CD964")
        val red = Color.parseColor("#FF6B6B")

        if (keyboardEnabled()) {
            enableStatus.text = "✓ Enabled"
            enableStatus.setTextColor(green)
        } else {
            enableStatus.text = "✗ Not enabled yet"
            enableStatus.setTextColor(red)
        }

        if (keyboardSelected()) {
            selectStatus.text = "✓ Currently active"
            selectStatus.setTextColor(green)
        } else {
            selectStatus.text = "✗ Not active yet"
            selectStatus.setTextColor(red)
        }
    }

    private fun keyboardEnabled(): Boolean {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == activity.packageName }
    }

    private fun keyboardSelected(): Boolean {
        val id = Settings.Secure.getString(activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        return id.startsWith(activity.packageName + "/")
    }
}
