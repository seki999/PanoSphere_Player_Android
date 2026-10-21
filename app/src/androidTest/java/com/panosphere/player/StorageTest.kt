package com.panosphere.player
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class StorageTest {
 @Test fun databaseRoundTrip() = runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try { val media=MediaRecord("content://test/1","测试",position=12345,mode="EQUIRECT_180",offset=-200); db.media().save(media); assertEquals(media,db.media().get(media.uri)); val id=db.subtitles().insert(SubtitleRecord(mediaUri=media.uri,uri="content://test/sub",name="test.srt")); assertEquals(id,db.subtitles().list(media.uri).single().id) } finally {db.close()}
 }
 @Test fun privateCopyFallbackRemainsReadable() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val file=java.io.File(context.cacheDir,"permission-test.srt").apply {writeText("test")}
  val retained=UriPermissionManager(context).retain(android.net.Uri.fromFile(file),"subtitles")
  assertEquals("test",context.contentResolver.openInputStream(retained)!!.bufferedReader().use {it.readText()})
 }
 @Test fun migrationPreservesHistory() = runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="migration-test.db"
  context.deleteDatabase(name)
  fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build()
  val record=MediaRecord("content://migration/1","保留历史",position=9876,favorite=true)
  var db=open();db.media().save(record);db.close()
  android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,0).use {raw->raw.execSQL("ALTER TABLE MediaRecord DROP COLUMN subtitleBottom");raw.execSQL("ALTER TABLE MediaRecord DROP COLUMN image");raw.version=1}
  db=open()
  try {val restored=db.media().get(record.uri)!!;assertEquals(9876L,restored.position);assertTrue(restored.favorite);assertEquals(16f,restored.subtitleBottom)} finally {db.close();context.deleteDatabase(name)}
 } @Test fun versionTwoUpgradeKeepsVideoSettingsAndSubtitles() = runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="image-migration-test.db";context.deleteDatabase(name)
  fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build()
  val record=MediaRecord("content://migration/video","已有全景视频",position=2345,mode="EQUIRECT_360",favorite=true,fov=60f)
  var db=open();db.media().save(record);db.subtitles().insert(SubtitleRecord(mediaUri=record.uri,uri="content://migration/sub",name="old.srt"));db.close()
  android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,0).use {raw->raw.execSQL("ALTER TABLE MediaRecord DROP COLUMN image");raw.version=2}
  db=open()
  try {assertEquals(record,db.media().get(record.uri));assertFalse(db.media().get(record.uri)!!.image);assertEquals(1,db.subtitles().list(record.uri).size)} finally {db.close();context.deleteDatabase(name)}
 }}

