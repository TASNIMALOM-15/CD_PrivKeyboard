package com.example.privkeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Single-activity app: Home / Theme / Clipboard pages behind a floating glass navigation pill. */
@Suppress("DEPRECATION")
class MainActivity : Activity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_HOME = 0
        const val PAGE_THEME = 1
        const val PAGE_EFFECTS = 2
        const val PAGE_CLIPBOARD = 3
        private const val REQ_IMAGE = 41
    }

    private lateinit var root: BackdropLayout
    private lateinit var column: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var menuButton: ImageView
    private lateinit var pagesFrame: FrameLayout
    private lateinit var nav: GlassNav
    private lateinit var fab: ImageView
    private lateinit var navLp: FrameLayout.LayoutParams
    private lateinit var fabLp: FrameLayout.LayoutParams

    private lateinit var homePage: HomePage
    private lateinit var themePage: ThemePage
    private lateinit var effectsPage: EffectsPage
    private lateinit var clipPage: ClipboardPage

    private var current = -1
    private var insetTop = 0
    private var insetBottom = 0

    private fun dp(v: Int) = Glass.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()

        root = BackdropLayout(this)

        // ---- glass toolbar
        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.setPadding(dp(22), dp(6), dp(10), dp(6))
        toolbar.background = GlassDrawable(toolbar, -1f, 1.4f)
        titleView = Glass.text(this, "PrivKeyboard", 20f, ThemeRole.PRIMARY, true)
        toolbar.addView(titleView, LinearLayout.LayoutParams(0, Glass.WRAP, 1f))
        menuButton = ImageView(this)
        menuButton.setImageResource(R.drawable.ic_more)
        menuButton.tag = ThemeRole.PRIMARY
        menuButton.setPadding(dp(10), dp(10), dp(10), dp(10))
        menuButton.isClickable = true
        menuButton.background = GlassDrawable(menuButton)
        menuButton.setOnClickListener { showMenu(it) }
        toolbar.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(44)))

        pagesFrame = FrameLayout(this)

        column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        val toolbarLp = LinearLayout.LayoutParams(Glass.MATCH, dp(56))
        toolbarLp.setMargins(dp(14), dp(8), dp(14), dp(8))
        column.addView(toolbar, toolbarLp)
        column.addView(pagesFrame, LinearLayout.LayoutParams(Glass.MATCH, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(Glass.MATCH, Glass.MATCH))

        // ---- floating glass bottom navigation
        nav = GlassNav(
            this,
            listOf(
                "Home" to R.drawable.ic_home,
                "Theme" to R.drawable.ic_palette,
                "Effects" to R.drawable.ic_effects,
                "Clipboard" to R.drawable.ic_clipboard
            )
        ) { showPage(it) }
        navLp = FrameLayout.LayoutParams(dp(336), dp(66), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        root.addView(nav, navLp)

        // ---- floating action button (theme page)
        fab = Glass.fab(this, R.drawable.ic_image) { pickImage() }
        fabLp = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END)
        root.addView(fab, fabLp)

        homePage = HomePage(this)
        themePage = ThemePage(this)
        effectsPage = EffectsPage(this)
        clipPage = ClipboardPage(this)

        root.setOnApplyWindowInsetsListener { _, insets ->
            insetTop = insets.systemWindowInsetTop
            insetBottom = insets.systemWindowInsetBottom
            applyInsets()
            insets
        }
        root.onRebuilt = { colors -> updateSystemBars(colors.lightBackdrop) }
        applyInsets()

        setContentView(root)
        showPage(intent.getIntExtra(EXTRA_PAGE, PAGE_HOME))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showPage(intent.getIntExtra(EXTRA_PAGE, PAGE_HOME))
    }

    override fun onResume() {
        super.onResume()
        notifyShown()
    }

    // ---------------------------------------------------------------- window

    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun applyInsets() {
        column.setPadding(0, insetTop, 0, 0)
        navLp.bottomMargin = insetBottom + dp(16)
        nav.layoutParams = navLp
        fabLp.bottomMargin = insetBottom + dp(96)
        fabLp.marginEnd = dp(20)
        fab.layoutParams = fabLp
    }

    private fun updateSystemBars(lightBackdrop: Boolean) {
        var f = window.decorView.systemUiVisibility
        f = if (lightBackdrop) f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        else f and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        if (Build.VERSION.SDK_INT >= 26) {
            f = if (lightBackdrop) f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        window.decorView.systemUiVisibility = f
    }

    // ---------------------------------------------------------------- pages

    private fun showPage(index: Int) {
        val page = index.coerceIn(PAGE_HOME, PAGE_CLIPBOARD)
        if (page != current) {
            current = page
            pagesFrame.removeAllViews()
            val view: View = when (page) {
                PAGE_THEME -> themePage
                PAGE_EFFECTS -> effectsPage
                PAGE_CLIPBOARD -> clipPage
                else -> homePage
            }
            pagesFrame.addView(view, FrameLayout.LayoutParams(Glass.MATCH, Glass.MATCH))
            titleView.text = when (page) {
                PAGE_THEME -> "Theme"
                PAGE_EFFECTS -> "Effects"
                PAGE_CLIPBOARD -> "Clipboard"
                else -> "PrivKeyboard"
            }
            val onTheme = page == PAGE_THEME
            menuButton.visibility = if (onTheme) View.VISIBLE else View.GONE
            fab.visibility = if (onTheme) View.VISIBLE else View.GONE
            nav.select(page)
            Glass.applyRoles(root)
        }
        notifyShown()
    }

    private fun notifyShown() {
        when (current) {
            PAGE_HOME -> homePage.onShow()
            PAGE_EFFECTS -> effectsPage.onShow()
            PAGE_CLIPBOARD -> clipPage.onShow()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- theme actions

    private fun showMenu(anchor: View) {
        GlassMenu.show(
            anchor,
            listOf<Pair<String, () -> Unit>>(
                "Reset background" to {
                    ThemeStore.resetBackground()
                    themePage.rebuild()
                },
                "Copy theme (JSON)" to {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("theme", ThemeStore.toJson()))
                    toast("Theme copied to clipboard")
                },
                "Paste theme (JSON)" to {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val text = try {
                        cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    if (text != null && ThemeStore.applyJson(text)) {
                        themePage.rebuild()
                        toast("Theme applied")
                    } else {
                        toast("Clipboard doesn't contain a theme")
                    }
                }
            )
        )
    }

    fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(i, "Choose background"), REQ_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_IMAGE && resultCode == RESULT_OK && uri != null) {
            toast("Loading photo\u2026")
            Thread {
                val ok = ThemeStore.saveImage(uri)
                runOnUiThread {
                    if (ok) {
                        ThemeStore.useSavedImage()
                        themePage.rebuild()
                    } else {
                        toast("Couldn't load that image")
                    }
                }
            }.start()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
