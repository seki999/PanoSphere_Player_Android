# 实现文件索引

本次从空目录创建完整工程，未删除用户原有代码。

## 工程

`settings.gradle.kts`、根 / app `build.gradle.kts`、`gradle.properties`、Gradle Wrapper（`gradlew`、`gradlew.bat`、`gradle/wrapper/*`）、`.gitignore`。

## 应用代码

|文件（相对于 app/src/main）|职责|
|---|---|
|AndroidManifest.xml|应用入口、按版本媒体读取权限，无联网 / 全盘存储权限|
|java/com/panosphere/player/MainActivity.kt|中文深色 Compose UI、视频 / 音乐 / 最近 / 收藏 / 播放列表 / 文件夹 / 设置，SAF 选择器，前台生命周期|
|java/com/panosphere/player/PlaybackController.kt|单一播放器、实际 / 请求状态、异步取消、队列、重试、进度、音频焦点 / noisy 处理、媒体扫描、字幕关联|
|java/com/panosphere/player/ScreenAwakeController.kt|集中式屏幕常亮、系统关屏广播和状态策略|
|java/com/panosphere/player/PanoramaView.kt|OES 视频纹理、180° 经纬 / 等距鱼眼 / 360° 射线投影、双眼源单眼提取、手势、FOV、传感器、GL 生命周期|
|java/com/panosphere/player/ProjectionMetadata.kt|标准 MP4 equi / st3d 元数据路径验证与有限自动识别|
|java/com/panosphere/player/Subtitles.kt|SRT / VTT / ASS / SSA 文本时序解析、编码、大小限制、URI 持久化与私有副本|
|java/com/panosphere/player/SubtitleRenderersFactory.kt|内嵌文本轨道的独立字幕时钟偏移与跳转复位|
|java/com/panosphere/player/Database.kt|Room 实体 / DAO、Migration、DataStore 入口|
|res/values/styles.xml、res/drawable/ic_launcher.xml|应用主题和矢量图标|

`app/schemas/com.panosphere.player.AppDatabase/2.json` 为导出的数据库 schema。

## 测试和素材

- `app/src/test/java/com/panosphere/player/CoreTest.kt`：13 个 JVM 测试。
- `app/src/androidTest/java/com/panosphere/player/PlaybackIntegrationTest.kt`：解码、字幕、模式、投影截图、左右眼、缩放、锁屏、重建、后台等集成验证。
- `app/src/androidTest/java/com/panosphere/player/ScreenTimeoutTest.kt`：超过系统实际超时的播放常亮与暂停后正常熄屏。
- `app/src/androidTest/java/com/panosphere/player/StorageTest.kt`：Room、迁移与私有副本回退。
- `scripts/generate-projection-fixtures.cjs`、`scripts/generate-test-media.ps1`：原创投影图案和媒体生成脚本。
- `test-media/*`、`app/src/androidTest/assets/*`：合法合成测试媒体和字幕；不打包进正式应用。

## 文档

`README.md` 提供构建、安装、功能和限制；`docs/VALIDATION.md` 记录真实命令和验证结果；`docs/DEVICE_TEST_PLAN.md` 提供实体手机验收方案。`docs/*.png` 为实际运行截图。
