package com.danielportelabyrne.cinema

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.cinema.SplashView

/**
 * Debug builds only: the opening on its own, without signing in or opening any page. It plays once on arrival; OK
 * plays it again (pressed mid-way, it starts over cleanly, which is also the test that two never overlap). Back leaves.
 *
 *     adb shell am start -n com.danielportelabyrne.cinema.ci/com.danielportelabyrne.cinema.IdentPreviewActivity
 *
 * (Without -Pcinema.appIdSuffix=.ci the package is plain com.danielportelabyrne.cinema.)
 */
class IdentPreviewActivity : Activity() {
    private lateinit var root: FrameLayout
    private var splash: SplashView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Cinema.BG) }
        root.addView(TextView(this).apply {
            text = "OK  play again      Back  leave"
            setTextColor(Cinema.DIM)
            textSize = 14f
            letterSpacing = 0.08f
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = Cinema.px(this@IdentPreviewActivity, 40) })
        setContentView(root)
        play()
    }

    private fun play() {
        splash?.let { root.removeView(it) }      // detaching stops its sound at once
        splash = SplashView.show(root, skip = false, done = { splash = null })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_SPACE
        if (!ok) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) play()
        return true
    }
}
