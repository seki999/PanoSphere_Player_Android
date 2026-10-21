package com.panosphere.player

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScreenTimeoutTest {
 @Test fun allMediaModesStayAwakePastTimeoutAndPauseAllowsSleep() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  fun ui(block: ()->Unit)=instrumentation.runOnMainSync(block)
  fun shell(command: String): String=instrumentation.uiAutomation.executeShellCommand(command).use {descriptor->android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText().trim()}}
  fun await(condition: ()->Boolean) {repeat(150) {var ok=false;ui {ok=condition()};if(ok)return;Thread.sleep(100)};fail("Timed out waiting for playback state")}
  fun sample(name: String): Uri {val file=File(context.cacheDir,name);instrumentation.context.assets.open(name).use {source->file.outputStream().use {source.copyTo(it)}};return Uri.fromFile(file)}
  val timeout=shell("settings get system screen_off_timeout")
  val stayOn=shell("settings get global stay_on_while_plugged_in")
  var activity: MainActivity?=null
  try {
   // Test environment only: always restored in finally. Production app never changes these settings.
   shell("settings put system screen_off_timeout 5000");shell("settings put global stay_on_while_plugged_in 0")
   shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
   activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
   val c=activity.controller
   val power=context.getSystemService(PowerManager::class.java)
   // The shortened timeout can expire while the activity is launching; the app then correctly stops, so start from an awake, foreground state.
   shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
   await {power.isInteractive && c.awake?.canPlay()==true}
   listOf(PlaybackMode.FLAT to "grid-360.mp4",PlaybackMode.EQUIRECT_180 to "equirect-180.mp4",PlaybackMode.EQUIRECT_360 to "grid-360.mp4",PlaybackMode.FLAT to "tone.mp3").forEach { (mode,name)->
    val uri=sample(name);var old: String?=null
    ui {old=c.current?.uri;c.import(listOf(uri));c.repeatMode=androidx.media3.common.Player.REPEAT_MODE_ONE}
    await {c.current?.uri!=old && c.current?.name==name && c.playing}
    ui {c.changeMode(mode)}
    Thread.sleep(12500) // Exceeds the emulator's 10-second minimum effective timeout.
    ui {assertTrue("$name / $mode must remain interactive",power.isInteractive);assertTrue("$name / $mode: Requested=${c.player.playWhenReady}, actual=${c.player.isPlaying}, state=${c.player.playbackState}, error=${c.error}",c.playing)}
   }
   ui {c.stop()}
   Thread.sleep(12500)
   assertFalse("Paused playback must allow normal automatic sleep",power.isInteractive)
   shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
   ui {assertFalse(c.playing);assertFalse(c.player.playWhenReady)}
  } finally {
   shell("settings put system screen_off_timeout $timeout");shell("settings put global stay_on_while_plugged_in $stayOn")
   shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
   activity?.let {ui {it.controller.stop();it.finish()}}
  }
 }
}

