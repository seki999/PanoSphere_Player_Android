# PanoSphere Player

离线 Android 本地音视频与 180° / 360° 全景播放器。Kotlin、Compose、单一 Media3 ExoPlayer 实例、OpenGL ES 2.0。默认简体中文；无账号、广告、服务端、在线流媒体、虚拟影院或 VR 分屏。最低 Android 8.0 / API 26。

## 构建与安装

固定版本：JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin / Compose compiler 2.0.20、Compose BOM 2024.12.01、Media3 1.9.3、Room 2.6.1、DataStore 1.1.1。compile / target SDK 35。该组合采用本机已安装、可验证的 SDK；并不声称 SDK 35 是最新 Android SDK。

在 Android Studio 打开项目，安装 Android SDK Platform 35，配置 `ANDROID_HOME`，或在未入库的 `local.properties` 中配置 `sdk.dir`。首次构建需联网下载依赖，应用运行不需要网络，Manifest 没有 INTERNET 权限。

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

macOS / Linux 使用 `./gradlew`。Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

Release：`./gradlew assembleRelease` 生成 `app/build/outputs/apk/release/app-release-unsigned.apk`。使用 Android Studio「Generate Signed Bundle / APK」或本地 `apksigner` 与自己的 keystore 签名。工程不保存签名密钥、密码。未签名 Release APK 不能直接安装；Debug APK 可直接安装。

## 使用

- 首页「打开文件」支持多选视频、音乐，所选文件组成当前播放队列。Media3 在设备能力范围内处理 MP4、MKV、WebM、MOV、MP3、AAC、M4A、FLAC、WAV、OGG 等。AVI 等并非保证支持。
- 「扫描设备」按 Android 版本请求媒体读取权限；拒绝权限后仍可用系统文件选择器。「文件夹」通过 SAF 授权，递归导入音视频。
- 视频、音乐、最近播放、收藏夹支持搜索和名称 / 最近播放排序。每条记录展示时长、大小和已有分辨率；文件失效时有错误提示，可「移除记录」。移除只清理数据库，不删除原文件。
- 点击媒体进入播放器。支持暂停、上 / 下一项、快进快退、进度、0.5–2 倍速、音量、画面适应 / 填满裁剪 / 按宽度、横竖屏、单视频循环（全景默认开启）、音乐单曲 / 列表循环、随机、收藏。播放设置可命名所属列表；搜索列表名后打开其中一项即以筛选结果组成队列。
- 单击画面显示 / 隐藏控制；锁定后只显示解锁入口。全景手势仅由画面 View 处理，进度条与菜单在独立 UI 中，互不旋转。
- 音乐显示名称、歌手和可读取的嵌入封面；无封面使用音符。设置中的大字号保存在 DataStore。音频焦点交给 Media3，耳机拔出自动暂停。

## 全景

「播放模式」可在同一播放器实例上切换 2D、180° 等距柱状、180° 圆形等距鱼眼、360° 等距柱状。单指拖动、双指缩放 FOV 35–100°、视角复位、传感器开关、SBS 左眼 / 右眼单眼观看。传感器使用 game rotation vector，设备不具备时回退 rotation vector。横竖屏根据显示旋转重新映射传感器坐标。

自动识别只接受验证过的 MP4 box 路径中的标准 `equi` 元数据：完整球面或左右各裁去 1/4 的对称 180°，以及 `st3d` 的单眼 / SBS 布局。不会从扩展名或宽高比猜投影。未支持的元数据、Matroska 标签、厂商私有鱼眼均应手动选择；上下双眼布局不支持。

采用全屏射线投影，等价于从球心观察纹理球面 / 半球，绝非 2D 放大。`PanoramaView.kt` 的 fragment shader 实现三个独立映射：

- 360°：`u = longitude / (2π) + 0.5; v = 0.5 - latitude / π`。
- 180° 经纬：`u = longitude / π + 0.5; v = 0.5 - latitude / π`，源覆盖经度 ±90°。
- 圆形鱼眼：`radius = acos(ray.z) / π`，光轴前方的圆心为 `(0.5,0.5)`；180° 边界半径 0.5。SBS 在上述每眼 UV 之后选择左 / 右半幅。

仅声明支持光心居中、半径为每眼图像短边一半的正方形完整等距 180° 鱼眼。非等距镜头、裁剪圆、偏心镜头、厂商标定和镜头畸变不在支持范围，需先转换 / 校正。180° 相机方向受有效覆盖范围限制。

官方 `SphericalGLSurfaceView` 已评估：它有成熟球面 / 传感器基础，但没有本项目要求的统一可控 FOV 与独立 180° 经纬 / 鱼眼适配接口。因此使用统一的 OES 射线渲染器覆盖 180 / 360，避免维护两个不同手势系统。ExoPlayer 始终只有一个；View 销毁只清除自己拥有的 Surface，GL 线程释放纹理与程序。

## 字幕

