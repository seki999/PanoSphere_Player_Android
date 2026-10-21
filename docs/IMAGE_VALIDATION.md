# v1.1 验证记录：全景图片与全景视频默认循环

验证日期：2026-10-21，日本时间。环境同 [VALIDATION.md](VALIDATION.md)：Android 15 / API 35 x86_64 模拟器 `commander_review_35`，`-gpu swiftshader`（纯软件 OpenGL ES）。

## 本次修复的问题

1. **第二张及以后的全景图片黑屏（已修复并验证）**
   现象：第一张全景图片正常显示，切到第二张后 GL 画面一直不出现。
   原因：`GLSurfaceView`（`SurfaceView`）要在布局后的下一次窗口遍历中才创建 Surface。视频播放时进度刷新会不断触发重绘，所以没有问题；静态图片界面没有任何东西请求下一帧，窗口就停止重绘，Surface 永远不会创建。
   修复：`PanoramaView.onAttachedToWindow` 在 Surface 创建前，逐帧请求重绘（最多 120 帧，Surface 创建后立即停止）。
   诊断依据：卡住期间手动 `invalidate()` 窗口可立即恢复画面。
2. **慢速 GPU 上全景视频卡在缓冲状态（已修复，部分验证）**
   现象：SwiftShader 上 180°/360° 视频切换模式后，`playbackState=BUFFERING` 长时间不恢复。
   原因：全屏投影着色器在软件渲染下只有约 2–3 fps。每次绘制只取出一个视频帧，`SurfaceTexture` 队列满后，MediaCodec 因背压停止输出。
   修复：每次绘制前取出 `onFrameAvailable` 计数对应的全部待处理帧，只绘制最新一帧，避免绘制速度拖慢解码。实体 GPU 手机原本不易出现此问题，但低端设备同样受益。
3. **测试稳定性**
   - 全景截图断言改为轮询（最多 20 次、每次间隔 250 ms），等待 SurfaceView 缓冲进入合成器后再取像素。
   - `ScreenTimeoutTest` 把系统超时缩短到 5 秒，屏幕可能在 Activity 启动期间熄灭；应用随后按设计停止了播放，这是正确行为。测试现在先唤醒，并等待前台可播放后再导入媒体。

## 实际结果

|检查|结果|
|---|---|
|`assembleDebug` / `assembleRelease`|通过|
|`testDebugUnitTest`|15 项通过，0 失败|
|`PanoramaImageTest`（3 项：四种图片投影截图方向断言、收藏 / FOV / 模式持久化、关屏停止浏览且不自动恢复、Activity 重建；全景视频默认单视频循环并在结尾回到开头、重开恢复循环、切回 2D 恢复原循环设置；EXIF 旋转与损坏图片报错）|**通过**|
|`StorageTest`（含 2→3 Migration 保留视频设置与字幕）|在完整运行中通过|
|`PlaybackIntegrationTest.gesturesAndStereoEyeExtraction`|**未通过**：SBS 左右眼截图差值断言失败。该测试在修复 2 之前运行，修复后尚未重跑|
|`ScreenTimeoutTest`|**未通过**：最后一次运行时 2D MP4 也卡在缓冲，那次运行模拟器负载平均值约为 12、内存接近耗尽。随后重启模拟器，但它没能完成启动（system_server watchdog），所以修复后没能在干净环境中重跑|

`lintDebug` 和完整 `connectedDebugAndroidTest` 在本次修改后**没有**完整通过的记录。上一版完整运行的失败输出：[build-validation-v1.1-attempt.log](build-validation-v1.1-attempt.log)。

## 待办

模拟器恢复后（或使用实体手机），执行：

```powershell
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug connectedDebugAndroidTest --console=plain
```

重点确认 `PlaybackIntegrationTest` 和 `ScreenTimeoutTest`。若在软件 GL 模拟器上仍不稳定，应改用 `-gpu host` 或实体手机，并按 [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md) 验收。

## 安装包

位于 `dist/`：

- `PanoSphere-Player-1.1-debug.apk`：Debug 签名，可直接安装（`adb install -r`）。SHA-256 `86dd314e3fe6d597451f927cc42730079a0b8ff0e1c9e0bc054a0ea00b81ffa5`
- `PanoSphere-Player-1.1-release-unsigned.apk`：未签名，需自行签名后才能安装。SHA-256 `0286b40710694c382f65ee7cb2618bcd93c8aeff8183c374e0bfc890a708a176`
