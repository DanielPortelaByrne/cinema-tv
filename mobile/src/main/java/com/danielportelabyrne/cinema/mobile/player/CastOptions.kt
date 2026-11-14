package com.danielportelabyrne.cinema.mobile.player

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions as GmsCastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Google's own Default Media Receiver: no Cinema app is needed on the Chromecast. */
class CastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): GmsCastOptions =
        GmsCastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
