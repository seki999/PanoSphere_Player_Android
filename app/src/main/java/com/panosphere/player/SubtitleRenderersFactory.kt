@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.panosphere.player

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput

/** Offsets only the text renderer clock, keeping audio/video on their original clock. */
class SubtitleRenderersFactory(context: Context, private val offsetUs: () -> Long): DefaultRenderersFactory(context) {
 override fun buildTextRenderers(context: Context, output: TextOutput, looper: Looper, extensionRendererMode: Int, out: java.util.ArrayList<Renderer>) {
  val text=java.util.ArrayList<Renderer>()
  super.buildTextRenderers(context,output,looper,extensionRendererMode,text)
  text.forEach { delegate -> out.add(object: Renderer by delegate {
   override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {delegate.resetPosition(positionUs-offsetUs(),sampleStreamIsResetToKeyFrame)}
   override fun render(positionUs: Long, elapsedRealtimeUs: Long) { delegate.render(positionUs-offsetUs(),elapsedRealtimeUs) }
  }) }
 }
}

