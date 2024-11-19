package com.example.appusage
import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class UsageTrackingService : Service() {

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private lateinit var appUsageTracker: AppUsageTracker
    private val handler = Handler(Looper.getMainLooper())

    private val trackingRunnable = object : Runnable {
        override fun run() {
            appUsageTracker.trackCurrentApp()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appUsageTracker = AppUsageTracker(this)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action){
            Actions.START.toString()->start()
            Actions.STOP.toString()->{
                stop()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start(){
        val notification=NotificationCompat.Builder(this,"running")
            .setSmallIcon(R.drawable.robot)
            .setContentTitle("We Care")
            .setContentText("Collecting your data \uD83D\uDE05")
            .build()

        startForeground(1,notification)
        handler.post(trackingRunnable)
    }

    private fun stop(){
        handler.removeCallbacks(trackingRunnable)
        stopForeground(true)
    }

    enum class Actions{
        START, STOP
    }

}
