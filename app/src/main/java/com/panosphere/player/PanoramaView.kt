package com.panosphere.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.*
import android.opengl.*
import android.view.*
import androidx.media3.exoplayer.ExoPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

enum class PlaybackMode { FLAT, EQUIRECT_180, FISHEYE_180, EQUIRECT_360 }
object ProjectionMath {
 fun cameraAngles(yaw: Float, pitch: Float, fov: Float, aspect: Float, mode: PlaybackMode): Pair<Float,Float> {
  if(mode==PlaybackMode.EQUIRECT_360)return yaw to pitch.coerceIn(-89.9f,89.9f)
  val p=pitch.coerceIn(-89.9f+fov/2,89.9f-fov/2)
  val tangent=tan(Math.toRadians(fov.toDouble()/2))
  val pr=Math.toRadians(p.toDouble())
  val maxYaw=Math.toDegrees(atan((cos(pr)-abs(sin(pr))*tangent)/(tangent*aspect))).toFloat().coerceAtLeast(0f)
  return yaw.coerceIn(-maxYaw,maxYaw) to p
 }
 fun clampYaw(yaw: Float, mode: PlaybackMode, horizontalFov: Float): Float = if(mode == PlaybackMode.EQUIRECT_360) yaw else yaw.coerceIn(-max(0f, 90f-horizontalFov/2), max(0f, 90f-horizontalFov/2))
}
/** Fullscreen ray projection into an external OES texture, with separate 180 adapters. */
class PanoramaView(context: Context, private val player: ExoPlayer, private val imageBitmap: android.graphics.Bitmap? = null): GLSurfaceView(context), GLSurfaceView.Renderer, SensorEventListener {
 @Volatile var mode = PlaybackMode.EQUIRECT_360
 @Volatile var eye = 0 // 0=mono, 1=left SBS, 2=right SBS
  @Volatile var gyro = false
  set(value) {
   if(field==value)return
   field=value;baselineYaw=null
   if(value && isActive) sensor?.let {sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)} else sensors.unregisterListener(this)
  }
 @Volatile var fov = 75f
 @Volatile private var yaw = 0f
 @Volatile private var pitch = 0f
 @Volatile private var sensorYaw = 0f; @Volatile private var sensorPitch = 0f
 private var baselineYaw: Float? = null; private var baselinePitch = 0f
 private val sensors = context.getSystemService(SensorManager::class.java)
 private val sensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
 private var texture: SurfaceTexture? = null
 private val pendingFrames = java.util.concurrent.atomic.AtomicInteger()
 @Volatile private var surface: Surface? = null
 @Volatile var hasRenderedFrame = false; private set
 private var program = 0
 private var textureId = 0
 private val textureTarget = if(imageBitmap==null) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
 private val textureMatrix = FloatArray(16)
 private val vertices = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)); position(0) }
 private var aspect = 1f
 private var isActive = false
 val hasOrientationSensor: Boolean get() = sensor != null
 private val scaler = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() { override fun onScale(d: ScaleGestureDetector): Boolean { fov = (fov/d.scaleFactor).coerceIn(35f,100f); return true }; override fun onScaleEnd(d: ScaleGestureDetector) {onFovChanged(fov)} })
 private var oldX = 0f; private var oldY = 0f
 var onTap: () -> Unit = {}
 var onFovChanged: (Float) -> Unit = {} 
 var gesturesEnabled=true
 private var downX = 0f; private var downY = 0f; private var moved = false
 init { preserveEGLContextOnPause=true; setEGLContextClientVersion(2); setRenderer(this); renderMode = RENDERMODE_CONTINUOUSLY }
 fun reset() { yaw = 0f; pitch = 0f; baselineYaw = null; sensorYaw = 0f; sensorPitch = 0f }
 fun active(value: Boolean) { if(value==isActive)return; isActive=value; if(value) { onResume(); if(gyro) sensor?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME) } } else { sensors.unregisterListener(this); onPause() } }
 override fun onTouchEvent(event: MotionEvent): Boolean {
  if(!gesturesEnabled)return true
  scaler.onTouchEvent(event)
  when(event.actionMasked) {
   MotionEvent.ACTION_DOWN -> { oldX=event.x; oldY=event.y; downX=event.x; downY=event.y; moved=false }
   MotionEvent.ACTION_POINTER_DOWN -> moved=true
   MotionEvent.ACTION_MOVE -> { if(abs(event.x-downX)+abs(event.y-downY)>12) moved=true; if(event.pointerCount==1 && !scaler.isInProgress) { yaw -= (event.x-oldX)*0.12f; pitch += (event.y-oldY)*0.12f }; oldX=event.x; oldY=event.y }
   MotionEvent.ACTION_UP -> if(!moved) { performClick(); onTap() }
  }; return true
 }
 override fun performClick(): Boolean { super.performClick(); return true }
 override fun onSensorChanged(event: SensorEvent) {
  if(!gyro) { baselineYaw=null; return }
  val raw=FloatArray(9); val remapped=FloatArray(9); val values=FloatArray(3)
  SensorManager.getRotationMatrixFromVector(raw,event.values)
  val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
  val axes = when(rotation) { Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X; Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X; Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z; else -> SensorManager.AXIS_X to SensorManager.AXIS_Z }
  SensorManager.remapCoordinateSystem(raw,axes.first,axes.second,remapped); SensorManager.getOrientation(remapped,values)
  val y=Math.toDegrees(values[0].toDouble()).toFloat(); val p=Math.toDegrees(values[1].toDouble()).toFloat()
  if(baselineYaw==null) { baselineYaw=y; baselinePitch=p }
  sensorYaw=((y-baselineYaw!!+540)%360)-180; sensorPitch=p-baselinePitch
 }
 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
 override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
  hasRenderedFrame=false
  val ids=IntArray(1); GLES20.glGenTextures(1,ids,0); textureId=ids[0]
  GLES20.glBindTexture(textureTarget,textureId)
  GLES20.glTexParameteri(textureTarget,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
  GLES20.glTexParameteri(textureTarget,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
  GLES20.glTexParameteri(textureTarget,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
  GLES20.glTexParameteri(textureTarget,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
  if(imageBitmap==null) {
   val oldTexture=texture; val oldSurface=surface
   val st=SurfaceTexture(textureId); texture=st; pendingFrames.set(0); st.setOnFrameAvailableListener { pendingFrames.incrementAndGet() }; val output=Surface(st); surface=output
   android.os.Handler(android.os.Looper.getMainLooper()).post {
    oldSurface?.let {player.clearVideoSurface(it);it.release()};oldTexture?.release()
    if(surface===output && isAttachedToWindow) player.setVideoSurface(output)
   }
  } else {
   val maximum=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,maximum,0)
   val limit=maximum[0].coerceAtLeast(1)
   val ratio=minOf(1f,limit.toFloat()/maxOf(imageBitmap.width,imageBitmap.height))
   val upload=if(ratio<1f) android.graphics.Bitmap.createScaledBitmap(imageBitmap,maxOf(1,(imageBitmap.width*ratio).toInt()),maxOf(1,(imageBitmap.height*ratio).toInt()),true) else imageBitmap
   GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,upload,0)
   val error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR)android.util.Log.e("PanoSphereGL","Image texture upload error: $error")
   if(upload!==imageBitmap)upload.recycle()
   Matrix.setIdentityM(textureMatrix,0)
  }
  fun shader(type: Int, source: String): Int { val id=GLES20.glCreateShader(type); GLES20.glShaderSource(id,source); GLES20.glCompileShader(id); val status=IntArray(1); GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0); check(status[0]!=0) { GLES20.glGetShaderInfoLog(id) }; return id }
  val vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 a; varying vec2 p; void main(){p=a;gl_Position=vec4(a,0.,1.);}")
  val fragment=shader(GLES20.GL_FRAGMENT_SHADER,if(imageBitmap==null) FRAGMENT else IMAGE_FRAGMENT)
  program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program)
  GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
 }
 override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { aspect=width.toFloat()/height.coerceAtLeast(1); GLES20.glViewport(0,0,width,height) }
 override fun onDrawFrame(gl: GL10?) {
  if(imageBitmap==null) {
   val st=texture ?: return
   // Drain every queued decoder frame so a slow draw rate never back-pressures MediaCodec into buffering.
   repeat(pendingFrames.getAndSet(0).coerceAtLeast(1)) { st.updateTexImage() }; st.getTransformMatrix(textureMatrix)
  }
  GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); GLES20.glUseProgram(program)
  val horizontal = Math.toDegrees((2*atan(tan(Math.toRadians(fov.toDouble()/2))*aspect))).toFloat()
  val (y,p)=ProjectionMath.cameraAngles(yaw + (if(gyro) sensorYaw else 0f),pitch + (if(gyro) sensorPitch else 0f),fov,aspect,mode)
  GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"camera"),Math.toRadians(y.toDouble()).toFloat(),Math.toRadians(p.toDouble()).toFloat(),tan(Math.toRadians(fov.toDouble()/2)).toFloat(),aspect)
  GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"mode"),if(mode==PlaybackMode.EQUIRECT_360) 0 else if(mode==PlaybackMode.EQUIRECT_180) 1 else 2)
  GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"eye"),eye)
  GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"transform"),1,false,textureMatrix,0)
  GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(textureTarget,textureId)
  GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"video"),0)
  val a=GLES20.glGetAttribLocation(program,"a"); GLES20.glEnableVertexAttribArray(a); GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,vertices); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);hasRenderedFrame=true
 }
 /** SurfaceView creates its buffer during a later window traversal; a still image never schedules one by itself. */
 @Volatile private var hasSurface = false
 private fun requestFramesUntilSurface(remaining: Int) { if(!hasSurface && isAttachedToWindow && remaining>0) { rootView.invalidate(); postOnAnimation { requestFramesUntilSurface(remaining-1) } } }
 override fun onAttachedToWindow() { super.onAttachedToWindow(); requestFramesUntilSurface(120) }
 override fun surfaceCreated(holder: SurfaceHolder) { hasSurface=true; super.surfaceCreated(holder) }
 override fun surfaceDestroyed(holder: SurfaceHolder) { hasSurface=false; super.surfaceDestroyed(holder) }
 override fun onDetachedFromWindow() {
  sensors.unregisterListener(this)
  surface?.let { player.clearVideoSurface(it); it.release() }; surface=null
  queueEvent { texture?.release(); texture=null; GLES20.glDeleteTextures(1,intArrayOf(textureId),0); GLES20.glDeleteProgram(program) }
  super.onDetachedFromWindow()
 }
 companion object {
 val FRAGMENT = """
 #extension GL_OES_EGL_image_external : require
 precision highp float;
 uniform samplerExternalOES video; uniform mat4 transform;
 uniform vec4 camera; uniform int mode; uniform int eye; varying vec2 p;
 const float PI=3.141592653589793;
 void main(){
 vec3 d=normalize(vec3(p.x*camera.z*camera.w,p.y*camera.z,1.));
 float cp=cos(camera.y),sp=sin(camera.y); d=vec3(d.x,cp*d.y+sp*d.z,-sp*d.y+cp*d.z);
 float cy=cos(camera.x),sy=sin(camera.x); d=vec3(cy*d.x+sy*d.z,d.y,-sy*d.x+cy*d.z);
 float lon=atan(d.x,d.z); float lat=asin(clamp(d.y,-1.,1.)); vec2 uv;
 if(mode==0){uv=vec2(lon/(2.*PI)+.5,.5-lat/PI);}
 else if(mode==1){if(d.z<0.){gl_FragColor=vec4(0.,0.,0.,1.);return;} uv=vec2(lon/PI+.5,.5-lat/PI);}
 else {if(d.z<0.){gl_FragColor=vec4(0.,0.,0.,1.);return;} float theta=acos(clamp(d.z,-1.,1.)); float radius=theta/PI; float len=length(d.xy); vec2 radial=len>.00001?d.xy/len:vec2(0.); uv=vec2(.5)+radius*vec2(radial.x,-radial.y);}
 uv.y=1.-uv.y;
 if(eye==1) uv.x*=.5; else if(eye==2) uv.x=.5+uv.x*.5;
 gl_FragColor=texture2D(video,(transform*vec4(uv,0.,1.)).xy);
 }
 """.trimIndent()
 val IMAGE_FRAGMENT = FRAGMENT.replace("#extension GL_OES_EGL_image_external : require", "").replace("samplerExternalOES", "sampler2D").replace("uv.y=1.-uv.y;", "")
 }
}






