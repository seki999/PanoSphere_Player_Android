package com.panosphere.player

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PanoramaImageTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private fun ui(block: ()->Unit)=instrumentation.runOnMainSync(block)
 private fun await(message: String,condition: ()->Boolean) {repeat(150) {var ok=false;ui {ok=condition()};if(ok)return;Thread.sleep(100)};fail(message)}
 private fun sample(name: String): Uri {val file=File(context.cacheDir,name);instrumentation.context.assets.open(name).use {source->file.outputStream().use {source.copyTo(it)}};return Uri.fromFile(file)}
 private fun shell(command: String) {instrumentation.uiAutomation.executeShellCommand(command).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}}
 private fun orientationAndScreenshot(c: PlaybackController,name: String) {
  val xy=IntArray(2);var w=0;var h=0
  ui {c.panorama!!.let {it.getLocationOnScreen(xy);w=it.width;h=it.height}}
  // SurfaceView buffers reach the compositor asynchronously, so poll the real screen until the texture appears.
  var last=""
  repeat(20) {attempt->
   Thread.sleep(250)
   val bitmap=instrumentation.uiAutomation.takeScreenshot()!!
   try {
    fun green(fraction: Float): Double=(22..35).flatMap {x->(-2..2).map {dy->bitmap.getPixel(xy[0]+w*x/100,xy[1]+(h*fraction).toInt()+dy*3)}}.filter {android.graphics.Color.blue(it)<180}.map {android.graphics.Color.green(it)}.average()
    val topGreen=green(.25f);val bottomGreen=green(.75f)
    last="$name: image texture north/south greens $topGreen / $bottomGreen after ${attempt+1} screenshots"
    if(topGreen-bottomGreen>60 || attempt==19) {
     val output=File(context.filesDir,"test-artifacts").apply {mkdirs()}
     File(output,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
     android.util.Log.i("PanoSphereTest",last)
     assertTrue(last,topGreen-bottomGreen>60)
     return
    }
   } finally {bitmap.recycle()}
  }
  fail(last)
 }
 @Test fun picturesRenderPersistAndStopWithoutAudio() {
  shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
  var activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val c=activity.controller
  try {
   for((name,mode) in listOf("equirect-360.png" to PlaybackMode.EQUIRECT_360,"equirect-180.png" to PlaybackMode.EQUIRECT_180,"fisheye-180.png" to PlaybackMode.FISHEYE_180,"equirect-360.jpg" to PlaybackMode.EQUIRECT_360)) {
    val uri=sample(name);var old: String?=null
    ui {old=c.current?.uri;c.import(listOf(uri))}
    await("$name loaded") {if(c.error!=null)throw AssertionError(c.error);c.current?.uri!=old && c.current?.image==true && c.imageBitmap!=null && c.imageViewing}
    ui {assertEquals(PlaybackMode.FLAT,c.mode);assertEquals(0,c.player.mediaItemCount);assertFalse(c.player.playWhenReady);c.changeMode(mode)}
    await("image GL attached") {c.panorama?.let {it.width>0 && it.mode==mode && it.hasRenderedFrame && it.isAttachedToWindow}==true}
    orientationAndScreenshot(c,"image-${mode.name}-${name.substringAfterLast('.')}")
    ui {assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON !=0)}
   }
   ui {c.update {copy(fov=58f,eye=0)};c.favorite(c.current!!)}
   Thread.sleep(200)
   val record=c.current!!
   ui {c.open(record);c.stop()}
   await("image reload remains stopped") {c.imageBitmap!=null && !c.imageViewing}
   ui {assertEquals(PlaybackMode.EQUIRECT_360,c.mode);assertEquals(58f,c.current!!.fov);assertTrue(c.current!!.favorite);assertFalse(c.player.playWhenReady)}
   ui {c.play()};await("manual image viewing") {c.imageViewing}
   shell("input keyevent KEYCODE_SLEEP");await("screen-off stops image view") {!c.imageViewing}
   shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
   await("foreground restored") {c.awake?.canPlay()==true}
   ui {assertFalse(c.imageViewing);assertFalse(c.player.playWhenReady);assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON ==0);activity.recreate()}
   Thread.sleep(700)
   ui {activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single();assertSame(c,activity.controller);assertFalse(c.imageViewing)}
  } finally {ui {c.stop();activity.finish()}}
 }
 @Test fun panoramicVideosDefaultToSingleItemLoop() {
  shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
  val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val c=activity.controller
  try {
   val uri=sample("equirect-180.mp4")
   ui {c.import(listOf(uri))}
   await("video plays") {c.playing}
   ui {assertEquals(Player.REPEAT_MODE_OFF,c.repeatMode);c.changeMode(PlaybackMode.EQUIRECT_180);assertEquals(Player.REPEAT_MODE_ONE,c.repeatMode);c.repeatMode=Player.REPEAT_MODE_OFF;c.changeMode(PlaybackMode.EQUIRECT_360);assertEquals(Player.REPEAT_MODE_ONE,c.repeatMode);c.player.seekTo(c.duration-300)}
   Thread.sleep(900)
   await("video loops from end") {c.playing && c.position<3000}
   val record=c.current!!
   ui {c.repeatMode=Player.REPEAT_MODE_OFF;c.open(record)}
   await("saved panoramic mode reopens") {c.playing && c.repeatMode==Player.REPEAT_MODE_ONE}
   ui {c.changeMode(PlaybackMode.FLAT);assertEquals(Player.REPEAT_MODE_OFF,c.repeatMode)}
  } finally {ui {c.stop();activity.finish()}}
 }
 @Test fun decoderAppliesExifAndRejectsCorruptImages() {
  val uri=sample("equirect-360.jpg")
  val file=File(uri.path!!)
  androidx.exifinterface.media.ExifInterface(file.path).apply {setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
  val bitmap=PanoramaImageLoader.load(context,uri)
  try {assertEquals(640,bitmap.width);assertEquals(1280,bitmap.height)} finally {bitmap.recycle()}
  val broken=File(context.cacheDir,"corrupt-image.png").apply {writeText("not an image")}
  try {PanoramaImageLoader.load(context,Uri.fromFile(broken));fail("corrupt image must be rejected")} catch(expected: IllegalStateException) {assertTrue(expected.message!!.contains("图片"))}
 }}
