package com.example.privkeyboard

import android.app.Application

class PrivApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeStore.init(this)
        Feedback.init(this)
    }
}
