package com.danielportelabyrne.cinema.mobile

import android.app.Application
import android.util.Log as AndroidLog
import com.danielportelabyrne.cinema.platform.Log

/** Connects the shared code to the phone: log lines to logcat, and one [Model] for every screen. */
class MobileApp : Application() {
    lateinit var model: Model
        private set

    override fun onCreate() {
        super.onCreate()
        Log.sink = Log.Sink { level, tag, message, error ->
            val t = "Cinema/$tag"
            when (level) {
                'E' -> AndroidLog.e(t, message, error); 'W' -> AndroidLog.w(t, message, error)
                'I' -> AndroidLog.i(t, message); else -> AndroidLog.d(t, message)
            }
        }
        model = Model(this)
    }
}