「字幕 → 导入字幕」通过系统选择器选择 SRT、VTT、ASS、SSA。立即关联当前视频，不重建播放器；系统文件选择器会让应用失去前台，因此按统一后台策略暂停，返回后手动继续。解析失败本身不会中断已在前台播放的视频。支持多个外部字幕切换与关闭，下次打开恢复选择。支持内嵌文本轨道切换与语言显示。

外部字幕支持 UTF-8、BOM、UTF-16；无效 UTF-8 尝试 GB18030。最大 8 MB，损坏、无内容、无效时间戳、权限失效均报告错误，不停止正在播放的视频。SAF 可持久化授权时保留授权；不支持时复制到应用私有目录。文件名、URI、语言字段保存在 Room。

外部字幕支持字号滑块与数值输入、字体 / 描边 / 背景颜色、背景透明度、屏幕垂直位置、底部间距、毫秒偏移和 ±0.1 秒调整。正偏移延后，负偏移提前。统一 TextView 屏幕覆盖层，不进入球面纹理。描边采用文字阴影边缘增强。

ASS / SSA 提供基础文本和时间解析：去除覆盖标签、处理换行；不保证高级排版、特效、字体附件、矢量绘图、卡拉 OK 保真。VTT 定位标签和 SRT HTML 样式不保留。内嵌文本字幕通过独立 TextRenderer 时钟适配实现偏移，不改变音视频时钟；跳转时同步复位字幕时钟。内嵌字幕使用 Media3 文本输出；不支持的内嵌图像字幕无文本输出。

## 屏幕和生命周期

`ScreenAwakeController` 仅在 Activity 前台、设备 interactive、已请求播放（含缓冲）且未被音频焦点抑制时设置 `FLAG_KEEP_SCREEN_ON`。暂停 / 结束 / 后台 / 退出立即清除。不使用 WakeLock，不改系统超时，不阻止电源键。

动态 `ACTION_SCREEN_OFF` 接收器立即调用停止策略：暂停唯一播放器、停止音频、记录位置、清除常亮并暂停传感器 / GL。Activity `onPause` / `onStop` 同样停止，重复调用幂等；同时处理多窗口失焦。异步文件加载和循环切换带取消代数保护，关屏后已经在等待的任务不能在解锁时重新启动播放。这里的「停止」保留 MediaItem 和当前位置以供手动恢复，不销毁整个播放器。解锁 / 回前台不调用 play；只有用户打开媒体或点击播放才播放。音频焦点自动处理不建立后台服务或锁屏播放入口。

系统广播和生命周期均通过主线程串行处理。Android 的事件派发不是硬实时保证，不能对所有 OEM 承诺零毫秒；实体电源键、蓝牙、电话中断仍须按测试清单真机验收。

## 架构与持久化

- `MainActivity.kt`：Compose 导航、文件选择与 UI，播放器控制逻辑在 ViewModel。
- `PlaybackController.kt`：唯一 ExoPlayer、可观察状态、队列、轨道、字幕关联、错误与生命周期停止。
- `ScreenAwakeController.kt`：常亮决策、屏幕广播。
- `PanoramaView.kt`：GL 纹理、射线投影、传感器、触摸与缩放。
- `ProjectionMetadata.kt`：有限且严格的标准投影元数据读取。
- `Database.kt`：Room 媒体 / 字幕实体、DAO、1→2 Migration；不使用 destructive migration。`app/schemas` 保存版本化 schema。
- `Subtitles.kt`：编码、字幕解析、URI 持久化 / 私有副本。

位置每 3 秒以及暂停 / 停止时保存；模式、单眼、FOV、传感器、字幕、样式、收藏和列表随修改保存。DataStore 保存全局大字号设置。卸载会删除私有副本和本地数据库。后续版本必须增加 Room 版本并写 Migration；不要删除数据库或引入破坏性回退。

## 测试与限制

实际执行结果见 `docs/VALIDATION.md`；人工验收步骤见 `docs/DEVICE_TEST_PLAN.md`。`test-media` 素材完全由程序和 FFmpeg 生成，无商业电影或音乐。`node scripts/generate-projection-fixtures.cjs` 生成投影 PPM；详见测试计划中的编码命令。

硬件解码与 Media3 支持共同决定可播放范围；4K / 8K / HDR 不能保证全机型可用。当前 GL 输出 SDR，不提供 HDR 色彩正确性保证；超高分辨率可能丢帧、发热或解码失败。错误会显示错误码与底层原因，允许重试。库扫描为按需刷新，大目录 / 云端 SAF 提供方可能较慢。不是所有提供方都可 seek，复制到本地后再打开。

## 常见问题

- 无法打开：文件已删除、授权失效、云文件不可访问或封装 / 编码不支持；重新导入，必要时转换为 H.264 / AAC MP4。
- 鱼眼扭曲：确认原始镜头确为等距圆形 180°，选择正确单眼；不符合声明格式需离线校正。
- 手势不响应：先解锁控制；只有全景模式旋转视角。
- 解锁无声音：符合设计，点击播放从记录位置恢复。
- ASS 与电脑不同：本版不使用 libass，仅基础字幕功能，不提供高级 ASS 保真。

