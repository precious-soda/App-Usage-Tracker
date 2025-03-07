package com.example.appusage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.Locale


class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "app-usage-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        try {
            val user = auth.currentUser ?: return Result.failure()
            val userUid = user.uid
            val dao = db.appSessionDao()

            val unsyncedSessions = dao.getUnsyncedSessions()
            if (unsyncedSessions.isEmpty()) return Result.success()

            val userSessionsRef = firestore.collection("users")
                .document(userUid)
                .collection("app_sessions")


            unsyncedSessions.forEach { session ->
                val durationInSeconds = session.duration / 1000

                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val startTimeFormatted = dateFormat.format(Date(session.startTime))
                val endTimeFormatted = dateFormat.format(Date(session.endTime))

                val sessionData = mapOf(
                    "appName" to session.appName,
                    "startTime" to startTimeFormatted,
                    "endTime" to endTimeFormatted,
                    "duration" to durationInSeconds
                )

                userSessionsRef.document(session.id.toString())
                    .set(sessionData)
                    .await()
            }

            unsyncedSessions.forEach { it.isSynced = true }
            dao.updateSessions(unsyncedSessions)

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        fun schedulePeriodicSync(context: Context) {
            val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                30,
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "AppUsageSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            )
        }

        fun syncNow(context: Context) {
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
        }
    }
}