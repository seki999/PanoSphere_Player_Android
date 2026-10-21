package com.panosphere.player
import org.junit.Assert.*
import org.junit.Test
class CoreTest {
 @Test fun awakeRequiresAllConditions() { for(f in listOf(false,true)) for(s in listOf(false,true)) for(p in listOf(false,true)) assertEquals(f && s && p,PlaybackPolicy.keepAwake(f,s,p)) }
 @Test fun offsetCanAdvanceAndDelay() { assertEquals(900L,PlaybackPolicy.subtitleTime(1000,100)); assertEquals(1100L,PlaybackPolicy.subtitleTime(1000,-100)) }
 @Test fun parsesSrtBom() { val text="\uFEFF1\n00:00:01,200 --> 00:00:02,500\n你好\n"; val cues=SubtitleParser.parse(SubtitleParser.decode(text.toByteArray()),"srt"); assertEquals(1200L,cues[0].start); assertEquals("你好",cues[0].text) }
 @Test fun parsesVttSettings() { assertEquals(1000L,SubtitleParser.parse("WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nHello","vtt")[0].start) }
 @Test fun parsesAssFormatAndCommas() { val cues=SubtitleParser.parse("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.20,0:00:03.00,Default,,0,0,0,,{\\b1}Hello, world\\N第二行","ass"); assertEquals("Hello, world\n第二行",cues[0].text) }
 @Test(expected=IllegalArgumentException::class) fun rejectsEmpty() { SubtitleParser.parse("nothing","srt") }
 @Test(expected=IllegalArgumentException::class) fun rejectsBrokenTime() { SubtitleParser.parse("1\nabc --> 00:00:02,000\nx","srt") }
 @Test fun utf16Supported() { assertTrue(SubtitleParser.decode("你好".toByteArray(Charsets.UTF_16)).contains("你好")) }
 @Test fun hemisphereLimitsYaw() { assertEquals(50f,ProjectionMath.clampYaw(130f,PlaybackMode.EQUIRECT_180,80f)); assertEquals(-50f,ProjectionMath.clampYaw(-130f,PlaybackMode.FISHEYE_180,80f)) }
 @Test fun sphereWrapNotClamped() { assertEquals(720f,ProjectionMath.clampYaw(720f,PlaybackMode.EQUIRECT_360,80f)) }
 @Test fun modesRemainDistinct() { assertEquals(4,PlaybackMode.entries.size); PlaybackMode.entries.forEach { assertEquals(it,PlaybackMode.valueOf(it.name)) } }
 @Test fun hemisphereFrustumNeverCrossesRearPlane() {
  for(fov in listOf(35f,75f,100f)) for(aspect in listOf(.5f,1f,2.2f)) for(pitch in listOf(-100f,0f,100f)) for(yaw in listOf(-180f,0f,180f)) {
   val (y,p)=ProjectionMath.cameraAngles(yaw,pitch,fov,aspect,PlaybackMode.FISHEYE_180)
   val yr=Math.toRadians(y.toDouble());val pr=Math.toRadians(p.toDouble());val t=kotlin.math.tan(Math.toRadians(fov.toDouble()/2))
   val minimum=kotlin.math.cos(yr)*(kotlin.math.cos(pr)-kotlin.math.abs(kotlin.math.sin(pr))*t)-kotlin.math.abs(kotlin.math.sin(yr))*t*aspect
   assertTrue("rear-facing corner: $minimum",minimum>=-0.000001)
  }
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsOutOfRangeTimestamp() {SubtitleParser.parse("1\n00:99:00,000 --> 01:00:00,000\nx","srt")} @Test fun imageDecodeIsBoundedBeforeAllocation() {assertEquals(1,PanoramaImageLoader.sampleSize(4096,2048));assertEquals(2,PanoramaImageLoader.sampleSize(8192,4096));assertEquals(8,PanoramaImageLoader.sampleSize(20000,10000));assertTrue(20000/PanoramaImageLoader.sampleSize(20000,10000)<=4096)}
 @Test(expected=IllegalArgumentException::class) fun imageDecodeRejectsInvalidSize() {PanoramaImageLoader.sampleSize(0,100)}}