## 依赖与许可证

AndroidX Compose / Activity / Media3 / Room / DataStore / DocumentFile：Apache License 2.0；Kotlin 与 coroutines：Apache 2.0。间接依赖 Guava：Apache 2.0；Checker Framework 注解：MIT；其他依赖以各发布包 LICENSE / NOTICE 为准。FFmpeg 仅是开发测试工具，不随 APK 分发。官方参考：[Media3 发布记录](https://developer.android.com/jetpack/androidx/releases/media3)、[Surface 类型](https://developer.android.com/media/media3/ui/surface)、[SphericalGLSurfaceView](https://developer.android.com/reference/androidx/media3/exoplayer/video/spherical/SphericalGLSurfaceView)、[保持屏幕常亮](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)。


## 当前验收边界

模拟器上的构建 / 测试通过不等同于所有手机验收完成。实体手机陀螺仪方向与漂移、物理电源键及 OEM 事件顺序、蓝牙耳机、HDR 和 4K / 8K 解码仍需真机验证。首版只声明本文列出的投影与字幕能力；不宣称任意 VR180 镜头、所有高级 ASS / SSA 样式或任意内嵌位图字幕均可正确显示。用户授权不可持久化时，应用私有副本随应用数据保留，卸载 / 清除应用数据会清除副本；从列表移除记录不会删除原文件。

实际代码文件见 [实现索引](docs/IMPLEMENTATION.md)，实际命令、结果与 APK 校验值见 [验证记录](docs/VALIDATION.md)。


## v1.1：180° / 360° 全景图片与视频默认循环

- 「打开文件」支持选择本地静态 JPG/JPEG、PNG、WebP 等 Android 可解码图片；新增「图片」分类。MediaStore 扫描与授权文件夹也纳入图片，沿用历史、收藏和按文件保存的模式 / FOV / 单眼 / 陀螺仪设置。
- 图片先以普通 2D 显示；进入「观看模式」手动选择「360° 等距柱状」「180° 等距柱状」或「180° 居中圆形等距鱼眼」。标准 360° 经纬图片通常为 2:1，但不会只凭比例或扩展名自动判定投影。鱼眼仍限制为前述居中完整等距圆形格式。
- 全景图片支持手指拖动、双指 FOV 缩放、传感器、复位、SBS 左 / 右眼单眼查看、横竖屏、上一张 / 下一张。静态图片不显示无意义的音量、倍速、字幕和时间进度条，不创建第二个音视频播放器，也不把图片伪装成视频。
- 图片在工作线程解码，最长边先按 4096 像素内采样，尊重 EXIF 方向；GL 上传时进一步按设备 `GL_MAX_TEXTURE_SIZE` 缩小。超大图片不会以原始全部分辨率加载；不是分块超高清图片查看器。动图只取静态帧，不提供动画播放。
- 图片浏览时保持屏幕亮；「暂停浏览」、关屏或失去前台会关闭常亮并停止传感器 / 全景渲染；返回后不自动继续，点击「继续浏览」恢复。已显示的静态画面保留。
- 视频进入任一 180° / 360° 模式默认单视频循环；重新打开已保存为全景模式的视频也默认单视频循环。仍可在「播放设置」手动关闭循环；v1.2 起视频不再提供列表循环。离开全景模式时恢复进入前的循环设置（如果用户已经手动改为其他循环模式，则保留手动选择）。图片不自动按定时器轮换。
- 数据库版本 3，通过 2→3 Migration 增加图片标识，保留原视频 / 音乐历史、收藏、字幕与设置；APK 升级为 1.1 / versionCode 2，可以覆盖安装。

本次新增与回归结果见 [图片功能验证记录](docs/IMAGE_VALIDATION.md)。

## v1.2：视频结束后归零并停止，不自动播放下一项

普通视频播放结束后，保留当前选中的视频，把播放器进度和 Room 历史位置重置为 0 秒，保持暂停，解除屏幕常亮。不会因为队列中还有其他视频而自动播放下一项，也不会因以前残留的列表循环状态而自动切换。

180° / 360° 视频仍默认单视频循环，从 0 秒继续播放当前视频；手动关闭循环后，结尾同样归零并停止。手动暂停、关屏或切到后台依然保存当时的位置，不会被此功能归零。

手动上一项 / 下一项保留；视频设置只提供“关闭循环 / 单视频循环”。音乐仍保留原有连续播放、单曲循环与列表循环。图片功能不变。

可覆盖安装 `dist/PanoSphere-Player-1.2-debug.apk`（versionCode 3，数据库版本不变）。本次验证见 [COMPLETION_VALIDATION.md](docs/COMPLETION_VALIDATION.md)。