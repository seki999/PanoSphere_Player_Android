@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.panosphere.player

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.media3.ui.PlayerView
import androidx.media3.common.*
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity: ComponentActivity() {
 lateinit var controller: PlaybackController; private set
 private lateinit var screen: ScreenAwakeController
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  controller=ViewModelProvider(this)[PlaybackController::class.java]
  screen=ScreenAwakeController(this) { controller.stop() }; controller.awake=screen
  setContent { MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF80CBC4),onPrimary=Color(0xFF062B2A),background=Color(0xFF10151D),surface=Color(0xFF1B2330))) { Surface(Modifier.fillMaxSize()) { App(controller) } } }
 }
 override fun onStart() { super.onStart(); screen.foreground(true); controller.resumeRendering() }
 override fun onResume() {
  super.onResume();screen.foreground(true)
  controller.refreshAwake()
  controller.resumeRendering()
 }
 override fun onPause() {screen.foreground(false);super.onPause()}
 override fun onStop() { screen.foreground(false); super.onStop() }
 override fun onDestroy() { controller.awake=null; screen.release(); super.onDestroy() }
 fun rotate() { requestedOrientation=if(resources.configuration.orientation==2) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
 @Composable private fun App(c: PlaybackController) {
  var page by remember { mutableStateOf("视频") }; var selectedPlaylist by remember {mutableStateOf<String?>(null)}; var query by remember { mutableStateOf("") }; var ascending by remember { mutableStateOf(false) }
  var playerVisible by rememberSaveableCompat { mutableStateOf(c.current!=null) }
  LaunchedEffect(c.current?.uri) { if(c.current!=null) playerVisible=true }
  val library by c.library.collectAsState()
  val mediaPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if(it.isNotEmpty()) { c.import(it); playerVisible=true } }
  val subtitlePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(c::importSubtitle) }
  val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(c::folder) }
  val permissionPicker=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if(result.values.any { it }) c.scan() else c.error="未获得媒体读取权限，仍可使用系统文件选择器打开文件。" }
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
   if(playerVisible && c.current!=null) {
    PlayerScreen(c, { playerVisible=false; c.stop(); requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }, { subtitlePicker.launch(arrayOf("*/*")) })
   } else {
    Text("PanoSphere",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(20.dp,16.dp,20.dp,0.dp))
    Text("本地影音 · 全景随行",color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(20.dp,4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) { listOf("视频","音乐","图片","最近播放","收藏夹","播放列表","文件夹","设置").forEach { name -> FilterChip(selected=page==name,onClick={page=name},label={Text(name)},modifier=Modifier.padding(3.dp)) } }
    Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Button(onClick={mediaPicker.launch(arrayOf("video/*","audio/*","image/*"))}) { Text("打开文件") }
     OutlinedButton(onClick={permissionPicker.launch(if(Build.VERSION.SDK_INT>=34) arrayOf(Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO,Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO,Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))}) { Text("扫描设备") }
    }
    if(page=="文件夹") { Button(onClick={folderPicker.launch(null)},modifier=Modifier.padding(16.dp)) { Text("授权并导入文件夹") }; Text("递归浏览用户授权文件夹的音视频和图片；导入结果显示于对应列表。",modifier=Modifier.padding(16.dp)) }
    if(page=="设置") { Settings() } else {
     OutlinedTextField(query,{query=it},label={Text("搜索文件 / 歌手 / 播放列表")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(16.dp,8.dp))
     TextButton(onClick={ascending=!ascending}) { Text(if(ascending) "排序：名称 ↑" else "排序：最近播放 ↓") }
     if(page=="播放列表") Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp)) { library.map {it.playlist}.distinct().forEach {name->FilterChip(selected=selectedPlaylist==name,onClick={selectedPlaylist=name},label={Text(name)},modifier=Modifier.padding(3.dp))} }
     val filtered=library.filter { (it.name+it.artist+it.playlist).contains(query,true) && when(page) { "视频" -> !it.music && !it.image; "音乐" -> it.music; "图片" -> it.image; "最近播放" -> it.lastPlayed>0; "收藏夹" -> it.favorite; "播放列表" -> selectedPlaylist==null || it.playlist==selectedPlaylist; else -> true } }.let { if(ascending) it.sortedBy { r->r.name.lowercase() } else it }
     if(filtered.isEmpty()) Text("还没有媒体。使用“打开文件”导入本地音视频或图片。",modifier=Modifier.padding(24.dp))
     LazyColumn(Modifier.weight(1f)) { items(filtered,key={it.uri}) { item ->
      Card(Modifier.fillMaxWidth().padding(12.dp,4.dp).clickable { c.open(item,filtered); playerVisible=true }) {
       Column(Modifier.padding(14.dp)) {
        Text((if(item.image) "▧  " else if(item.music) "♫  " else "▶  ")+item.name,style=MaterialTheme.typography.titleMedium)
        Text("${if(item.image) "图片" else formatTime(item.duration)}  ${item.resolution}  ${if(item.size>0) "${item.size/1024/1024} MB" else ""}",style=MaterialTheme.typography.bodySmall)
        if(item.music) Text(item.artist,style=MaterialTheme.typography.bodySmall)
        Text("列表：${item.playlist}",style=MaterialTheme.typography.bodySmall)
        Row { TextButton(onClick={c.favorite(item)}) { Text(if(item.favorite) "★ 已收藏" else "☆ 收藏") }; TextButton(onClick={c.delete(item)}) { Text("移除记录") } }
       }
      }
     } }
    }
   }
  }
  c.error?.let { message -> AlertDialog(onDismissRequest={c.error=null},title={Text("操作提示")},text={Text(message)},confirmButton={TextButton(onClick={c.error=null}) {Text("知道了")}},dismissButton={if(c.current!=null) TextButton(onClick={c.error=null;c.play()}) {Text("重试播放")}}) }
 }
 @Composable private fun Settings() {
  val scope=rememberCoroutineScope(); val preferences by settings.data.collectAsState(initial=emptyPreferences())
  var checked=preferences[booleanPreferencesKey("largeText")] ?: false
  Column(Modifier.padding(20.dp)) {
   Text("离线播放 · 无广告 · 无账号",style=MaterialTheme.typography.titleLarge)
   Row(verticalAlignment=Alignment.CenterVertically) { Text("音乐大字号"); Switch(checked,{ value -> scope.launch { settings.edit { it[booleanPreferencesKey("largeText")]=value } } }) }
   Text("播放时保持屏幕常亮；电源键关屏或进入后台即停止，返回后必须手动播放。\n\n支持等距柱状 360°、180°，以及居中等距圆形 180° 鱼眼。未校准的厂商鱼眼不支持自动识别。\n\nASS/SSA 支持时间与基础文本；不保证复杂特效、字体和排版还原。")
  }
 }
 @Composable private fun PlayerScreen(c: PlaybackController, back: ()->Unit, importSubtitle: ()->Unit) {
  val record=c.current ?: return

  var menu by remember { mutableStateOf("") }; var visible by remember { mutableStateOf(true) }; var locked by remember { mutableStateOf(false) }; var fit by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
  androidx.activity.compose.BackHandler { if(menu.isNotEmpty()) menu="" else back() }
  DisposableEffect(visible,locked) { val bars=androidx.core.view.WindowInsetsControllerCompat(window,window.decorView); bars.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; if(!visible || resources.configuration.orientation==2) bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); onDispose {bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())} }
  val preferences by settings.data.collectAsState(initial=emptyPreferences())
  var playlistName by remember(record.uri) { mutableStateOf(record.playlist) }
  Column(Modifier.fillMaxSize()) {
   if(visible && !locked) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
    TextButton(onClick=back) {Text("‹ 返回")}; Text(record.name,modifier=Modifier.weight(1f),maxLines=1); TextButton(onClick={locked=true;visible=false}) {Text("锁定")}
   }
   BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
    if(record.image && c.imageBitmap==null) {
     CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else if(record.image && c.mode==PlaybackMode.FLAT) {
     AndroidView(factory={context -> android.widget.ImageView(context).apply {scaleType=android.widget.ImageView.ScaleType.FIT_CENTER;setOnClickListener {if(!locked)visible=!visible}}},update={it.setImageBitmap(c.imageBitmap)},onRelease={it.setImageDrawable(null)},modifier=Modifier.fillMaxSize())
    } else if(record.music) {
     Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
      AlbumArt(record.uri)
      Text(c.metadata.title?.toString() ?: record.name,fontSize=if(preferences[booleanPreferencesKey("largeText")]==true) 32.sp else 24.sp)
      Text(c.metadata.artist?.toString() ?: record.artist,color=Color.LightGray)
     }
    } else if(c.mode==PlaybackMode.FLAT) {
     AndroidView(factory={ context -> PlayerView(context).apply { useController=false; subtitleView?.visibility=android.view.View.GONE; player=c.player; setOnClickListener { if(!locked) visible=!visible } } },update={it.resizeMode=fit},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
    } else {
     key(record.uri,c.mode) { AndroidView(factory={context -> PanoramaView(context,c.player,if(record.image)c.imageBitmap else null).also { c.panorama=it; it.mode=c.mode; it.eye=record.eye; it.fov=record.fov; it.gyro=record.gyro; it.onTap={if(!locked) visible=!visible}; it.onFovChanged={fov->c.update {copy(fov=fov)}}; it.active((if(record.image)c.imageViewing else c.playbackRequested) && c.awake?.canPlay()==true) } },update={it.mode=c.mode;it.eye=record.eye;it.gyro=record.gyro;it.fov=record.fov;it.gesturesEnabled=!locked},onRelease={view -> if(c.current?.uri==record.uri)c.update { copy(fov=view.fov) }; view.active(false); if(c.panorama===view)c.panorama=null},modifier=Modifier.fillMaxSize()) }
    }
        if(visible && !locked && !record.music && !record.image) Row(Modifier.align(Alignment.Center).background(Color(0x66000000),androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),verticalAlignment=Alignment.CenterVertically) {
     TextButton(onClick={c.player.seekBack()}) {Text("↶ 10")}
     TextButton(onClick={c.toggle();c.resumeRendering()}) {Text(if(c.playbackRequested) "❚❚" else "▶",fontSize=30.sp)}
     TextButton(onClick={c.player.seekForward()}) {Text("10 ↷")}
    }
    val subtitle=c.subtitleText()
    if(subtitle.isNotBlank()) AndroidView(factory={android.widget.TextView(it).apply { gravity=android.view.Gravity.CENTER; setTypeface(Typeface.DEFAULT,Typeface.BOLD); setPadding(12,4,12,4) }},update={it.text=subtitle;it.textSize=record.fontSize;it.setTextColor(record.foreground.toInt());it.setBackgroundColor(record.background.toInt());it.setShadowLayer(3f,1f,1f,record.outline.toInt())},modifier=Modifier.align(Alignment.TopCenter).fillMaxWidth(0.92f).offset(y=(maxHeight-70.dp-record.subtitleBottom.dp).coerceAtLeast(0.dp)*record.subtitlePosition))
    if(!visible || locked) TextButton(onClick={locked=false;visible=true},modifier=Modifier.align(Alignment.TopEnd)) {Text(if(locked) "解锁" else "显示控制")}
   }
   if(visible && !locked) Column(Modifier.padding(horizontal=12.dp)) {
    if(!record.image) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(formatTime(c.position)); Text(formatTime(c.duration)) }
    Slider(value=if(c.duration>0) c.position.toFloat()/c.duration else 0f,onValueChange={c.player.seekTo((it*c.duration).toLong())}) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
     TextButton(onClick={c.next(-1)}) {Text(if(record.image) "上一张" else "上一首")}; if(!record.image) TextButton(onClick={c.player.seekBack()}) {Text("−10秒")}; Button(onClick={c.toggle(); c.resumeRendering()}) {Text(if(record.image) {if(c.imageViewing) "暂停浏览" else "继续浏览"} else if(c.playbackRequested) "暂停" else "播放")}; if(!record.image) TextButton(onClick={c.player.seekForward()}) {Text("+10秒")}; TextButton(onClick={c.next(1)}) {Text(if(record.image) "下一张" else "下一首")}
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
     TextButton(onClick={menu="模式"}) {Text(if(record.image) "观看模式" else "播放模式")}; if(!record.image) TextButton(onClick={menu="字幕"}) {Text("字幕")}; if(!record.image) TextButton(onClick={menu="播放"}) {Text("播放设置")}; TextButton(onClick={rotate()}) {Text("横/竖屏")}; TextButton(onClick={c.favorite(record)}) {Text(if(record.favorite) "★" else "☆")}
    }
   }
  }
  if(menu.isNotEmpty()) AlertDialog(onDismissRequest={menu=""},title={Text(menu)},text={Column(Modifier.verticalScroll(rememberScrollState())) {
   when(menu) {
    "模式" -> {
     if(!record.image) TextButton(onClick={c.detectProjection()}) {Text("自动识别标准投影元数据")}; Text("请根据原始图片或视频手动选择投影；图片不会按扩展名自动猜测。")
     listOf("普通 2D" to PlaybackMode.FLAT,"180° 等距柱状" to PlaybackMode.EQUIRECT_180,"180° 居中圆形等距鱼眼" to PlaybackMode.FISHEYE_180,"360° 等距柱状" to PlaybackMode.EQUIRECT_360).forEach { (name,mode) -> TextButton(onClick={c.changeMode(mode)}) {Text((if(c.mode==mode) "● " else "○ ")+name)} }
     Text("鱼眼需完整圆形、180° 等距、光心居中；其他镜头需预先校正。")
     Row { listOf("单眼源","SBS左眼","SBS右眼").forEachIndexed { index,name -> TextButton(onClick={c.update {copy(eye=index)}}) {Text((if(record.eye==index) "●" else "")+name)} } }
     Row(verticalAlignment=Alignment.CenterVertically) {Text("陀螺仪"); Switch(record.gyro,{value -> if(value && c.panorama?.hasOrientationSensor==false) c.error="设备没有可用方向传感器，可使用手指拖动。" else c.update {copy(gyro=value)}; c.panorama?.reset()})}
     Text("FOV ${record.fov.toInt()}°"); Slider(record.fov,{value -> c.update {copy(fov=value)}},valueRange=35f..100f)
     TextButton(onClick={c.panorama?.reset()}) {Text("复位视角")}
    }
    "字幕" -> {
     Button(onClick=importSubtitle) {Text("导入字幕")}
     TextButton(onClick={c.selectSubtitle(-1)}) {Text("关闭字幕")}
     c.subtitles.forEach { subtitle -> TextButton(onClick={c.selectSubtitle(subtitle.id)}) {Text((if(record.selectedSubtitle==subtitle.id) "● " else "")+subtitle.name)} }
     c.tracks.groups.filter {it.type==C.TRACK_TYPE_TEXT}.forEach { group -> repeat(group.length) { index -> val format=group.getTrackFormat(index); TextButton(onClick={c.selectEmbedded(group,index)}) {Text("内嵌：${format.language?.let {Locale.forLanguageTag(it).displayLanguage} ?: "未知语言"} ${format.label ?: ""}")}} }
     TextButton(onClick={menu="字幕设置"}) {Text("字幕设置")}
     Text("ASS/SSA 仅基础文本与时序，不保证高级样式、动画、绘图。")
    }
    "字幕设置" -> {
     Text("字号 ${record.fontSize.toInt()} sp"); Slider(record.fontSize,{v->c.update {copy(fontSize=v)}},valueRange=12f..64f)
     var sizeText by remember(record.fontSize) { mutableStateOf(record.fontSize.toInt().toString()) }
     OutlinedTextField(sizeText,{sizeText=it;it.toFloatOrNull()?.takeIf {v->v in 12f..64f}?.let {v->c.update {copy(fontSize=v)}}},label={Text("字号数值 12–64")},singleLine=true)
     Text("字幕垂直位置（上 / 中 / 下）"); Slider(record.subtitlePosition,{v->c.update {copy(subtitlePosition=v)}},valueRange=0f..1f)
     Text("底部间距 ${record.subtitleBottom.toInt()} dp"); Slider(record.subtitleBottom,{v->c.update {copy(subtitleBottom=v)}},valueRange=0f..150f)
     Text("时间偏移 ${record.offset} ms（正数延后）")
     Row {TextButton(onClick={c.update {copy(offset=offset-100)}}) {Text("提前 0.1秒")};TextButton(onClick={c.update {copy(offset=offset+100)}}) {Text("延后 0.1秒")}}
     var offsetText by remember(record.offset) {mutableStateOf(record.offset.toString())}
     OutlinedTextField(offsetText,{offsetText=it;it.toLongOrNull()?.let {v->c.update {copy(offset=v.coerceIn(-3600000,3600000))}}},label={Text("偏移毫秒")},singleLine=true)
     Text("字体颜色"); Row { listOf(0xFFFFFFFF,0xFFFFFF00,0xFF80CBC4).forEach { color-> TextButton(onClick={c.update {copy(foreground=color)}}) {Text("●",color=Color(color))} } }
     Text("描边颜色"); Row {listOf(0xFF000000,0xFFFFFFFF,0xFF0000FF).forEach {color->TextButton(onClick={c.update {copy(outline=color)}}) {Text("●",color=Color(color))}}}
     Text("背景颜色"); Row {listOf(0x00000000L,0x00FFFFFFL,0x000000FFL).forEach {color->TextButton(onClick={c.update {copy(background=(background and 0xFF000000) or color)}}) {Text("●",color=Color(color or 0xFF000000))}}}
     Text("背景透明度"); Slider(((record.background shr 24) and 255).toFloat()/255,{v->c.update {copy(background=(background and 0xFFFFFF) or ((v*255).toLong() shl 24))}})
    }
    "播放" -> {
     Text("倍速 ${c.player.playbackParameters.speed}"); Slider(c.player.playbackParameters.speed,{c.player.setPlaybackSpeed(it)},valueRange=0.5f..2f)
     Text("音量"); Slider(c.player.volume,{c.player.volume=it})
     TextButton(onClick={fit=when(fit) {AspectRatioFrameLayout.RESIZE_MODE_FIT->AspectRatioFrameLayout.RESIZE_MODE_ZOOM;AspectRatioFrameLayout.RESIZE_MODE_ZOOM->AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;else->AspectRatioFrameLayout.RESIZE_MODE_FIT}}) {Text("画面：${when(fit){0->"适应 / 原始比例";4->"填满裁剪";else->"原始比例按宽度"}}")}
     TextButton(onClick={c.repeatMode=if(record.music) (c.repeatMode+1)%3 else if(c.repeatMode==Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE}) {
      Text(if(record.music) "循环：${listOf("关闭","单曲","列表")[c.repeatMode]}" else "循环：${if(c.repeatMode==Player.REPEAT_MODE_ONE) "单视频" else "关闭"}")
     }
     TextButton(onClick={c.player.shuffleModeEnabled=!c.player.shuffleModeEnabled}) {Text("随机：${c.player.shuffleModeEnabled}")}
     OutlinedTextField(playlistName,{playlistName=it},label={Text("所属播放列表")},singleLine=true)
     TextButton(onClick={c.update {copy(playlist=playlistName.ifBlank {"默认列表"})};menu=""}) {Text("保存播放列表")}
    }
   }
  }},confirmButton={TextButton(onClick={menu=""}) {Text("完成")}})
 }
}
@Composable private fun AlbumArt(uri: String) {
 val context=androidx.compose.ui.platform.LocalContext.current
 var art by remember(uri) {mutableStateOf<android.graphics.Bitmap?>(null)}
 LaunchedEffect(uri) {
  art=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
   val retriever=android.media.MediaMetadataRetriever()
   try { retriever.setDataSource(context,android.net.Uri.parse(uri)); retriever.embeddedPicture?.let {android.graphics.BitmapFactory.decodeByteArray(it,0,it.size)} }
   catch(e: Exception) { android.util.Log.i("AlbumArt","No readable embedded artwork: ${e.message}"); null } finally {retriever.release()}
  }
 }
 if(art!=null) AndroidView(factory={android.widget.ImageView(it)},update={it.setImageBitmap(art)},modifier=Modifier.size(160.dp)) else Text("♫",fontSize=96.sp,color=MaterialTheme.colorScheme.primary)
}
@Composable private fun <T> rememberSaveableCompat(calculation: ()->T): T = remember(calculation=calculation)
fun formatTime(ms: Long): String = "%d:%02d".format(ms/60000,ms/1000%60)













