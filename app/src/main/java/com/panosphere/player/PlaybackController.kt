@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.panosphere.player

import android.app.Application
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.compose.runtime.*

class PlaybackController(app: Application): AndroidViewModel(app) {
 val database=AppDatabase.open(app)
 val permissions=UriPermissionManager(app)
 val library=database.media().observe().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 @Volatile private var subtitleOffsetUs=0L
 val player=ExoPlayer.Builder(app,SubtitleRenderersFactory(app) {subtitleOffsetUs}).build().apply {
  setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
  setHandleAudioBecomingNoisy(true)
 }
 var current by mutableStateOf<MediaRecord?>(null); private set
 var imageBitmap by mutableStateOf<android.graphics.Bitmap?>(null); private set
 var imageViewing by mutableStateOf(false); private set
 var playing by mutableStateOf(false); private set
 var playbackRequested by mutableStateOf(false); private set
 var metadata by mutableStateOf(MediaMetadata.EMPTY); private set
 private var playbackGeneration=0L
 var position by mutableLongStateOf(0); private set
 var duration by mutableLongStateOf(0); private set
 var error by mutableStateOf<String?>(null)
 var mode by mutableStateOf(PlaybackMode.FLAT); private set
 var subtitles by mutableStateOf<List<SubtitleRecord>>(emptyList()); private set
 var cues by mutableStateOf<List<SubtitleCue>>(emptyList()); private set
 var embeddedText by mutableStateOf(""); private set
 var tracks by mutableStateOf(Tracks.EMPTY); private set
 var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
 private var awakeReference=java.lang.ref.WeakReference<ScreenAwakeController>(null)
 var awake: ScreenAwakeController?
  get()=awakeReference.get()
  set(value) {awakeReference=java.lang.ref.WeakReference(value)}
 private var panoramaReference=java.lang.ref.WeakReference<PanoramaView>(null)
 var panorama: PanoramaView?
  get()=panoramaReference.get()
  set(value) {panoramaReference=java.lang.ref.WeakReference(value)}
 private var queue=emptyList<MediaRecord>()
 private var cueJob: Job? = null
 private var openJob: Job? = null
 private var loadedSubtitle: Long = -1
 init {
  player.addListener(object: Player.Listener {
      override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) {if(current?.image!=true)playbackRequested=value;refreshAwake()}
   override fun onMediaMetadataChanged(value: MediaMetadata) {metadata=value}
   override fun onEvents(player: Player, events: Player.Events) {if(current?.image!=true) {playing=player.isPlaying;playbackRequested=player.playWhenReady};refreshAwake()}
   override fun onIsPlayingChanged(value: Boolean) { if(current?.image!=true)playing=player.isPlaying; refreshAwake(); panorama?.active((imageViewing || player.playWhenReady) && awake?.canPlay()==true); if(!player.isPlaying) save() }
   override fun onPlayerError(e: PlaybackException) { android.util.Log.e("PanoSphere","Playback error",e); error="播放失败：${e.errorCodeName}\n${e.cause?.message ?: e.message}\n请检查文件权限、封装和设备解码能力。"; playing=false; awake?.update(false) }
   override fun onTracksChanged(value: Tracks) { tracks=value }
   override fun onCues(group: androidx.media3.common.text.CueGroup) { embeddedText=group.cues.mapNotNull { it.text }.joinToString("\n") }
   override fun onPlaybackStateChanged(state: Int) {
    if(state!=Player.STATE_ENDED || current?.image==true) return
    val generation=playbackGeneration
    viewModelScope.launch {
     if(generation!=playbackGeneration || player.playbackState!=Player.STATE_ENDED || !player.playWhenReady) return@launch
     save()
     val hasNext=queue.indexOfFirst {it.uri==current?.uri} in 0 until queue.lastIndex
     when(PlaybackCompletionPolicy.decide(current?.music==true,repeatMode,hasNext)) {
      CompletionAction.REPEAT_CURRENT -> {player.seekTo(0);play()}
      CompletionAction.ADVANCE -> next(1)
      CompletionAction.STOP -> {
       stop()
       if(current?.music==false) {
        player.seekTo(0)
        position=0
        save()
       }
      }
     }
    }
   }
  })
  viewModelScope.launch { while(isActive) { position=player.currentPosition.coerceAtLeast(0); duration=player.duration.coerceAtLeast(0); delay(100) } }
  viewModelScope.launch { while(isActive) { delay(3000); save() } }
 }
 fun report(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch(e: Exception) { if(e is CancellationException) throw e; android.util.Log.e("PanoSphere","Operation failed",e); error=e.message ?: "操作失败" } } }
 fun import(uris: List<Uri>) {
 val requestedGeneration=playbackGeneration
 report {
  val imported=withContext(Dispatchers.IO) { uris.map { source ->
   val name=permissions.name(source); val uri=permissions.retain(source,"media"); val existing=database.media().get(uri.toString())
   existing ?: metadata(uri,name).also { database.media().save(it) }
  } }
  if(imported.isNotEmpty()) open(imported.first(),imported,autoPlay=requestedGeneration==playbackGeneration)
 }
 }
 private fun metadata(uri: Uri, name: String): MediaRecord {
  val context=getApplication<Application>()
  val image=PanoramaImageLoader.inspect(context,uri)
  if(image!=null) return MediaRecord(uri.toString(),name,image=true,resolution="${image.width} × ${image.height}",size=context.contentResolver.openAssetFileDescriptor(uri,"r")?.use {it.length} ?: 0)
  val retriever=MediaMetadataRetriever()
  return try {
   retriever.setDataSource(getApplication(),uri)
   val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
   MediaRecord(uri.toString(),name,music=width==null,duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,resolution=width?.let { "$it × ${retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)}" } ?: "",artist=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知歌手", size=getApplication<Application>().contentResolver.openAssetFileDescriptor(uri,"r")?.use { it.length } ?: 0)
  } finally { retriever.release() }
 }
 fun open(record: MediaRecord, items: List<MediaRecord> = library.value, autoPlay: Boolean = true) {
  stop(); error=null; openJob?.cancel(); cueJob?.cancel(); queue=items; imageBitmap=null; cues=emptyList(); embeddedText=""; loadedSubtitle=-1
  val requestedGeneration=playbackGeneration
  openJob=viewModelScope.launch {
   current=database.media().get(record.uri) ?: record; mode=PlaybackMode.valueOf(current!!.mode); subtitleOffsetUs=current!!.offset*1000
   try {
    getApplication<Application>().contentResolver.openAssetFileDescriptor(Uri.parse(record.uri),"r")?.close() ?: error("文件不存在")
    if(current!!.image) {
     applyPanoramaLoopDefault()
     player.clearMediaItems()
     val bitmap=withContext(Dispatchers.IO) {PanoramaImageLoader.load(getApplication(),Uri.parse(record.uri))}
     imageBitmap=bitmap;subtitles=emptyList();tracks=Tracks.EMPTY;metadata=MediaMetadata.EMPTY
     if(autoPlay && requestedGeneration==playbackGeneration) play()
     return@launch
    }
    applyPanoramaLoopDefault()
    player.setMediaItem(MediaItem.fromUri(record.uri)); player.prepare(); player.seekTo(current!!.position)
    subtitles=database.subtitles().list(record.uri)
    if(current!!.selectedSubtitle>=0) selectSubtitle(current!!.selectedSubtitle)
    if(autoPlay && requestedGeneration==playbackGeneration) play()
   } catch(e: Exception) { if(e is CancellationException)throw e; error="无法打开 ${record.name}：${e.message}。可在列表中清理失效记录。" }
  }
 }
 fun play() { if(awake?.canPlay()!=true) return; if(current?.image==true) {if(imageBitmap==null) {current?.let {open(it,queue)};return};imageViewing=true;playbackRequested=true;panorama?.active(true);refreshAwake();update {copy(lastPlayed=System.currentTimeMillis())};return}; if(player.playbackState==Player.STATE_IDLE || player.playerError!=null) player.prepare(); player.play(); update { copy(lastPlayed=System.currentTimeMillis()) } }
 fun toggle() { if(imageViewing || player.playWhenReady) stop() else play() }
 fun stop() { playbackGeneration++; imageViewing=false;player.pause();playbackRequested=false; save(); playing=false; awake?.update(false); panorama?.active(false) }
 fun resumeRendering() { panorama?.active((imageViewing || playing) && awake?.canPlay()==true) }
  fun refreshAwake() {awake?.update(imageViewing || (player.playWhenReady && player.playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_NONE && player.playbackState in listOf(Player.STATE_BUFFERING,Player.STATE_READY)))}
 fun save() { val record=current ?: return; if(record.image) {viewModelScope.launch {database.media().save(record)};return}; val updated=record.copy(artist=player.mediaMetadata.artist?.toString() ?: record.artist,resolution=player.videoSize.let {if(it.width>0) "${it.width} × ${it.height}" else record.resolution},position=player.currentPosition.coerceAtLeast(0),duration=player.duration.coerceAtLeast(0).takeIf { it>0 } ?: record.duration); current=updated; viewModelScope.launch { database.media().save(updated) } }
 fun update(transform: MediaRecord.() -> MediaRecord) { val record=current ?: return; val updated=record.transform(); current=updated; subtitleOffsetUs=updated.offset*1000; if(record.offset!=updated.offset && updated.selectedSubtitle==-2L) {embeddedText=""; player.seekTo((player.currentPosition-1).coerceAtLeast(0))}; viewModelScope.launch { database.media().save(updated) } }
 fun detectProjection() = report {
  val media=current ?: return@report
  if(media.image) {error="图片请根据原始投影手动选择 180° 经纬、180° 鱼眼或 360° 经纬，不根据扩展名猜测投影。";return@report}
  val result=withContext(Dispatchers.IO) { ProjectionMetadata.read(getApplication(),Uri.parse(media.uri)) }
  if(current?.uri!=media.uri) return@report
  if(result==null) error="没有可验证的标准 MP4 equi 投影元数据，请手动选择。不会依据文件扩展名推断全景。"
  else {changeMode(result.mode);update {copy(eye=result.eye)}}
 }
  private var repeatBeforePanorama=Player.REPEAT_MODE_OFF
 private var panoramaLoopApplied=false
 private fun applyPanoramaLoopDefault() {
  val panoramicVideo=current?.let {!it.image && !it.music && mode!=PlaybackMode.FLAT}==true
  if(panoramicVideo) {
   if(!panoramaLoopApplied)repeatBeforePanorama=repeatMode
   repeatMode=Player.REPEAT_MODE_ONE;panoramaLoopApplied=true
  } else if(panoramaLoopApplied) {
   if(repeatMode==Player.REPEAT_MODE_ONE)repeatMode=repeatBeforePanorama
   panoramaLoopApplied=false
  }
  if(current?.let {!it.music && !it.image}==true && repeatMode==Player.REPEAT_MODE_ALL) repeatMode=Player.REPEAT_MODE_OFF
 }
 fun changeMode(value: PlaybackMode) {mode=value;update {copy(mode=value.name)};applyPanoramaLoopDefault()}
 fun next(direction: Int) { if(queue.isEmpty()) return; val index=queue.indexOfFirst { it.uri==current?.uri }; val next=if(player.shuffleModeEnabled) queue.indices.random() else Math.floorMod(index+direction,queue.size); open(queue[next],queue) }
 fun favorite(record: MediaRecord) = report { val changed=record.copy(favorite=!record.favorite); database.media().save(changed); if(current?.uri==record.uri) current=changed }
 fun delete(record: MediaRecord) = report { if(current?.uri==record.uri) { stop(); player.clearMediaItems(); current=null;imageBitmap=null }; database.media().delete(record.uri) }
 fun importSubtitle(uri: Uri) = report {
  val media=current ?: return@report
  val result=withContext(Dispatchers.IO) {
   val name=permissions.name(uri); val retained=permissions.retain(uri,"subtitles")
   val bytes=getApplication<Application>().contentResolver.openInputStream(retained)?.use { it.readBytesLimited() } ?: error("无法读取字幕")
   SubtitleParser.parse(SubtitleParser.decode(bytes),name.substringAfterLast('.'))
   database.subtitles().insert(SubtitleRecord(mediaUri=media.uri,uri=retained.toString(),name=name))
  }
  if(current?.uri==media.uri) { subtitles=database.subtitles().list(media.uri); selectSubtitle(result) }
 }
 fun selectSubtitle(id: Long) {
  cueJob?.cancel(); cues=emptyList(); embeddedText=""; loadedSubtitle=-1
  player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()
  update { copy(selectedSubtitle=id) }
  if(id<0) return
  val subtitle=subtitles.find { it.id==id } ?: return
  val mediaUri=current?.uri
  cueJob=viewModelScope.launch {
   try {
    val parsed=withContext(Dispatchers.IO) { val bytes=getApplication<Application>().contentResolver.openInputStream(Uri.parse(subtitle.uri))?.use { it.readBytesLimited() } ?: error("字幕不存在或权限失效"); SubtitleParser.parse(SubtitleParser.decode(bytes),subtitle.name.substringAfterLast('.')) }
    if(current?.uri==mediaUri && current?.selectedSubtitle==id) { cues=parsed; loadedSubtitle=id }
   } catch(e: Exception) { if(e is CancellationException) throw e; error="字幕导入失败：${e.message}" }
  }
 }
 fun selectEmbedded(group: Tracks.Group, index: Int) { selectSubtitle(-2); player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,index)).build() }
 fun subtitleText(): String { val media=current ?: return ""; if(media.image)return ""; if(media.selectedSubtitle==-2L) return embeddedText; val time=PlaybackPolicy.subtitleTime(position,media.offset); return cues.filter { time>=it.start && time<it.end }.joinToString("\n") { it.text } }
 fun scan() = report {
  withContext(Dispatchers.IO) {
   val context=getApplication<Application>()
   val collections=listOf(Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,false,false),Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,false),Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,false,true))
   for((base,music,image) in collections) {
    val permission=if(android.os.Build.VERSION.SDK_INT>=33) when {image->android.Manifest.permission.READ_MEDIA_IMAGES;music->android.Manifest.permission.READ_MEDIA_AUDIO;else->android.Manifest.permission.READ_MEDIA_VIDEO} else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val granted=androidx.core.content.ContextCompat.checkSelfPermission(context,permission)==android.content.pm.PackageManager.PERMISSION_GRANTED || (!music && android.os.Build.VERSION.SDK_INT>=34 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    if(!granted)continue
    val columns=if(image) arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.WIDTH,MediaStore.MediaColumns.HEIGHT) else arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.DURATION,MediaStore.MediaColumns.SIZE)
    context.contentResolver.query(base,columns,null,null,null)?.use {cursor->
     while(cursor.moveToNext()) {
      val uri=android.content.ContentUris.withAppendedId(base,cursor.getLong(0)).toString()
      if(database.media().get(uri)==null) database.media().save(if(image) MediaRecord(uri,cursor.getString(1),image=true,size=cursor.getLong(2),resolution="${cursor.getInt(3)} × ${cursor.getInt(4)}") else MediaRecord(uri,cursor.getString(1),music,duration=cursor.getLong(2),size=cursor.getLong(3)))
     }
    }
   }
  }
 }
 fun folder(uri: Uri) = report {
  getApplication<Application>().contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
  withContext(Dispatchers.IO) {
   val root=androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(),uri) ?: error("无法读取文件夹")
   suspend fun walk(folder: androidx.documentfile.provider.DocumentFile) { folder.listFiles().forEach { file -> if(file.isDirectory) walk(file) else if(file.type?.startsWith("video/")==true || file.type?.startsWith("audio/")==true || file.type?.startsWith("image/")==true) { if(database.media().get(file.uri.toString())==null) database.media().save(metadata(file.uri,file.name ?: "媒体")) } } }
   walk(root)
  }
 }
 override fun onCleared() { player.release(); database.close() }
}















