package com.panosphere.player

import android.app.Activity
import android.content.*
import android.os.PowerManager
import android.view.WindowManager

object PlaybackPolicy {
 fun keepAwake(foreground: Boolean, interactive: Boolean, playing: Boolean) = foreground && interactive && playing
 fun subtitleTime(position: Long, offset: Long) = position - offset
}
class ScreenAwakeController(private val activity: Activity, private val stop: () -> Unit) {
 private var foreground = false
 private val power = activity.getSystemService(PowerManager::class.java)
 private val receiver = object: BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { if(i?.action == Intent.ACTION_SCREEN_OFF) { stop(); update(false) } } }
 init { activity.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
 fun foreground(value: Boolean) { foreground = value; if(!value || !power.isInteractive) stop(); update(false) }
 fun update(playing: Boolean) {
  if(PlaybackPolicy.keepAwake(foreground, power.isInteractive, playing)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
 }
 fun canPlay() = foreground && power.isInteractive
 fun release() { activity.unregisterReceiver(receiver); activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
}
