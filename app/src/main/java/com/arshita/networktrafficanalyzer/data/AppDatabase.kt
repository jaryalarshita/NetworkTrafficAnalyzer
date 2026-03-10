package com.arshita.networktrafficanalyzer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The single Room database for the app.
 *
 * Holds two tables:
 *  • [NetworkFlow]  — captured traffic data
 *  • [SecurityAlert] — alerts from the ThreatEngine
 *
 * Access via [AppDatabase.getInstance] (thread-safe singleton).
 */
@Database(
    entities = [NetworkFlow::class, SecurityAlert::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** Get the DAO for traffic data & alerts. */
    abstract fun trafficDao(): TrafficDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton database instance.
         * Creates it on first call; subsequent calls return the same instance.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "network_traffic_db"
                )
                    // Wipe & rebuild instead of migrating (fine during development)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
