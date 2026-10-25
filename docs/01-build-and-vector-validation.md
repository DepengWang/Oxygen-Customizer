# 原版构建与 Vector 验证记录

日期：2026-10-25（Asia/Shanghai）
基线：`3c287022f0f1acc733caea4be49b622fe09ee1f6`
分支：`feature/clockopia-lockscreen-aod`

## 1. 构建命令与结果

执行的原版构建命令：

```text
set GRADLE_USER_HOME=D:\AndroidStudio\Oxygen-Customizer\.gradle-home
gradlew.bat :app:assembleDebug --stacktrace
```

结果：`BUILD SUCCESSFUL`，耗时约 10 分 10 秒，71 个 actionable tasks 执行。

产物：

```text
app/build/outputs/apk/debug/OxygenCustomizer.apk
```

产物大小：103,373,929 bytes
SHA-256：`cd9bef9e9946660ae5ae2f747860fefc6ae275b7c054e8f7393bbc24f447bfda`

APK 读取结果：

| 字段 | 结果 |
|---|---|
| package | `it.dhd.oxygencustomizer` |
| versionCode | `210` |
| versionName | `beta-210.debug` |
| minSdk | `33` |
| targetSdk / compileSdk | `37` |
| debuggable | 是 |
| 签名 | Android Debug，RSA 2048，APK Signature Scheme v2 |
| 证书 SHA-256 | `70188c99fa909fe8965c97f5bc5c33ecb331ca68dd5627c036ad49fcfcf37db` |

构建过程出现的主要信息是上游既有的 AGP/Gradle 弃用警告、资源字符串格式警告和 Java/Kotlin
deprecated API 警告；没有编译错误，也没有为消除警告而改动源码。

## 2. Xposed 与 Magisk 静态验证

已从源码和构建 APK 确认：

- Manifest 声明 `xposedmodule=true`。
- Manifest 声明 `xposedminversion=93`。
- Manifest 使用 `@array/xposed_scope`；当前 scope 为 `android`、
  `com.android.systemui`、`com.android.settings`、`com.android.launcher`。
- 锁屏和 AOD Hook 均由 `ModPacks` 在 `com.android.systemui` 中注册。
- AOD Hook 类为 `xposed/hooks/systemui/aod/AodClock.java`，针对
  `com.oplus.systemui.aod.aodclock.off.AodClockLayout`，并保留 OOS13 类名回退。
- 锁屏 Hook 类为 `xposed/hooks/systemui/lockscreen/LockscreenClock.java`，针对 OOS15+
  的 keyguard clock 类，并包含 OOS13/OOS14 类名分支。
- 当前源代码已有“用户导入自定义字体”路径：锁屏读取
  `/sdcard/.oxygen_customizer/lockscreen_clock_font.ttf`，AOD 读取
  `/sdcard/.oxygen_customizer/aod_clock_font.ttf`，通过 `Typeface.createFromFile` 后递归应用。
  这只是源码事实，不代表本轮已在设备验证。
- Companion 模块资源中的 `module.prop` 为 `id=OxygenCustomizerCompanion`；主生成模块目录常量为
  `/data/adb/modules/OxygenCustomizer`。

## 3. Vector/真机验证状态

本轮没有执行安装或系统修改。第一次 `adb devices -l` 因 ADB server 无响应而超时；在用户
授权后精确结束卡住的 `adb.exe` 进程并重新启动 ADB server，第二次读取成功：

```text
2dfa4369  device  product:CPH2573IN model:CPH2573 device:OP595DL1
```

已连接设备的只读系统属性：

- Android release：`16`
- SDK：`36`
- OTA：`CPH2573_11.F.57_2570_202608111406`
- Display ID：`CPH2573_16.0.10.501(EX01)`

以下事实仍未知：

- Vector 是否已启用本 APK 模块。
- Vector 实际作用域是否包含 `android`、`com.android.systemui`、`com.android.settings`、
  `com.android.launcher`。
- SystemUI 是否出现 `Oxygen Customizer Version: beta-210.debug` 或相关 Hook 日志。
- 原版锁屏时钟和 AOD 时钟设置是否在设备上实际生效。

## 4. APK 安装记录

在用户明确确认后，使用以下命令完成覆盖安装；没有卸载应用或清除数据：

```text
adb.exe -s 2dfa4369 install -r app/build/outputs/apk/debug/OxygenCustomizer.apk
```

结果：`Success`。

设备回读确认：

- package：`it.dhd.oxygencustomizer`
- versionCode：`210`
- versionName：`beta-210.debug`
- minSdk / targetSdk：`33 / 37`
- APK 路径：`/data/app/~~PPYncfv8FEcyOhGT5-HsGw==/it.dhd.oxygencustomizer-5C0W1_IsmdRk70ERIGg55Q==/base.apk`
- APK 签名方案：v2

安装后没有自动启动应用、没有重启 SystemUI 或手机。`dumpsys package` 显示该包当前为
`stopped=true`、`notLaunched=true`、`enabled=0`；这只是安装后的系统包状态记录，尚未据此
改变应用启用状态。

## 5. 安装前检查项

由于本产物是 debug 签名，不能假设可以覆盖用户现有的 release 签名版本。安装前应由用户确认：

1. 现有 Oxygen Customizer 的包名、签名和版本；
2. Vector 中当前启用的模块与作用域；
3. Magisk 中 `OxygenCustomizer` / `OxygenCustomizerCompanion` 是否已存在及其配置备份；
4. 是否允许安装这个 debug APK；
5. 是否允许按上游流程重启（本轮未执行）。

## 6. 阶段 1 结论

“可构建”门槛已通过；“可安装”仅完成 APK 产物检查，尚未对设备执行安装；“Vector 可加载、
Hook 生效、锁屏/AOD 基础功能实测”尚未通过验证。因此暂不进入 Clockopia 字体修改，也不
提交任何字体功能代码。进入阶段 2 的条件是：用户确认安装测试 APK，并在 Vector/OnePlus 12
上完成模块加载和锁屏、AOD 至少各一项原版设置的可观察验证。
