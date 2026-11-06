package com.danielportelabyrne.cinema

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log as AndroidLog
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.ui.Perf

/** Connects the shared code to this device: its log lines go to logcat, and its timing marks (debug builds) use elapsed realtime. */
class CinemaApp : Application() {
    private var started = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val whenAway = Runnable {
        // Nothing of Cinema on screen (the TV went to standby, or the viewer is in another app) and nothing playing:
        // a waiting update installs now, quietly. Music playing on in the background is left alone.
        val audio = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
        if (started == 0 && audio?.isMusicActive != true) com.danielportelabyrne.cinema.update.AppUpdater(this).installQuietly()
    }

    /** Counts Cinema's screens on view, so an update can wait until there are none. */
    private fun watchForBackground() = registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(a: android.app.Activity) { started++; main.removeCallbacks(whenAway) }
        override fun onActivityStopped(a: android.app.Activity) { started--; if (started <= 0) { started = 0; main.removeCallbacks(whenAway); main.postDelayed(whenAway, AWAY_MS) } }
        override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
        override fun onActivityResumed(a: android.app.Activity) {}
        override fun onActivityPaused(a: android.app.Activity) {}
        override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
        override fun onActivityDestroyed(a: android.app.Activity) {}
    })

    private companion object { const val AWAY_MS = 5_000L }

    override fun onCreate() {
        super.onCreate()
        Perf.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Perf.clock = SystemClock::elapsedRealtime
        com.danielportelabyrne.cinema.design.cinema.OpeningState.watchScreen(this)
        watchForBackground()
        Log.sink = Log.Sink { level, tag, message, error ->
            when (level) {
                'D' -> AndroidLog.d(tag, message, error)
                'I' -> AndroidLog.i(tag, message, error)
                'W' -> AndroidLog.w(tag, message, error)
                else -> AndroidLog.e(tag, message, error)
            }
        }
    }
}
