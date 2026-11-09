@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.panosphere.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer

/**
 * Offsets only the text renderer clock, keeping audio/video on their original clock,
 * and adds bundled FFmpeg/dav1d software renderers for high-resolution (e.g. >4096px VR) or AV1 media
 * where Android's C2 software MediaCodec (`c2.android.av1-dav1d.decoder`) fails due to its 4096px limit.
 */
class SubtitleRenderersFactory(
 context: Context,
 private val forceSoftwareVideo: () -> Boolean = { false },
 private val offsetUs: () -> Long
): DefaultRenderersFactory(context) {
 init {
  setEnableDecoderFallback(true)
  setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
 }

 override fun buildVideoRenderers(
  context: Context,
  extensionRendererMode: Int,
  mediaCodecSelector: MediaCodecSelector,
  enableDecoderFallback: Boolean,
  eventHandler: Handler,
  eventListener: VideoRendererEventListener,
  allowedVideoJoiningTimeMs: Long,
  out: java.util.ArrayList<Renderer>
 ) {
  out.add(object: MediaCodecVideoRenderer(
   context,
   codecAdapterFactory,
   mediaCodecSelector,
   allowedVideoJoiningTimeMs,
   enableDecoderFallback,
   eventHandler,
   eventListener,
   MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
  ) {
   override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int {
    val mime = format.sampleMimeType
    val ffmpegSupported = mime != null && FfmpegLibrary.supportsFormat(mime)
    if (ffmpegSupported && forceSoftwareVideo()) {
     return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
    }
    val base = super.supportsFormat(mediaCodecSelector, format)
    if (!ffmpegSupported) return base
    val decoders = getDecoderInfos(mediaCodecSelector, format, format.drmInitData != null)
    if (decoders.isEmpty() || decoders.none { it.isFormatSupported(context, format) }) {
     val adaptive = RendererCapabilities.getAdaptiveSupport(base)
     val tunneling = RendererCapabilities.getTunnelingSupport(base)
     val hardware = RendererCapabilities.getHardwareAccelerationSupport(base)
     val decoderSupport = RendererCapabilities.getDecoderSupport(base)
     return RendererCapabilities.create(C.FORMAT_EXCEEDS_CAPABILITIES, adaptive, tunneling, hardware, decoderSupport)
    }
    return base
   }

   override fun getDecoderInfos(
    mediaCodecSelector: MediaCodecSelector,
    format: Format,
    requiresSecureDecoder: Boolean
   ): List<MediaCodecInfo> {
    val mime = format.sampleMimeType
    val ffmpegSupported = mime != null && FfmpegLibrary.supportsFormat(mime)
    if (ffmpegSupported && forceSoftwareVideo()) return emptyList()
    val infos = super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
    if (!ffmpegSupported) return infos
    return infos.filterNot { info ->
     shouldExcludeSoftwareMediaCodec(info.name, info.softwareOnly, format.width, format.height, true)
    }
   }
  })

  if (extensionRendererMode != EXTENSION_RENDERER_MODE_OFF) {
   try {
    val index = if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) out.size - 1 else out.size
    out.add(index, FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY))
   } catch (e: Exception) {
    android.util.Log.w("PanoSphere", "Failed to instantiate FfmpegVideoRenderer", e)
   }
  }
 }

 override fun buildAudioRenderers(
  context: Context,
  extensionRendererMode: Int,
  mediaCodecSelector: MediaCodecSelector,
  enableDecoderFallback: Boolean,
  audioSink: AudioSink,
  eventHandler: Handler,
  eventListener: AudioRendererEventListener,
  out: java.util.ArrayList<Renderer>
 ) {
  super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
  if (extensionRendererMode != EXTENSION_RENDERER_MODE_OFF) {
   try {
    val index = if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) out.size - 1 else out.size
    out.add(index, FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
   } catch (e: Exception) {
    android.util.Log.w("PanoSphere", "Failed to instantiate FfmpegAudioRenderer", e)
   }
  }
 }

 override fun buildTextRenderers(context: Context, output: TextOutput, looper: Looper, extensionRendererMode: Int, out: java.util.ArrayList<Renderer>) {
  val text=java.util.ArrayList<Renderer>()
  super.buildTextRenderers(context,output,looper,extensionRendererMode,text)
  text.forEach { delegate -> out.add(object: Renderer by delegate {
   override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {delegate.resetPosition(positionUs-offsetUs(),sampleStreamIsResetToKeyFrame)}
   override fun render(positionUs: Long, elapsedRealtimeUs: Long) { delegate.render(positionUs-offsetUs(),elapsedRealtimeUs) }
  }) }
 }

 companion object {
  /**
   * Android's AOSP Codec2 software video decoders (e.g. `c2.android.av1-dav1d.decoder`) hardcode
   * `inRange(2, 4096)` for width and height in `SizeSetter`, causing `ERROR_CODE_DECODER_INIT_FAILED`
   * on >4096px VR videos (such as 4320x2160). Exclude them when bundled FFmpeg/dav1d is available.
   */
  fun shouldExcludeSoftwareMediaCodec(
   codecName: String,
   softwareOnly: Boolean,
   width: Int,
   height: Int,
   ffmpegSupported: Boolean
  ): Boolean {
   if (!ffmpegSupported) return false
   val isPlatformSoftware = softwareOnly ||
    codecName.startsWith("c2.android.", ignoreCase = true) ||
    codecName.startsWith("OMX.google.", ignoreCase = true)
   return isPlatformSoftware && (width > 4096 || height > 4096)
  }

  fun shouldRetryWithSoftwareVideo(errorCode: Int, alreadyRetried: Boolean, isVideo: Boolean): Boolean {
   if (alreadyRetried || !isVideo) return false
   return errorCode in setOf(
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
   )
  }
 }
}


