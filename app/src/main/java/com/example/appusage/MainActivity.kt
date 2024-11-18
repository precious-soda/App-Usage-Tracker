package com.example.appusage

import android.app.usage.UsageEvents
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

        setContent {
            AppUsageTheme {
                var isTrackingEnabled by remember {
                    mutableStateOf(false)
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Greeting(
                            name = "Collect Data",
                        )
                        Toggle(
                            isChecked = isTrackingEnabled,
                            onCheckedChange={isChecked->
                                isTrackingEnabled=isChecked
                                if(isChecked) startTracking() else stopTracking()
                            }

                        )
                    }

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

    private fun stopTracking(){
        handler.removeCallbacks(trackingRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(trackingRunnable)
    }
}

@Composable
fun Greeting(name: String) {
    Text(
        text = "$name!",
        modifier = Modifier.padding(start = 5.dp)
    )
}


@Composable
fun Toggle(isChecked: Boolean, onCheckedChange:(Boolean)->Unit){
    Switch(
        checked = isChecked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(end = 5.dp)
        )
}
