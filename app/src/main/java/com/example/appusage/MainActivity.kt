package com.example.appusage

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.appusage.ui.theme.AppUsageTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUsageTracker(private val context: Context) {
    private var lastTrackedApp: String? = null
    private var lastTrackedTime: Long = 0
    private val minimumTimeThreshold = 1000


    data class AppSession(
        val packageName: String,
        val appName: String,
        val startTime: Long,
        var endTime: Long = 0,
        var duration: Long = 0
    )

    private val activeSessions = mutableMapOf<String, AppSession>()

    fun trackCurrentApp() {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {

                val currentTime = event.timeStamp
                val packageName = event.packageName


                if (packageName == lastTrackedApp &&
                    currentTime - lastTrackedTime < minimumTimeThreshold) {
                    continue
                }


                val appName = try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                } catch (e: Exception) {
                    packageName
                }

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {

                        activeSessions[packageName] = AppSession(
                            packageName = packageName,
                            appName = appName,
                            startTime = currentTime
                        )

                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(currentTime))
                        Log.i("AppUsageTracker", "App Opened: $appName | Time: $timeStr")
                    }

                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {

                        activeSessions[packageName]?.let { session ->
                            session.endTime = currentTime
                            session.duration = currentTime - session.startTime

                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(currentTime))
                            val durationSeconds = session.duration / 1000

                            Log.i("AppUsageTracker",
                                "App Closed: ${session.appName} | " +
                                        "Time: $timeStr | " +
                                        "Duration: $durationSeconds seconds")

                            activeSessions.remove(packageName)
                        }
                    }
                }

                lastTrackedApp = packageName
                lastTrackedTime = currentTime
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var appUsageTracker: AppUsageTracker
    private val handler = Handler(Looper.getMainLooper())
    private val trackingRunnable = object : Runnable {
        override fun run() {
            appUsageTracker.trackCurrentApp()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isUsageAccessGranted()) {

            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Log.w("AppUsageTracker", "Please grant Usage Access for this app.")
        } else {
            appUsageTracker = AppUsageTracker(this)
            startTracking()
        }

        enableEdgeToEdge()
        setContent {
            AppUsageTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "App Usage Tracker",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }


    private fun isUsageAccessGranted(): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            System.currentTimeMillis() - 1000 * 60 * 60 * 24,
            System.currentTimeMillis()
        )
        return appList != null && appList.isNotEmpty()
    }

    private fun startTracking() {
        handler.post(trackingRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(trackingRunnable)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppUsageTheme {
        Greeting("Android")
    }
}