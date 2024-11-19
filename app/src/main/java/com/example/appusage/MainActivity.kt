package com.example.appusage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.app.NotificationManagerCompat
import com.example.appusage.ui.theme.AppUsageTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object PreferencesConstants {
    const val PREF_NAME = "AppUsagePrefs"
    const val KEY_TRACKING_ENABLED = "tracking_enabled"
}

class AppUsageTracker(private val context: Context) {
    private var lastTrackedApp: String? = null
    private var lastTrackedTime: Long = 0
    private val minimumTimeThreshold = 1000


//    private val sessionStorage= LinkedList<AppSession>()
    private val activeSessions = mutableMapOf<String, AppSession>()

    private fun isSystemApp(packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun trackCurrentApp() {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {

                val currentTime = event.timeStamp
                val packageName = event.packageName

                if(isSystemApp(packageName)){
                    continue
                }


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

                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {

                        activeSessions[packageName] = AppSession(
                            packageName = packageName,
                            appName = appName,
                            startTime = currentTime
                        )
                        val timeOpen = timeFormat.format(Date(currentTime))
                        Log.i("AppUsageTracker",
                            "App Name: $appName | " +
                                    "Open: $timeOpen | ")
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED -> {

                        activeSessions[packageName]?.let { session ->
                            session.endTime = currentTime
                            session.duration = currentTime - session.startTime

                            val timeClose = timeFormat.format(Date(currentTime))
                            val durationSeconds = session.duration / 1000

                            Log.i("AppUsageTracker",
                                "App Name: ${session.appName} | " +
                                        "close: $timeClose | " +
                                        "Duration: $durationSeconds seconds")

//                            sessionStorage.add(session)
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isUsageAccessGranted()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Log.w("AppUsageTracker", "Please grant Usage Access for this app.")
        }

        val savedTrackingState = getTrackingState()

        setContent {
            AppUsageTheme {
                var isTrackingEnabled by remember {
                    mutableStateOf(savedTrackingState)
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
                            name = "Just tracking apps \uD83D\uDE44",
                        )
                        Toggle(
                            isChecked = isTrackingEnabled,
                            onCheckedChange={isChecked->

                                isTrackingEnabled=isChecked
                                saveTrackingState(isChecked)

                                if(isChecked) {
                                    startTrackingService()
                                } else {
                                    stopTrackingService()
                                }
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

    private fun isNotificationEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun requestNotificationPermission(context: Context) {
        if (!isNotificationEnabled(context)) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        }
    }

    private fun saveTrackingState(isEnabled: Boolean) {
        getSharedPreferences(PreferencesConstants.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PreferencesConstants.KEY_TRACKING_ENABLED, isEnabled)
            .apply()
    }

    private fun getTrackingState(): Boolean {
        return getSharedPreferences(PreferencesConstants.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PreferencesConstants.KEY_TRACKING_ENABLED, false)
    }

    private fun startTrackingService() {
        if (!isNotificationEnabled(this)) {
            requestNotificationPermission(this)
        }
        Intent(applicationContext, UsageTrackingService::class.java).also {
            it.action=UsageTrackingService.Actions.START.toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
            } else {
                startService(it)
            }
        }
    }

    private fun stopTrackingService() {
        Intent(applicationContext, UsageTrackingService::class.java).also {
            it.action=UsageTrackingService.Actions.STOP.toString()
            startService(it)
        }
    }


}

@Composable
fun Greeting(name: String) {
    Text(
        text = "$name",
        modifier = Modifier.padding(start = 5.dp, top = 10.dp)
    )
}


@Composable
fun Toggle(isChecked: Boolean, onCheckedChange:(Boolean)->Unit){
    Switch(
        checked = isChecked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(end = 5.dp, top = 10.dp)
        )
}
