package com.panosphere.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/** Decode in a worker thread, bounding memory before allocating pixels. */
object PanoramaImageLoader {
 data class Info(val width: Int, val height: Int)
 const val MAX_DECODE_EDGE = 4096

 fun inspect(context: Context, uri: Uri): Info? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
  return if(bounds.outWidth > 0 && bounds.outHeight > 0) Info(bounds.outWidth, bounds.outHeight) else null
 }

 fun sampleSize(width: Int, height: Int, limit: Int = MAX_DECODE_EDGE): Int {
  require(width > 0 && height > 0 && limit > 0)
  var sample = 1
  while(maxOf(width, height).toLong() > limit.toLong() * sample) sample *= 2
  return sample
 }

 fun load(context: Context, uri: Uri): Bitmap {
  val info = inspect(context, uri) ?: error("图片损坏或格式不支持")
  val options = BitmapFactory.Options().apply {
   inSampleSize = sampleSize(info.width, info.height)
   inPreferredConfig = Bitmap.Config.ARGB_8888
  }
  val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
   ?: error("无法解码图片，请检查文件权限或重新导入")
  try {
   val orientation = context.contentResolver.openInputStream(uri)?.use {
    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
   } ?: ExifInterface.ORIENTATION_NORMAL
   val matrix = Matrix()
   when(orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
   }
   if(matrix.isIdentity) return decoded
   return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
    if(it !== decoded) decoded.recycle()
   }
  } catch(e: Exception) {
   decoded.recycle()
   throw IllegalArgumentException("无法读取图片方向信息：${e.message}", e)
  }
 }
}
