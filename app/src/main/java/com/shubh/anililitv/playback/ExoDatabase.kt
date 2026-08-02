package com.shubh.anililitv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider

@OptIn(UnstableApi::class)
object ExoDatabase {
    @Volatile private var instance: DatabaseProvider? = null

    fun provider(context: Context): DatabaseProvider = instance ?: synchronized(this) {
        instance ?: StandaloneDatabaseProvider(context.applicationContext).also { instance = it }
    }
}
