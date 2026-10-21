# 实际验证记录

验证日期：2026-10-21，日本时间。全部结果来自本机执行，没有把未执行的真机项目标记通过。

## 环境

- Windows / PowerShell，Microsoft OpenJDK 17.0.20。
- Android SDK Platform 35；Gradle Wrapper 8.9。
- Android 15 / API 35 / x86_64 模拟器 `commander_review_35`，无连接实体手机。
- 已生成并运行程序自制的 H.264 / AAC MP4、MP3、投影网格和四种字幕素材。

## 最终命令与结果

```powershell
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug connectedDebugAndroidTest --console=plain
```

最终结果：`BUILD SUCCESSFUL in 1m 55s`，`133 actionable tasks: 19 executed, 114 up-to-date`。（v1.0 的完整输出日志在 v1.1 验证时被覆盖；v1.1 那次完整运行的实际输出见 [build-validation-v1.1-attempt.log](build-validation-v1.1-attempt.log)，以及 [IMAGE_VALIDATION.md](IMAGE_VALIDATION.md)。）

|检查|实际结果|
|---|---|
|assembleDebug|通过，生成可直接安装的签名 Debug APK|
|assembleRelease|通过，生成未签名 Release APK|
|testDebugUnitTest|13 项，通过；0 failures / 0 errors / 0 skipped|
|lintDebug|通过；0 errors，14 warnings|
|connectedDebugAndroidTest|6 项，通过；0 failures / 0 errors / 0 skipped；仪器测试约 102.7 秒|
|adb install -r Debug APK|Success|
|adb shell am start|成功启动 MainActivity|
|最终启动 AndroidRuntime 错误检查|未观察到崩溃日志|

JUnit 原始结果：[unit-test-results.xml](unit-test-results.xml)、[device-test-results.xml](device-test-results.xml)。Lint HTML 位于 `app/build/reports/lint-results-debug.html`。警告包括固定依赖有新版本、备份规则建议、KAPT / 自定义 View 工具构造器等，不是零警告构建。SDK 工具还报告 SDK XML 版本差异，以及未安装 NDK strip 工具；依赖自带的原生库原样打包，未阻止 Debug / Release 构建。

曾遇到一次 Windows dex 文件占用导致构建失败，重新执行同一命令通过；没有删除源码或绕过检查。开发时发现的 Android ICU ASS 正则差异、视频纹理上下翻转、GL 暂停导致 Surface 失效、循环回调重入造成状态滞后均已修复并通过回归。

## 已实际覆盖

- 本地 MP4 / MP3 解码与播放，位置跳转、手动暂停 / 恢复。
- 2D / 180° 经纬 / 180° 鱼眼 / 360° 模式切换，以及连续快速切换。
- 180° 两种投影截图方向像素断言，SBS 左右眼提取结果不同，双指 FOV 变化和保存。
- SRT / VTT / ASS / SSA 连续导入与关闭、当前时刻文本输出；内嵌 mov_text 切换与 3000 ms 延迟。
- 屏幕常亮标志、十次真实 Android sleep / wake 电源事件、解锁不自播、后台停止、Activity 重建、横竖屏。
- 异步打开后立即停止，待加载完成也不会自动恢复播放。
- Room 读写、1→2 Migration 保留历史、无法持久化 URI 授权时的私有副本回退。
- 超过系统实际无操作超时：测试临时关闭模拟器充电常亮，将超时设为 5000 ms；模拟器最低有效超时为 10000 ms。四种媒体 / 模式分别无触摸播放 12500 ms 后仍 interactive 且播放；暂停后等待 12500 ms 正常熄屏，唤醒不自播。
- 测试结束已恢复原模拟器设置：`screen_off_timeout=2147483647`、`stay_on_while_plugged_in=1`。应用正式代码没有写系统超时设置。

## APK

Debug（已安装启动）：

`C:\Users\seki9\Documents\PanoSphere_Player\app\build\outputs\apk\debug\app-debug.apk`

大小：13,736,180 bytes。SHA-256：

```text
1E17261FF321C95DDBF6B89B1E719BF75B9122366EB51055176EFCDDEF122ADC
```

Release（必须自行签名后安装）：

`C:\Users\seki9\Documents\PanoSphere_Player\app\build\outputs\apk\release\app-release-unsigned.apk`

大小：9,879,056 bytes。SHA-256：

```text
B26C9B3937C15306687CF3558C0EEE876E316F1BB53FB7C6F5C070AB605FBC05
```

机器可读记录：[artifacts.json](artifacts.json)。

## 未完成的实体手机验收与支持边界

不能把本次结果视为所有第一版真机验收已完成。陀螺仪真实方向 / 漂移、实体电源键及 OEM 时序、蓝牙耳机、真实硬件解码 / HDR / 4K / 8K、各 SAF 文档提供方交互仍待执行 [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md)。仪器测试通过 URI 导入合成文件，没有宣称自动点击测试了所有系统选择器界面。

投影支持范围为标准等距柱状和本文档 / README 声明的居中圆形等距鱼眼；未标定厂商镜头、上下双眼布局和任意投影网格不保证。ASS / SSA 仅基础文本与时序，复杂样式和内嵌位图字幕不保证。自动识别仅支持经过验证的有限标准 MP4 元数据。SDK 使用本机 SDK 35，不宣称它是当日最新 SDK。
