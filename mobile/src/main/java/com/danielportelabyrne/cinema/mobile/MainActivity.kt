package com.danielportelabyrne.cinema.mobile

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.danielportelabyrne.cinema.mobile.ui.CinemaApp
import com.danielportelabyrne.cinema.mobile.ui.CinemaTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = (application as MobileApp).model
        setContent {
            CinemaTheme {
                BackHandler(enabled = model.stack.isNotEmpty() || model.tab != Tab.HOME) { model.back() }
                CinemaApp(model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val model = (application as MobileApp).model
        if (!model.needsSetup) model.refresh()
    }
}
