package com.miruronative.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider

/**
 * The single Media3 database provider for this process.
 *
 * Media3 keeps the download index and every cache's file index in one SQLite database
 * (`exoplayer_internal.db`). Constructing a provider per cache opens a second `SQLiteOpenHelper`
 * against that same file, which duplicates the open/upgrade work and puts the playback cache and
 * the download index into avoidable lock contention with each other.
 */
@OptIn(UnstableApi::class)
object ExoDatabase {
    @Volatile private var instance: DatabaseProvider? = null

    fun provider(context: Context): DatabaseProvider = instance ?: synchronized(this) {
        instance ?: StandaloneDatabaseProvider(context.applicationContext).also { instance = it }
    }
}
