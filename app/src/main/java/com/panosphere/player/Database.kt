package com.panosphere.player

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow

val Context.settings by preferencesDataStore("settings")
@Entity data class MediaRecord(@PrimaryKey val uri: String, val name: String, val music: Boolean = false, val position: Long = 0, val duration: Long = 0, val lastPlayed: Long = 0, val favorite: Boolean = false, val mode: String = "FLAT", val eye: Int = 0, val gyro: Boolean = false, val fov: Float = 75f, val selectedSubtitle: Long = -1, val offset: Long = 0, val fontSize: Float = 24f, val subtitlePosition: Float = 0.85f, val foreground: Long = 0xFFFFFFFF, val background: Long = 0x80000000, val outline: Long = 0xFF000000, val size: Long = 0, val resolution: String = "", val artist: String = "", val playlist: String = "默认列表", @ColumnInfo(defaultValue="16.0") val subtitleBottom: Float = 16f, @ColumnInfo(defaultValue="0") val image: Boolean = false)
@Entity data class SubtitleRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val mediaUri: String, val uri: String, val name: String, val language: String = "und")
@Dao interface MediaDao {
 @Query("SELECT * FROM MediaRecord ORDER BY lastPlayed DESC") fun observe(): Flow<List<MediaRecord>>
 @Query("SELECT * FROM MediaRecord WHERE uri=:uri") suspend fun get(uri: String): MediaRecord?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(record: MediaRecord)
 @Query("DELETE FROM MediaRecord WHERE uri=:uri") suspend fun delete(uri: String)
}
@Dao interface SubtitleDao {
 @Query("SELECT * FROM SubtitleRecord WHERE mediaUri=:uri") suspend fun list(uri: String): List<SubtitleRecord>
 @Insert suspend fun insert(record: SubtitleRecord): Long
}
@Database(entities = [MediaRecord::class, SubtitleRecord::class], version = 3, exportSchema = true)
abstract class AppDatabase: RoomDatabase() {
 abstract fun media(): MediaDao
 abstract fun subtitles(): SubtitleDao
 companion object {
  val MIGRATION_1_2=object: androidx.room.migration.Migration(1,2) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE MediaRecord ADD COLUMN subtitleBottom REAL NOT NULL DEFAULT 16.0") }
  }
  val MIGRATION_2_3=object: androidx.room.migration.Migration(2,3) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {db.execSQL("ALTER TABLE MediaRecord ADD COLUMN image INTEGER NOT NULL DEFAULT 0")}
  }
  fun open(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "panosphere.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3).build()
 }
}


