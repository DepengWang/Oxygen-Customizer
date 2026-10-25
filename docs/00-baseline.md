# Oxygen Customizer 基线记录

日期：2026-10-25（Asia/Shanghai）
项目：OxygenClock / Oxygen Customizer
执行范围：任务书阶段 0；阶段 1 的源码与构建基线部分

## 1. 仓库状态

| 项目 | 已确认事实 |
|---|---|
| 上游仓库 | `https://github.com/DHD2280/Oxygen-Customizer.git` |
| 个人 fork | `https://github.com/DepengWang/Oxygen-Customizer.git` |
| fork 所有者 | `DepengWang` |
| 上游/个人默认分支 | `beta` |
| 本地分支 | `feature/clockopia-lockscreen-aod` |
| origin | `https://github.com/DepengWang/Oxygen-Customizer.git` |
| upstream | `https://github.com/DHD2280/Oxygen-Customizer.git` |
| 基线 commit | `3c287022f0f1acc733caea4be49b622fe09ee1f6` |
| commit 摘要 | `PulseViewHook: removed useless code` |
| commit 时间 | `2026-10-24T17:28:47+02:00` |
| 许可证 | GPLv3，见 `LICENSE.md` |

`origin/beta` 与 `upstream/beta` 在本次准备时解析到同一个 commit。任务书中的
`Oxygen-Customizer.md`、`Clockopia.ttf` 和 `Orbitron-Regular.ttf` 是本地新增的未跟踪文件，
已保留，尚未把字体加入 APK 或源代码。

## 2. 构建基线

源码和构建脚本显示：

- Gradle Wrapper：9.8.0（`gradle/wrapper/gradle-wrapper.properties`）。
- Android Gradle Plugin：9.3.3；Kotlin：2.4.20（`gradle/libs.versions.toml`）。
- `compileSdk = 37`、`targetSdk = 37`、`minSdk = 33`。
- 应用 `applicationId`：`it.dhd.oxygencustomizer`。
- beta 版本：`versionCode = 210`、`versionName = beta-210`。
- Java/Kotlin 编译目标：JVM 21。
- 当前 `java -version`：Eclipse Temurin 21.0.12.1 LTS。
- Gradle 运行记录：Gradle 9.8.0；launcher JVM 25.0.3；daemon 使用兼容的 Java 21。
- Android SDK Build Tools：37.0.0。
- Gradle 用户目录改为项目内 `.gradle-home`，原因是默认用户 Gradle 目录无法创建 wrapper lock 文件。

## 3. 源码结构初步确认

- 主应用模块：`app`。
- OxygenOS/系统类 stub 模块：`oos_launcher`、`oos_systemui_15`、`oos_systemui_16`。
- Xposed 入口：`app/src/main/java/it/dhd/oxygencustomizer/xposed/MainHook.java`，由
  `XPLauncher` 按目标包分发 mod pack。
- SystemUI 目标包：`com.android.systemui`，由
  `Constants.Packages.SYSTEM_UI` 定义。
- Lock screen / AOD Hook 均在 SystemUI mod pack 中注册：
  `xposed/ModPacks.java` 的 `AodClock` 和 `LockscreenClock`。
- 当前 scope：`android`、`com.android.systemui`、`com.android.settings`、
  `com.android.launcher`，见 `app/src/main/res/values/arrays.xml`。
- Xposed 最低版本元数据：93，见 `app/src/main/AndroidManifest.xml`。
- Companion Magisk 模块元数据：
  `app/src/main/assets/Module/OxygenCustomizerCompanion/module.prop`，
  `id=OxygenCustomizerCompanion`；运行时生成/安装的主模块目录常量为
  `/data/adb/modules/OxygenCustomizer`，见 `ModuleConstants.java`。

## 4. 阶段门槛状态

原版源码已成功编译出可安装 debug APK，但尚未在 OnePlus 12/OxygenOS 16 上安装或启用。
Vector/LSPosed 实际加载、scope 生效、SystemUI Hook 日志、锁屏/AOD 设置效果以及安全功能
回归均必须在设备上完成后，才能进入阶段 2 和 Clockopia 实现。

## 5. 暂未执行的高风险动作

- 未安装 APK，未覆盖已有版本，未清除数据。
- 未启用/禁用 Vector 或 Magisk 模块。
- 未重启设备、SystemUI 或手机。
- 未修改系统分区、Doze、AOD 或认证设置。
- 未修改 Clockopia/Orbitron 字体，也未修改上游源代码。
