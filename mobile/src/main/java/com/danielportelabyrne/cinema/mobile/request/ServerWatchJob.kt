package com.danielportelabyrne.cinema.mobile.request

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.danielportelabyrne.cinema.mobile.MainActivity
import com.danielportelabyrne.cinema.mobile.MobileApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Every quarter of an hour or so on Wi-Fi, while something is waited for, looks whether the server has it and starts
 * saving it to the phone, with Cinema closed. Starting a download from here needs Android's leave to run in the
 * background; without it, a notification asks the owner to open Cinema.
 */
class ServerWatchJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val model = (application as MobileApp).model
        running = model.scope.launch {
            try { model.checkWaiting() } finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); return false }

    companion object {
        private const val ID = 52
        private const val EVERY_MS = 15 * 60_000L
        private const val CHANNEL = "ready"

        /** Asks Android to keep looking. Leaves an existing schedule alone, so its clock is not restarted. */
        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java)
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(JobInfo.Builder(ID, ComponentName(context, ServerWatchJob::class.java))
                .setPeriodic(EVERY_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPersisted(true).build())
        }

        fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(ID)

        /** Something has arrived that could not be started from the background. */
        fun announce(context: Context, names: List<String>) {
            val notifications = context.getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Ready to save", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(ID, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("${names.joinToString(", ")} is on your server")
                .setContentText("Open Cinema to save it to this phone.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
