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
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.lazy.LazyColumn
import java.util.Locale
import androidx.compose.material3.*
import androidx.room.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState


object PreferencesConstants {
    const val PREF_NAME = "AppUsagePrefs"
    const val KEY_TRACKING_ENABLED = "tracking_enabled"
}

class AppUsageTracker(private val context: Context) {
    private var lastTrackedApp: String? = null
    private var lastTrackedTime: Long = 0
    private val minimumTimeThreshold = 1000
    private val activeSessions = mutableMapOf<String, AppSession>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "app-usage-db"
    ).build()

    private val dao = db.appSessionDao()


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

                handleAppEvent(event)
            }
        }
    }

    private fun handleAppEvent(event: UsageEvents.Event) {
        val currentTime = event.timeStamp
        val packageName = event.packageName

        if (isSystemApp(packageName)) return
        if (packageName == lastTrackedApp &&
            currentTime - lastTrackedTime < minimumTimeThreshold) return

        val appName = getAppName(packageName)

        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                handleAppOpen(packageName, appName, currentTime)
            }
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                handleAppClose(packageName, currentTime)
            }
        }

        lastTrackedApp = packageName
        lastTrackedTime = currentTime
    }

    private fun handleAppOpen(packageName: String, appName: String, time: Long) {
        activeSessions[packageName] = AppSession(
            packageName = packageName,
            appName = appName,
            startTime = time
        )
    }

    private fun handleAppClose(packageName: String, time: Long) {
        activeSessions[packageName]?.let { session ->
            session.endTime = time
            session.duration = time - session.startTime

            scope.launch {
                dao.insert(session)
            }
            Log.d(
                "AppUsageTracker",
                "App Closed: ${session.appName}, Package: $packageName, " +
                        "Duration: ${session.duration / 1000} seconds, " +
                        "Start Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(session.startTime))}, " +
                        "End Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(session.endTime))}"
            )

            activeSessions.remove(packageName)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db by lazy {
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "app-usage-db"
            ).build()
        }

        if (!isUsageAccessGranted()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Log.w("AppUsageTracker", "Please grant Usage Access for this app.")
        }

        val savedTrackingState = getTrackingState()

        setContent {
            AppUsageTheme {
                var sessions by remember { mutableStateOf<List<AppSession>>(emptyList()) }
                var isTrackingEnabled by remember {
                    mutableStateOf(savedTrackingState)
                }
                LaunchedEffect(Unit) {
                    launch(Dispatchers.IO) {
                        db.appSessionDao().getRecentSessions().let{
                            sessions = it
                        }
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Greeting(
                                name = "Just tracking apps \uD83D\uDE44",
                            )
                            Toggle(
                                isChecked = isTrackingEnabled,
                                onCheckedChange = { isChecked ->

                                    isTrackingEnabled = isChecked
                                    saveTrackingState(isChecked)

                                    if (isChecked) {
                                        startTrackingService()
                                    } else {
                                        stopTrackingService()
                                    }
                                }

                            )
                        }
                        SessionList(sessions = sessions)
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

@Composable
fun SessionList(sessions: List<AppSession>) {
    LazyColumn {
        items(sessions) { session ->
            SessionCard(session = session)
        }
    }
}

@Composable
fun SessionCard(session: AppSession) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = session.appName,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("App: ${session.appName}")
            Text("Duration: ${session.duration / 1000} seconds")
            Text("Started: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(session.startTime))}")
            Text("Ended: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(session.endTime))}")
        }
    }
}

