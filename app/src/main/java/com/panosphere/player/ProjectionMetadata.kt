package com.panosphere.player

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads only validated ISO BMFF box paths; filenames are never projection evidence. */
object ProjectionMetadata {
 data class Result(val mode: PlaybackMode, val eye: Int)
 fun read(context: Context, uri: Uri): Result? {
  context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { descriptor ->
   FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
    var offset=descriptor.startOffset
    val end=if(descriptor.length>=0) offset+descriptor.length else channel.size()
    while(offset+8<=end) {
     val head=ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
     channel.position(offset); if(channel.read(head)<8) return null; head.flip()
     var size=head.int.toLong() and 0xFFFFFFFFL; val type=head.int; var header=8
     if(size==1L) { if(head.remaining()<8) return null;size=head.long;header=16 }
     if(size==0L)size=end-offset
     if(size<header || size>end-offset)return null
     if(type==fourcc("moov")) {
      require(size<=16*1024*1024) { "投影元数据过大，请手动选择" }
      val data=ByteBuffer.allocate((size-header).toInt()).order(ByteOrder.BIG_ENDIAN)
      channel.position(offset+header); while(data.hasRemaining()) if(channel.read(data)<0)return null
      data.flip();return find(data,0,data.limit(),0)
     }
     offset+=size
    }
   }
  };return null
 }
 private fun fourcc(value: String)=ByteBuffer.wrap(value.toByteArray(Charsets.US_ASCII)).int
 private fun find(data: ByteBuffer,start: Int,end: Int,depth: Int): Result? {
  if(depth>16)return null
  var p=start
  var foundMode: PlaybackMode?=null;var foundEye=0
  while(p+8<=end) {
   val size=data.getInt(p);val type=data.getInt(p+4)
   if(size<8 || size>end-p)return null
   val payload=p+8;val boxEnd=p+size
   when(type) {
    fourcc("equi") -> if(size==28 && data.getInt(payload)==0) {
     val bounds=(0..3).map { (data.getInt(payload+4+it*4).toLong() and 0xFFFFFFFFL)/4294967296.0 }
     foundMode=when { bounds.all {it==0.0}->PlaybackMode.EQUIRECT_360; bounds[0]==0.0 && bounds[1]==0.0 && kotlin.math.abs(bounds[2]-.25)<.00001 && kotlin.math.abs(bounds[3]-.25)<.00001 ->PlaybackMode.EQUIRECT_180;else->null }
    }
    fourcc("st3d") -> if(size>=13 && data.getInt(payload)==0) { foundEye=if(data.get(payload+4).toInt()==2)1 else if(data.get(payload+4).toInt()==0)0 else return null }
    else -> {
     val skip=when(type) {
      fourcc("moov"),fourcc("trak"),fourcc("mdia"),fourcc("minf"),fourcc("stbl"),fourcc("sv3d"),fourcc("proj") ->0
      fourcc("stsd") ->8
      fourcc("avc1"),fourcc("hvc1"),fourcc("hev1"),fourcc("av01"),fourcc("vp09") ->78
      else -> -1
     }
     if(skip>=0 && payload+skip<boxEnd) { val result=find(data,payload+skip,boxEnd,depth+1);if(result!=null) {foundMode=result.mode; if(result.eye!=0)foundEye=result.eye} }
    }
   }
   p=boxEnd
  }
  return foundMode?.let {Result(it,foundEye)}
 }
}
