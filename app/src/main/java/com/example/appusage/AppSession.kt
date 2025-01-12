package com.example.appusage

import androidx.lifecycle.LiveData
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "app_sessions")
data class AppSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") var endTime: Long = 0,
    var duration: Long = 0
)

@Dao
interface AppSessionDao {
    @Query("SELECT * FROM app_sessions ORDER BY start_time DESC")
    suspend fun getAllSessions(): List<AppSession>

    @Insert
    suspend fun insert(session: AppSession)

    @Query("DELETE FROM app_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM app_sessions ORDER BY start_time DESC LIMIT 10")
    suspend fun getRecentSessions(): List<AppSession>

    @Query("SELECT * FROM app_sessions ORDER BY end_time DESC LIMIT 10")
    fun getRecentSessionsLive(): LiveData<List<AppSession>>
}

@Database(entities = [AppSession::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSessionDao(): AppSessionDao
}