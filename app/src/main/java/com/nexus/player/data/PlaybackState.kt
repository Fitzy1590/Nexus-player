package com.nexus.player.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "playback_state")
data class PlaybackState(
    @PrimaryKey val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    /** Format.id of the selected audio / text track (container-stable). Null = default selection. */
    val audioTrackId: String?,
    val subtitleTrackId: String?,
    /** Per-item speed override; null = use global speed. */
    val speed: Float?,
    val updatedAt: Long,
)

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE mediaKey = :key")
    suspend fun get(key: String): PlaybackState?

    @Upsert
    suspend fun upsert(state: PlaybackState)
}

@Database(entities = [PlaybackState::class], version = 1, exportSchema = false)
abstract class NexusDb : RoomDatabase() {
    abstract fun playbackState(): PlaybackStateDao

    companion object {
        @Volatile private var instance: NexusDb? = null
        fun get(ctx: Context): NexusDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, NexusDb::class.java, "nexus.db").build()
                .also { instance = it }
        }

        private val VOLATILE = setOf("token", "access_token", "sig", "signature", "expires", "exp", "e", "st", "hdnts", "auth")

        /** Drops fragment and volatile signed-URL params so token rotation maps to the same item. */
        fun keyFor(uri: String): String {
            val base = uri.substringBefore('#')
            val q = base.substringAfter('?', "")
            if (q.isEmpty()) return base
            val kept = q.split('&').filter { it.substringBefore('=').lowercase() !in VOLATILE }
            return base.substringBefore('?') + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        }

        /** Resume threshold: ignore <5 s in and treat ≥95 % as completed. */
        fun shouldResume(s: PlaybackState?): Boolean =
            s != null && !s.completed && s.positionMs > 5_000 &&
                (s.durationMs <= 0 || s.positionMs < s.durationMs * 0.95)
    }
}
