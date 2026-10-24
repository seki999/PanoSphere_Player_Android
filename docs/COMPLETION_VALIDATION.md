# v1.2 视频结束行为变更

日期：2026-10-25。当前工程路径：`C:\Users\seki9\Documents\PanoSphere_Player_Android`。

## 实现

- 普通视频播完：停留当前视频，暂停、解除常亮，播放器进度与 Room 历史位置归零。
- 即使队列还有其他视频，或状态中残留了列表循环，也不会自动播放下一项。
- 180° / 360° 视频仍默认单视频循环；手动关闭循环后，结尾归零并停止。
- 手动上一项 / 下一项保留；视频设置不再提供列表循环。音乐连续播放与音乐列表循环保留。
- 手动暂停、关屏和进入后台仍保存实际断点，不会归零。
- 图片浏览和现有数据结构不变。APK 版本为 1.2 / versionCode 3。

## 修改文件

- `PlaybackController.kt`：播放结束策略、停止后的进度与历史归零、视频循环设置归一化。
- `PlaybackCompletionPolicy.kt`：区分视频与音乐的结束策略。
- `MainActivity.kt`：视频只显示关闭 / 单视频循环，音乐保留原设置。
- `PlaybackCompletionPolicyTest.kt`：结束策略单元测试。
- `VideoCompletionTest.kt`：实际解码到结尾，检查停止、归零、历史、无自动下一项、保留手动切换和普通暂停断点。
- `app/build.gradle.kts`：可覆盖安装的版本号；README：说明更新。

## 验证命令

```powershell
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.panosphere.player.VideoCompletionTest,com.panosphere.player.PanoramaImageTest#panoramicVideosDefaultToSingleItemLoop' --console=plain
```

本记录末尾补充本次实际执行结果；上一版图片与完整仪器测试的支持边界仍以 `IMAGE_VALIDATION.md` 为准，不将历史未完成的验收标记通过。

## 本次实际结果

- `assembleDebug`、`assembleRelease`、`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest` 均通过。
- JVM 测试共 19 项（原有 15 项 + 结束策略 4 项），0 失败。
- Lint：0 errors、15 warnings。Debug APK 已通过 `apksigner verify --verbose` 的 v2 签名验证。
- 新增的 `VideoCompletionTest` 已成功编译，但设备执行未完成：新建 API 35 模拟器 `panosphere_completion_35` 无法完成 Android 启动，`service check activity` 返回 `not found`。实际 instrument 尝试失败于 Android 系统服务，不是已通过的应用测试。失败输出见 `completion-device-attempt.log`。
- 本次未重新验证上一版图片与整个仪器测试套件；原有边界继续保留在 `IMAGE_VALIDATION.md`。实体手机播放到结尾、保存位置和退出 / 重新打开仍需实际验收。

安装包在 `dist/PanoSphere-Player-1.2-debug.apk`（可直接安装）和 `dist/PanoSphere-Player-1.2-release-unsigned.apk`（需自行签名）。大小和 SHA-256 见 `completion-artifacts-v1.2.json`；实际构建日志见 `build-v1.2-completion.log`。
