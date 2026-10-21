package com.panosphere.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class UriPermissionManager(private val context: Context) {
 fun retain(uri: Uri, folder: String): Uri {
  return try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); uri }
  catch(e: SecurityException) {
   val dir = File(context.filesDir, folder).apply { mkdirs() }
   val target = File(dir, java.util.UUID.randomUUID().toString() + "_" + name(uri).replace(Regex("[^\\p{L}\\p{N}. -]"), "_"))
   context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "无法读取文件" }; target.outputStream().use { input.copyTo(it) } }
   Uri.fromFile(target)
  }
 }
 fun name(uri: Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment ?: "媒体"
}
data class SubtitleCue(val start: Long, val end: Long, val text: String)
object SubtitleParser {
 private fun time(value: String): Long {
  val p = value.trim().replace(',', '.').split(':'); require(p.size in 2..3) { "无效字幕时间戳：$value" }
  require(p.all {it.toDoubleOrNull()?.let {n->n.isFinite() && n>=0}==true} && p.last().toDouble()<60 && (p.size!=3 || p[1].toDouble()<60)) {"字幕时间戳超出范围：$value"}
  return ((p.last().toDouble() + p[p.size-2].toDouble()*60 + if(p.size == 3) p[0].toDouble()*3600 else 0.0)*1000).toLong()
 }
 fun decode(bytes: ByteArray): String {
  require(bytes.size <= 8*1024*1024) { "字幕超过 8 MB" }
  if(bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))) return String(bytes, Charsets.UTF_16)
  return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
  catch(e: java.nio.charset.CharacterCodingException) { try { charset("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() } catch(other: java.nio.charset.CharacterCodingException) {throw IllegalArgumentException("无法识别字幕编码，请转换为 UTF-8",other)} }
 }
 fun parse(text: String, extension: String): List<SubtitleCue> {
  val normalized = text.replace("\r", "")
  val result = if(extension.lowercase() in listOf("ass", "ssa")) {
   var fields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
   var events = false
   normalized.lines().mapNotNull { line ->
    if(line.startsWith("[")) events = line.equals("[Events]", true)
    if(events && line.startsWith("Format:", true)) fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
    if(events && line.startsWith("Dialogue:", true)) {
     val p = line.substringAfter(':').split(',', limit = fields.size)
     require(p.size == fields.size) { "损坏的 ASS 对话行" }
     SubtitleCue(time(p[fields.indexOf("start")]), time(p[fields.indexOf("end")]), p[fields.indexOf("text")].replace(Regex("\\{[^}]*\\}"), "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " "))
    } else null
   }
  } else {
   require(extension.lowercase() in listOf("srt", "vtt")) { "仅支持 SRT、VTT、ASS、SSA" }
   normalized.split(Regex("\n\\s*\n")).mapNotNull { block ->
    val lines = block.lines(); val index = lines.indexOfFirst { "-->" in it }
    if(index < 0) null else {
     val t = lines[index].split("-->"); require(t.size == 2) { "字幕时间格式错误" }
     SubtitleCue(time(t[0]), time(t[1].trim().substringBefore(' ')), lines.drop(index+1).joinToString("\n").replace(Regex("<[^>]*>"), ""))
    }
   }
  }
  require(result.isNotEmpty()) { "字幕为空或时间戳格式错误" }
  require(result.all { it.start >= 0 && it.end > it.start }) { "字幕时间范围错误" }
  return result.map {it.copy(text=it.text.trimEnd())}.sortedBy { it.start }
 }
}

fun java.io.InputStream.readBytesLimited(): ByteArray {
 val output=java.io.ByteArrayOutputStream()
 val buffer=ByteArray(8192)
 while(true) { val count=read(buffer); if(count<0) break; require(output.size()+count<=8*1024*1024) {"字幕超过 8 MB"}; output.write(buffer,0,count) }
 return output.toByteArray()
}



