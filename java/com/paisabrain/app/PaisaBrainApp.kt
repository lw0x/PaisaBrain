package com.paisabrain.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.paisabrain.app.db.PaisaBrainDatabase

class PaisaBrainApp : Application() {

    lateinit var database: PaisaBrainDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = PaisaBrainDatabase.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val roastChannel = NotificationChannel(
                CHANNEL_ROAST,
                getString(R.string.channel_weekly_roast),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_roast_desc)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alerts_desc)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(roastChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        lateinit var instance: PaisaBrainApp
            private set
        const val CHANNEL_ROAST = "weekly_roast"
        const val CHANNEL_ALERTS = "budget_alerts"
    }
}
