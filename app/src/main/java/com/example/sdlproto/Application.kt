package com.example.sdlproto

import android.app.Application
import android.util.Log

class Application : Application() {

    override fun onCreate() {
        super.onCreate()

        Constants(this)
        Log.d(Constants.LOG_TAG, "Application.onCreate:in")
    }

}