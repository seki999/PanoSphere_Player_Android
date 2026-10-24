package com.panosphere.player

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoCompletionTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private fun ui(block: ()->Unit)=instrumentation.runOnMainSync(block)
 private fun await(message: String,condition: ()->Boolean) {
  repeat(200) {var ok=false;ui {ok=condition()};if(ok)return;Thread.sleep(100)}
  fail(message)
 }
 private fun sample(name: String): Uri {
  val file=File(context.cacheDir,name)
  instrumentation.context.assets.open(name).use {source->file.outputStream().use {source.copyTo(it)}}
  return Uri.fromFile(file)
 }
 @Test fun completedVideoRewindsWithoutAdvancingAndManualNextStillWorks() {
  val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val c=activity.controller
  try {
   val uris=listOf(sample("grid-360.mp4"),sample("equirect-180.mp4"))
   ui {c.import(uris)}
   await("first video must be ready") {if(c.error!=null)throw AssertionError(c.error);c.playing && c.duration>0}
   var firstUri=""
   ui {firstUri=c.current!!.uri}
   for(repeat in listOf(Player.REPEAT_MODE_OFF,Player.REPEAT_MODE_ALL)) {
    ui {c.repeatMode=repeat;c.play();c.player.seekTo(c.duration-250)}
    await("finished video must stay selected, pause and rewind") {if(c.error!=null)throw AssertionError(c.error);c.current?.uri==firstUri && !c.player.playWhenReady && c.player.currentPosition==0L && c.position==0L}
    Thread.sleep(500)
    ui {assertEquals(firstUri,c.current!!.uri);assertFalse(c.playbackRequested);assertFalse(c.playing);assertEquals(0L,c.current!!.position);assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON==0)}
    assertEquals(0L,runBlocking {c.database.media().get(firstUri)!!.position})
   }
   ui {c.next(1)}
   await("manual next must still open second video") {c.current?.uri!=firstUri && c.playing}
   ui {c.next(-1)}
   await("manual previous returns to the first video") {c.current?.uri==firstUri && c.playing}
   ui {c.stop();c.player.seekTo(1500);c.save()}
   ui {assertEquals("manual stop must preserve resume position",1500L,c.current!!.position)}
  } finally {ui {c.stop();activity.finish()}}
 }
}
