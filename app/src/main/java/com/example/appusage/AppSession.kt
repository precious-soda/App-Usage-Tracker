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
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "app_sessions")
data class AppSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") var endTime: Long = 0,
    var duration: Long = 0,
    var isSynced: Boolean= false
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

    @Query("SELECT * FROM app_sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<AppSession>

    @Update
    suspend fun updateSessions(sessions: List<AppSession>)

    @Query("DELETE FROM sqlite_sequence WHERE name = 'app_sessions'")
    suspend fun resetAutoIncrement()

}

@Database(entities = [AppSession::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSessionDao(): AppSessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("" +
                        "ALTER TABLE app_sessions ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}