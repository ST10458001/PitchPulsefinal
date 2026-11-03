package com.rosebank.pitchpulse

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** Flat match row cached for the 7-day offline window (FR-008). */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val sport: String,
    val league: String,
    val home: String,
    val away: String,
    val homeCode: String,
    val awayCode: String,
    val homeColor: String,
    val awayColor: String,
    val homeScore: Int,
    val awayScore: Int,
    val minute: Int,
    val clock: String,
    val status: String,
    val kickoffAt: Long,
    val possessionHome: Int,
    val shotsHome: Int,
    val shotsAway: Int,
    val sotHome: Int,
    val sotAway: Int,
    val xgHome: Double,
    val xgAway: Double,
    val momentum: String,
    val pulse: String,
    val firstScorer: String
)

@Entity(tableName = "favourites", primaryKeys = ["kind", "entityId"])
data class FavouriteEntity(
    val kind: String,
    val entityId: String,
    val label: String,
    val position: Int
)

@Entity(tableName = "predictions")
data class PredictionEntity(
    @PrimaryKey val matchId: String,
    val resultPick: String,
    val scorePick: String,
    val scorerPick: String,
    val safeBet: Boolean,
    val queued: Boolean,
    val points: Int,
    val settled: Boolean
)

@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: String,
    val summary: String,
    val url: String,
    val tag: String
)

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches")
    fun all(): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(items: List<MatchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: MatchEntity)

    @Query("DELETE FROM matches")
    fun clear()
}

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourites ORDER BY position ASC")
    fun all(): List<FavouriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE kind = :kind AND entityId = :id")
    fun remove(kind: String, id: String)

    @Query("SELECT COUNT(*) FROM favourites")
    fun count(): Int
}

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions")
    fun all(): List<PredictionEntity>

    @Query("SELECT * FROM predictions WHERE matchId = :id LIMIT 1")
    fun one(id: String): PredictionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: PredictionEntity)
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM news")
    fun all(): List<NewsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(items: List<NewsEntity>)

    @Query("DELETE FROM news")
    fun clear()
}

@Database(
    entities = [MatchEntity::class, FavouriteEntity::class, PredictionEntity::class, NewsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao
    abstract fun favourites(): FavouriteDao
    abstract fun predictions(): PredictionDao
    abstract fun news(): NewsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pitchpulse.db"
                ).allowMainThreadQueries().build().also { instance = it }
            }
        }
    }
}
