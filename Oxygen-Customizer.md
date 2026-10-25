# Oxygen Customizer 二次开发计划任务书

> 版本：v1.0  
> 日期：2026-10-25  
> 项目代号：OxygenClock  
> 目标：基于 Oxygen Customizer，为 OnePlus 12 的 OxygenOS 16 锁屏和 AOD 增加 Clockopia和Orbitron 风格字体定制，保留原生功能。

## 1. 项目背景与环境

| 项目 | 当前情况 |
|---|---|
| 设备 | OnePlus 12 |
| 系统 | OxygenOS 16 / Android 16 |
| Root | Magisk，Zygisk 已启用 |
| Hook 框架 | Vector（LSPosed 衍生版本），已安装 |
| 上游项目 | https://github.com/DHD2280/Oxygen-Customizer |
| 开发方式 | Fork → 本地克隆 → Android Studio / Gradle 编译 → 真机测试 |
| 字体方向 | Clockopia 和Orbitron风格：镂空、断笔、几何科技感数字 |
| 本地目录建议 | `D:\AndroidStudio\Oxygen-Customizer` |

**基本原则：优先复用上游项目现有功能和 Hook，不重新实现系统锁屏或 AOD，不更改系统认证链路。**

## 2. 范围与目标

### 2.1 必须完成（P0）

1. Fork 上游项目，克隆到本地并设置 `origin` / `upstream`。
2. 检查上游许可证、分支、版本和构建要求；选择适合 OxygenOS 16 的基线，不预设具体分支。
3. 不修改源代码，首先成功构建可安装的测试 APK，并记录构建环境、版本和产物。
4. 验证 Vector 是否能够加载该项目的 Xposed 模块，以及所需作用域和 Hook 是否实际生效。
5. 分析原项目锁屏时钟和 AOD 时钟的实现路径，区分配置 UI、Xposed Hook、Magisk 模块、主题/资源覆盖。
6. 在锁屏和 AOD 时钟中增加 Clockopia 风格字体选项，保持原生字体为默认值及回退选项。
7. 不影响指纹、人脸、PIN/密码、通知、快捷操作、Doze、AOD 亮度和刷新机制。
8. 提供安装、验证、禁用模块及故障回退文档。

### 2.2 期望完成（P1）

- 锁屏和 AOD 独立选择字体、字号、字重、颜色；仅在技术可行时开放字距、纵向位置等选项。
- 预览字体效果，支持 0–9、冒号和日期所需字符的字形检查。
- 可选 OnePlus 风格红色数字强调，默认 AOD 不启用高亮红色。
- 支持导入用户合法持有的 TTF/OTF 字体，避免把不明授权的字体直接打包分发。
- 将新增配置与现有持久化、备份和恢复机制对接。

### 2.3 暂不实施（P2 / 非目标）

- 不自行开发整套 Keyguard、解锁认证或 AOD 常驻绘制服务。
- 不重写 Vector / LSPosed Hook 框架，不额外开发 Zygisk 注入模块。
- 不全局替换系统字体，不默认修改所有应用的 Typeface。
- 不在第一版 AOD 中加入高频更新的运动、心率或动画数据。
- 不因外观修改而禁用系统安全功能。

## 3. 技术路线及决策门槛

采用 **原项目现有 Xposed Hook 优先** 的策略。Vector 虽已安装，但兼容性必须实测，不能把“安装成功”视为“Hook 已生效”。

1. **原生复用**：优先定位上游锁屏/AOD 时钟配置和字体注入路径；若已支持字体扩展，只新增资源、选项和适配。
2. **定向 Hook**：若需要新增 Hook，仅针对已确认的时钟 View/Typeface/资源路径实施，不拦截全局字体创建。
3. **资源覆盖**：若目标由独立字体或资源驱动，可比较现有 Magisk 资源覆盖机制；不得覆盖通用系统字体。
4. **厂商适配**：若 OxygenOS 16 的类名、资源 ID 或绘制流程与上游不同，建立版本化适配层，禁止凭猜测硬编码。
5. **AOD 低功耗约束**：保持原生 Doze/AOD 生命周期和刷新机制，不能通过持续唤醒屏幕模拟 AOD。

**阶段门槛：** 只有原版在当前设备上能稳定加载且基础功能正常，才进入 Clockopia 修改；若原版不兼容，先修复兼容性并提交独立变更。

## 4. 分阶段实施任务

### 阶段 0：仓库准备与基线记录

- [ ] 确认 Fork 所有者、仓库地址及上游默认分支。
- [ ] 克隆到 `D:\AndroidStudio\Oxygen-Customizer`；配置 `upstream`。
- [ ] 阅读 README、LICENSE、CHANGELOG、构建脚本和模块结构。
- [ ] 记录 Git commit、分支、Gradle/AGP、JDK、Android SDK/NDK 要求。
- [ ] 建立开发分支 `feature/clockopia-lockscreen-aod`，不直接在上游同步分支开发。
- [ ] 建立 `docs/`，存放任务、测试和兼容性记录。

交付：`docs/00-baseline.md`。

### 阶段 1：原版构建、安装与 Vector 验证

- [ ] 用项目自带 Gradle Wrapper 构建，修复环境问题但避免无关依赖升级。
- [ ] 记录原版 `applicationId`、签名、版本号、Xposed 模块声明、Magisk 模块 ID 与安装路径。
- [ ] 先备份现有同类模块设置及 Magisk 配置；核对签名冲突、覆盖安装和数据迁移风险。
- [ ] 仅在用户确认后安装测试 APK；不默认卸载已有版本或清除数据。
- [ ] 在 Vector 中确认模块启用、作用域（以源码要求为准）和目标进程 Hook 日志。
- [ ] 实测原版锁屏时钟与 AOD 时钟的至少一个可观察设置。
- [ ] 记录 SystemUI 崩溃、启动循环、AOD 不显示等问题。

交付：`docs/01-build-and-vector-validation.md`、构建 APK、测试记录。

**通过标准：** APK 可安装；Vector 模块可加载；锁屏和 AOD 的基础定制能力分别得到实际验证。未通过则停止新增字体功能。

### 阶段 2：源码定位与 ROM 资源映射

- [ ] 找到锁屏时钟、AOD 时钟的 UI 配置、状态持久化、Hook 入口、版本判断和资源加载逻辑。
- [ ] 记录目标包、进程、类/方法、资源名称、触发时机与异常处理；只记录经代码或日志证实的信息。
- [ ] 检查锁屏与 AOD 是否共用字体对象或设置。
- [ ] 核实 AOD 在亮屏预览、Doze、充电和通知唤醒状态下是否使用同一绘制路径。
- [ ] 对比上游已知 OxygenOS 16 适配实现，避免重复造轮子。

交付：`docs/02-clock-resource-map.md`、`docs/03-implementation-decision.md`。

### 阶段 3：Clockopia 字体功能

- [ ] 确认拟使用字体的来源、许可和可分发性；如无法确认，仅支持用户自行导入字体。
- [ ] 检查数字 0–9、冒号、空格、日期符号等必需字形；缺失时按字符或整体回退。
- [ ] 在现有锁屏字体配置中增加 `System Default` / `Clockopia`。
- [ ] 在现有 AOD 字体配置中增加独立的 `System Default` / `Clockopia`。
- [ ] 尽量复用项目的设置页面、资源打包、Hook 和配置存储，不创建第二套重复机制。
- [ ] 默认值维持上游行为；字体载入失败时自动回退原生字体。
- [ ] 验证 12/24 小时制、单/双位小时、冒号、不同日期和字体裁切情况。
- [ ] 字号、颜色、字距等扩展功能逐项加开关，并按 AOD 低功耗约束验收。

交付：功能代码、字体说明、测试 APK、`docs/04-font-implementation.md`。

### 阶段 4：外观统一与可选增强

- [ ] 统一锁屏/AOD 的 Clockopia 风格，同时允许分别设置。
- [ ] 锁屏可选 OnePlus 风格红色数字；明确红色规则为用户配置，不擅自假定系统内部规则。
- [ ] AOD 默认灰白、低亮度、无动态动画；避免固定高亮图形长期显示。
- [ ] 检查与现有锁屏主题、通知和指纹图标的布局冲突。
- [ ] 确保不影响用户现有桌面启动器、Lawnchair 或其他模块。

交付：`docs/05-design-spec.md`、截图对比。

### 阶段 5：稳定性、功耗与发布

- [ ] 测试冷启动、重启、锁屏/解锁、指纹、人脸、PIN、来电、通知、充电、低电量、AOD 切换。
- [ ] 检查 SystemUI crash、ANR、Hook 异常、异常唤醒及耗电。
- [ ] 在相同亮度、AOD 模式和测试时长下与原版对比功耗；不以一次电量百分比变化下结论。
- [ ] 测试 Vector 禁用模块、Magisk 模块禁用及字体回退。
- [ ] ROM 指纹或 SystemUI 版本变化时，默认关闭未经验证的定向 Hook。
- [ ] 输出 Release APK、版本说明、兼容矩阵、回退指南。

交付：`docs/06-test-report.md`、`docs/07-rollback-guide.md`、发布产物。

## 5. 关键风险及控制措施

| 风险 | 控制方式 |
|---|---|
| Vector 与上游 Hook 不兼容 | 先验证原版，失败先修复兼容性，不混入字体变更 |
| SystemUI 崩溃/启动循环 | 定向 Hook、异常保护、禁用模块回退；预先验证恢复手段 |
| AOD 与锁屏绘制路径不同 | 分别分析、分别测试，不假设共用资源 |
| 主题更新或 OTA 导致失效 | 记录 ROM 指纹、版本门槛和兼容矩阵 |
| APK 签名或模块 ID 冲突 | 安装前检查签名、包名、模块 ID 和配置迁移，禁止盲目覆盖 |
| 字体授权不明确 | 不直接打包分发，优先用户自行导入 |
| AOD 额外耗电/烧屏 | 保持原生 Doze、亮度及像素位移策略，不加入高频刷新 |
| 原有功能回归 | 原版基线与改版逐项对照，保持改动小且可独立回退 |

## 6. 开发规范

- 优先最小修改，禁止无关 UI 重构和大规模依赖升级。
- 原有功能不得删除；如需改变行为，增加可关闭选项。
- Hook 点必须注明 ROM 适配条件、失败行为和日志。
- 每阶段单独提交 Git commit，重要功能独立 PR。
- 禁止自动刷机、重启、卸载应用、清除数据或修改系统分区；必须获得用户确认。
- 开发记录与任务文档放在项目目录，不依赖用户目录下的临时会话文件。
- 遵守上游开源许可证及字体许可证。

## 7. 建议目录

```text
D:\AndroidStudio\Oxygen-Customizer\
├── [保留上游原有源码结构]
├── docs\
│   ├── 00-baseline.md
│   ├── 01-build-and-vector-validation.md
│   ├── 02-clock-resource-map.md
│   ├── 03-implementation-decision.md
│   ├── 04-font-implementation.md
│   ├── 05-design-spec.md
│   ├── 06-test-report.md
│   └── 07-rollback-guide.md
└── [字体资源放在确认的原项目资源路径中]
```

## 8. 验收标准

- **构建**：从干净检出可复现构建 APK。
- **兼容**：Vector 能加载模块，相关锁屏和 AOD Hook 实际生效。
- **字体**：两种状态均可选 Clockopia，并可分别恢复系统默认字体。
- **安全**：指纹、人脸、PIN/密码、通知、来电及快捷操作正常。
- **功耗**：未引入额外常驻屏幕绘制或高频唤醒；与原版对照无明显异常。
- **稳定**：无新增持续 SystemUI 崩溃、重启循环或 AOD 黑屏。
- **回退**：用户可禁用模块并恢复原生显示，无须清空手机数据。
- **维护**：保留 upstream 同步能力，改动范围可审查。

## 9. 给 Codex 的启动指令

请以本任务书为唯一实施范围，先执行 **阶段 0 和阶段 1**。首先确认本地项目是否存在、Fork 地址及 upstream 状态；分析源码和构建环境，编译原版 APK。检查 Vector（LSPosed 衍生版本）兼容性、模块作用域、签名与 Magisk 模块 ID。未经用户确认不得安装、重启、卸载、清除数据或修改系统设置。完成后提交 `docs/00-baseline.md` 与 `docs/01-build-and-vector-validation.md`，明确列出已经验证的事实、尚未验证的假设、阻塞项及进入阶段 2 的条件。不要直接开始修改 Clockopia 字体。

---

## 10. 当前开发进度（2026-10-25，交接给 Claude Code）

### 10.1 用户当前需求

- 在模组下方提供独立的“全局字体”菜单。
- 菜单固定提供三个选项：`系统字体`、`Clockopia`、`Orbitron`。
- 作用域覆盖原生锁屏和 AOD 界面的时钟及附属文字，不要求先启用自定义锁屏/AOD。
- 不改变用户已有锁屏/AOD 布局、颜色、位置和其他设置。
- 手机在线时，成功构建后自动安装 APK；不得整机重启，SystemUI 重启可用于重新加载 Hook。

### 10.2 已确认的环境和验证事实

- 工作目录：`D:\AndroidStudio\Oxygen-Customizer`。
- 设备：`2dfa4369`，型号 `CPH2573`，OxygenOS 16 / Android 16。
- Vector/Hook 环境已启用，模块可以加载；APK 已安装且可打开。
- 已安装包：`it.dhd.oxygencustomizer`，已核对 `versionCode=210`、`versionName=beta-210.debug`。
- 物理设备 ADB 状态曾确认：`2dfa4369 device`。
- 使用的构建命令：

  ```powershell
  cmd.exe /d /c "set GRADLE_USER_HOME=D:\AndroidStudio\Oxygen-Customizer\.gradle-home&& gradlew.bat :app:assembleDebug --stacktrace"
  ```

- 目前没有提交、推送或修改远程仓库；工作区包含未提交修改和若干调试 APK/反编译/截图文件，接手时不要盲目清理。

### 10.3 已完成的源码修改

- `app/src/main/res/xml/mods.xml` 已加入全局字体菜单：
  - `System Default`
  - `Clockopia`
  - `Orbitron`
- 已加入字体资源：
  - `app/src/main/assets/Fonts/clockopia.ttf`
  - `app/src/main/assets/Fonts/orbitron_regular.ttf`
- `Constants.Preferences.General.GLOBAL_FONT_STYLE` 已作为统一配置键。
- 已移除/停用原先要求分别进入自定义锁屏/AOD 后才能设置字体的入口逻辑。
- `ViewHelper.applyFontToView()` 已同时设置 `TextView` 的 Typeface、Paint Typeface，并递归处理 Paint 字段；该路径已实际证明能改变锁屏字体及 AOD 底部电量字体。
- `AodClock.java` 已增加 OOS 16 原生时钟类加载和多处 Hook，包括：
  - `com.oplus.keyguard.clock.base.ui.view.ClockTimeView`
  - `ClockContainer`、`ClockViewRoot`
  - `CustomizedTextView`
  - `ThemeClockHelper`
  - OOS 16 原生 `keyguard_style_clock` 视图树
  - PersonalityClocks 的 Big Clock 类

### 10.4 已实际验证成功的功能

- 锁屏大时钟已能显示 Orbitron 几何字形。
- 锁屏附属文字（例如 Sunday、Charging 88%）已能跟随 Orbitron。
- AOD 底部电量文字曾成功跟随 Orbitron。
- AOD 上方的解锁次数曾成功跟随 Orbitron。
- 这些成功结果对应的是递归字体应用路径；后续修改不得破坏该路径。

### 10.5 已确认的 AOD 中间时钟问题

通过 SystemUI/Keyguard APK 反编译和 UI dump 已确认：

- AOD 中间时间的可访问节点仍是 `com.oplus.keyguard.clock.base` 下的 TextView，当前设备 UI dump 中包括：
  - `clock_time_hour_second_txt`
  - `clock_time_symbol_text`
  - `clock_time_minutes_txt`
- `ClockTimeView.render()` 在 `ThemeClockHelper.isClockImg()` 为真时调用 `setTextImg()` / `setTextSymbol()`，这些方法会设置数字图片/compound Drawable。
- 因此，普通 TextView 的 `getTypeface()` 已经显示为 Orbitron，并不代表屏幕上的黄色 AOD 数字实际使用了该 Typeface；屏幕仍可能显示原生图片字形。
- 相关 SystemUI 资源已找到：
  - `array/aod_global_off_horizontal_drawable`
  - `array/aod_global_off_vertical_drawable`
  - `drawable/aod_clock_drawable_horizontal_0..9`
  - `drawable/aod_clock_drawable_vertical_0..9`
- 代表性截图：`oxygen-aod-hdrfix.png`。截图中 AOD 中间 `1:58` 仍是原生细字体，而底部 `89%` 已是 Orbitron。

### 10.6 最近一次失败尝试及当前工作区状态

为替换图片字形，最近尝试在原生中央时钟 TextView 同一布局位置加入普通 TextView 覆盖层，并将原生数字文字设为透明。该方案的问题：

- 覆盖层最初覆盖范围过大，连日期/天气等附属文字路径也被触碰。
- 安装并重启 SystemUI 后出现 AOD 黑屏/高频重绘现象；日志中出现 SystemUI ANR 记录。
- 用户明确反馈：该次修改使此前已经成功的“解锁次数”和“电量”字体恢复/失效。
- 随后已将覆盖层目标收窄到中央时钟小时、冒号、分钟，并删除了一个会在每次 `dispatchDraw` 后反复 `post/invalidate` 的重绘循环；该收窄版只完成了构建过程，用户要求停止时尚未重新安装和验证，不能视为可用版本。
- 当前已安装在手机上的版本仍不能作为最终修复版本；不要继续在手机上测试覆盖层，先回退或隔离这部分代码。

### 10.7 接手建议（按优先级）

1. **先恢复已验证功能**：回退 `AodClock.java` 中最近新增的 `mNativeAodFontOverlays`、`mNativeAodOriginalColors`、`installNativeAodFontOverlay()`、`removeNativeAodFontOverlay()` 及其调用，重新确认锁屏、AOD 解锁次数、AOD 底部电量仍为 Orbitron。
2. **不要再通过每帧覆盖层改布局**：原生 AOD 使用低功耗/特殊绘制路径，改变子树会引入重绘循环和 ANR 风险。
3. 优先继续追踪 `ThemeClockHelper.setTextImg()` 的图片来源和调用方：
   - 最小方案应在 `setTextImg()` 入口将数字图片路径替换为真正的字体绘制，且只针对 `ClockTimeView` 的四个时间子视图。
   - 若必须禁用图片路径，应确认 `isClockImg()` Hook 在实际使用的 ClassLoader 中命中，并在一次 render 内清除 compound Drawable 后设置文本；不要在 `dispatchDraw` 中反复改变布局。
4. 任何新 Hook 必须先加一次性日志证明实际命中，再构建、安装、重启 SystemUI，并按顺序验证：锁屏时钟 → AOD 中间时间 → AOD 解锁次数 → AOD 底部电量 → SystemUI 是否稳定。
5. 只有在完整验证没有黑屏、ANR 和功能回退后，才能把任务书阶段 3/4 标记完成。

### 10.8 当前未完成项

- [x] AOD 中央黄色小时/冒号/分钟稳定替换为 Clockopia/Orbitron（2026-10-25，见 10.9）。
- [x] 确认日期、解锁次数、电量等附属文字在修复后仍保持已验证字体（Orbitron 实测）。
- [ ] 验证切回“系统字体”能完整恢复原生显示。
- [ ] 完成 SystemUI 稳定性、Doze/AOD 功耗和整机功能回归测试（SystemUI 启动崩溃已修复，见 10.9.1；长时间功耗未测）。
- [ ] 更新 `docs/02-clock-resource-map.md`、`docs/03-implementation-decision.md`、`docs/04-font-implementation.md` 和测试/回退文档（暂以第 11 节为准）。
- [ ] 清理临时调试产物前先确认没有被接手者用于证据比对。

> 最终完成状态、实现位置、验证结果与已知问题统一见 **第 11 节**。

### 10.9 AOD 中央时钟修复记录（2026-10-25）

**纠正 10.5 的结论**：UI dump 中的 `keyguard_style_clock` / `clock_time_*` 位于 `[167,535][984,916]`，是**锁屏**大时钟，并非 AOD 中央时间。AOD 画面属于 `aod_off_layout`（`AodClockLayout.mAodViewFromApk`），由 `/product/app/VariUIEngine/VariUIEngine.apk` 的 egview 控件绘制，uiautomator 看不到其子节点。当前 AOD 样式的视图树：

```text
com.oplus.aodimpl.AodRootLayout
  AodGroupLayout > CustomViewGroup > com.oplus.egview.widget.insight.OpParsonsClock
      OpParsonsUnlockLabel (解锁次数) / OpParsonsBar / LinearLayout > TextView(时间), TextView(日期)
  AodGroupLayout > AodGroupLayout > AodUvTextView / BatteryView / NotificationView
```

**根因**：`OpParsonsClock.applyLayoutParams()` 等方法会对时间/日期 TextView 重新 `setTypeface(createFromAsset("red_font_text_45lt.ttf"))`，覆盖递归字体路径。

**实现（`AodClock.java`）**：
- `ensureAodEngineHooks()`：从 `mAodViewFromApk` 中找到 egview 控件，用其**实际 ClassLoader**（VariUIEngine）挂 hook；`OpParsonsClock` 的 `applyLayoutParams`/`onTimeChanged`/`updateResource`/`onAttachedToWindow` 之后重新应用全局字体；egview `AttributeTextView`/`AodTextView`/`TimeView` 在绘制前与 `setTypeface*`/`setTextWidget` 后只改 `mTextPaint` 字体（不触发布局）。
- `loadClockTypeface()` 按样式缓存 Typeface（此前每次 `createFromAsset` 导致 `setTypeface` 永远“不相等”→ 重复布局/绘制）。
- `applyFontToAodView()` 增加重入保护；`ViewHelper.applyFontToView()` 字体相同时跳过 `setTypeface`（`setTypeface` 被 hook，此前会无限递归，曾出现在 ANR 堆栈中）。
- 删除覆盖层方案（`installNativeAodFontOverlay` 等）。
- 锁屏 `ClockTimeView`：`ThemeClockHelper` 改为在实际 ClassLoader（KeyguardClockBase）上 hook，且仅在非系统字体时让 `isClockImg()` 返回 false。

**实测（Orbitron，SystemUI 仅重启进程）**：锁屏时钟/星期/日期/天气 ✔；AOD 中央时间（保留原生黄色）✔、日期 ✔、解锁次数 ✔、底部电量 ✔；跨分钟刷新后保持 ✔；无新 ANR/崩溃，SystemUI PID 稳定；Doze 中 20 s 内 SystemUI 绘制帧数为 0（无重绘循环）。

**未验证**：切回“系统字体”的恢复效果；Clockopia 实机效果；其它 AOD 样式（非 Insight 时钟）；12 小时制；长时间功耗对比。

### 10.9.1 SystemUI 启动崩溃修复（2026-10-25）

- 现象：SystemUI 启动后 3–7 s 反复 native 崩溃（`GC tried to mark invalid reference 0x401f5030`，HeapTaskDaemon，`DexCache::VisitNativeRoots`），tombstone 显示自 12:16 起即存在（早于本次接手），重启时约 4/5 概率崩溃数次后才稳定，有触发 Rescue Party 的风险。注意：`logcat -c` 会清掉 crash 缓冲区，排查时应查 `/data/tombstones/`。
- 原因：`AodClock` 用 `createPackageContext(..., CONTEXT_INCLUDE_CODE)` 为 `com.oplus.keyguard.clock.base` / `com.oplus.keyguard.personality.clocks` 创建的类加载器只存在局部变量中（ActivityThread 仅弱引用缓存），其中的类被 hook 后随加载器卸载，GC 扫描到已释放的 DexCache。
- 修复：静态列表 `mPackageClassLoaders` 强引用这两个类加载器。修复后连续 9 次重启 0 崩溃（修复前 5 次中 4 次崩溃）；安装新 APK 后的首次启动仍可能崩溃 1–2 次后恢复（疑为 Vector 刷新模块的过渡期）。

### 10.10 锁屏通知与充电动画（2026-10-25）

- `LockscreenNotificationFont`（新 mod）：`StatusBarState == KEYGUARD` 时对 `ExpandableNotificationRow` 内全部 TextView 应用全局字体（英文/数字用所选字体，中文自动回落系统字体），离开锁屏时按记录恢复原 Typeface。实测锁屏通知条 Orbitron ✔；“解锁后通知栏恢复系统字体”需人工解锁验证，未确认。锁屏媒体卡片不在范围内。
- `ChargingAnimation`（新 mod）：
  - 字体：`ChargeLevelAndLogoView` / `FrameChargeLevelAndLogoView` 在 inflate、电量更新、字重变化后重设字体；`GradientColorTextView.onDraw` 前兜底。VOOC 跳动小数写入同一 `chargeLevelView`（`updateBatteryLevelView(III)`），已覆盖（代码确认，未实测快充）。实测普通充电“92 %” Orbitron ✔。
  - 时长：原生 `OplusChargeAnimImpl.mDelayCancelAnimRunnable` 14 s 后关闭。拦截 `Handler.postDelayed` 中该 Runnable（身份比较），按偏好 `charging_anim_duration` 改延时或不投递（一直显示）。拔电、点击、熄屏仍会关闭。实测 5 分钟设置下 25 s 时仍显示 ✔。
  - 菜单：模组页“全局字体”下新增“充电动画显示时长”（默认 14 秒/30 秒/1/2/5 分钟/一直显示）。
  - 小数跳动持续：原生 `BatteryLevelToDecimalHelper`（单例，每 1 s 读 `OplusBatteryManager.getDecimalData()` 起止值并用 1 s 线性动画推进）只在 `updateChargeAnim()`（插电）时启动，且只推给一个 `mCallback` 视图。停止点：每个动画周期结束的淡出（`startLevelAndLogoFadeoutAnimation`）、内核无小数数据（起/止为 0 或止为 100）、电量 100。淡入和 `removeView()` 重建都不会重启/改绑 → 周期重来后数字冻结。修复：`updateChargeAnim`/淡入后确保循环运行并把 `mCallback` 指向当前可见视图；循环被停且视图仍在屏上时每 3 s 复查（淡出中等待，隐藏/移除/熄屏即停止）。小数只取内核真实值，内核不提供时显示原生整数。USB 普通充电下验证：循环按预期启动/重试，熄屏后重试停止，无崩溃。
  - 内核限制（实测 dmesg）：`oplus_chg_v2` 的 `/proc/ui_soc_decimal` 只在每次超充会话（2VBUS）开始时 `ui_soc_decimal_init`，`calculate_decimal_time` 到 17 即 `deinit`，之后恒为 "0, 0"；写该节点只设置 `boot_completed`，无法重新开启。唯一重新触发方式是中断/恢复充电（不采用）。
  - 接续方案：数据通路 hook（helper 私有 `getDecimalData`、`OplusBatteryManager.getDecimalData`）会被 ART 编译/内联绕过，不可靠。改为：hook 视图 `updateBatteryLevelView(III)` 记录内核期显示值测速；`stopCalculateBatteryRate` 时由模块直接调用视图 `updatePowerFormat(1)` + `updateBatteryLevelView(...)`。（此处记录的是中间版本的步进算法，**最终算法见 11.3.4**。）

---

## 11. 已完成工作总表（冻结基线，2026-10-25）

> 本节是截至 2026-10-25 的最终完成状态，作为后续修 bug / 优化的基准，**暂不改动**。前面 10.x 是过程记录，若与本节冲突，以本节为准。
> 代码基线：分支 `feature/clockopia-lockscreen-aod`，提交 `335d534`（已推送到 `origin` = `DepengWang/Oxygen-Customizer`）。个人修改，**不向上游提交 PR**；上游更新时 `git fetch upstream` 后合并进本分支。

### 11.1 功能清单与设置项

| 功能 | 入口（模组页底部） | 偏好 key / 取值 | 默认 |
|---|---|---|---|
| 全局字体 | 全局字体 | `global_font_style`：`system` / `clockopia` / `orbitron` | `system` |
| 充电动画显示时长 | 充电动画显示时长（全局字体下方） | `charging_anim_duration`（ms 字符串）：`0`=默认 14 秒、`30000`、`60000`、`120000`、`300000`、`-1`=一直显示 | `0` |

全局字体作用范围（不需要先启用自定义锁屏/AOD，不改变原生布局、颜色、位置）：

1. 锁屏：原生大时钟、星期、日期、天气、“Charging xx%”等附属文字。
2. AOD（Insight 时钟样式）：中央时间（保留原生黄色）、日期、解锁次数、底部电量。
3. 锁屏通知条：应用名、标题、时间、正文（仅锁屏状态；英文/数字用所选字体，中文自动回落系统字体）。
4. 充电动画：电量数字（含 SuperVOOC 跳动小数）与百分号。

### 11.2 涉及文件

| 文件 | 内容 |
|---|---|
| `app/src/main/res/xml/mods.xml` | 全局字体、充电动画显示时长两个菜单 |
| `app/src/main/res/values/arrays.xml` | `clock_font_style_*`、`charging_anim_duration_*` 数组 |
| `app/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml` | 菜单文案（中英） |
| `app/src/main/res/xml/aod_clock_prefs.xml`、`lockscreen_clock.xml` | 移除/停用原先分别设置字体的入口 |
| `app/src/main/assets/Fonts/clockopia.ttf`、`orbitron_regular.ttf` | 内置字体 |
| `utils/Constants.java` | `GLOBAL_FONT_STYLE`、`CHARGING_ANIM_DURATION` |
| `xposed/ModPacks.java` | 注册 `LockscreenNotificationFont`、`ChargingAnimation` |
| `xposed/utils/ViewHelper.java` | `applyFontToView()`：TextView + Paint 字段递归设字体，字体相同则跳过 |
| `xposed/hooks/systemui/lockscreen/LockscreenClock.java` | 锁屏原生时钟应用全局字体 |
| `xposed/hooks/systemui/aod/AodClock.java` | AOD 与锁屏原生时钟字体（见 11.3.1–11.3.2） |
| `xposed/hooks/systemui/lockscreen/LockscreenNotificationFont.java` | 锁屏通知字体（新） |
| `xposed/hooks/systemui/lockscreen/ChargingAnimation.java` | 充电动画字体、时长、小数持续跳动（新） |

### 11.3 实现要点

#### 11.3.1 AOD（`AodClock.java`）

- OOS 16 的 AOD 画面是 `aod_off_layout`（`AodClockLayout.mAodViewFromApk`），由 `/product/app/VariUIEngine/VariUIEngine.apk` 的 egview 控件绘制；**不是** `keyguard_style_clock`（那是锁屏时钟）。Insight 样式视图树见 10.9。
- `ensureAodEngineHooks()`：在 `initForAodApk` / `performTimeUpdate` 后，从视图树找到 egview 控件，用其**实际 ClassLoader** 挂 hook：
  - `OpParsonsClock` 的 `applyLayoutParams` / `onTimeChanged` / `updateResource` / `onAttachedToWindow` 之后重新应用全局字体（原生会重设 `red_font_text_45lt.ttf`）。
  - `AttributeTextView` / `AodTextView` / `TimeView`：绘制前、`setTypeface*` / `setTextWidget` 后只改 `mTextPaint` 的字体，不触发布局。
- `loadClockTypeface()` 按样式缓存 Typeface；`applyFontToAodView()` 有重入保护（防止 `setTypeface` hook 递归，曾导致 ANR）。
- 已删除旧的覆盖层方案。

#### 11.3.2 锁屏原生时钟与 SystemUI 启动崩溃修复（`AodClock.java`）

- `ThemeClockHelper`（KeyguardClockBase）在**实际 ClassLoader** 上 hook；仅在非系统字体时让 `isClockImg()` 返回 false，使 `ClockTimeView.render()` 走文字路径；字体切换后对每个 `ClockTimeView` 只重渲染一次（`post` 中执行）。
- `createPackageContext(..., CONTEXT_INCLUDE_CODE)` 得到的 KeyguardClockBase / PersonalityClocks 类加载器由静态列表 `mPackageClassLoaders` 强引用，防止被卸载导致 GC 崩溃（详见 10.9.1）。

#### 11.3.3 锁屏通知（`LockscreenNotificationFont.java`）

- 通过 `ControllersProvider.registerStatusBarStateChangedCallback` 判断是否处于锁屏（`StatusBarState == 1`）。
- hook `ExpandableNotificationRow.onLayout`（锁屏时对行内全部 TextView 设字体）和 `onAttachedToWindow`（登记行）。
- 用 `WeakHashMap` 记录每个 TextView 的原字体，离开锁屏或切回系统字体时恢复。

#### 11.3.4 充电动画（`ChargingAnimation.java`）

1. **字体**：`ChargeLevelAndLogoView` / `FrameChargeLevelAndLogoView` 在 `onFinishInflate`、`onConfigurationChanged`、`setLevelVariationSettings`、`setOplusFontVariationSettings`、`setTypeFace`、`updateBatteryLevelView`、`updateChargeAnim`、`startLevelAndLogoFadeinAnimation` 之后重设字体；`GradientColorTextView.onDraw` 前字体不同才设置。
2. **显示时长**：原生 `OplusChargeAnimImpl.mDelayCancelAnimRunnable` 在 14 s 后关闭动画。全局 hook `Handler.postDelayed`，仅当 Runnable 与该对象**身份相同**时修改延时；“一直显示”则不投递。拔电、点击屏幕、熄屏仍按原生关闭。
3. **周期与数字冻结的原因**：
   - 闪光动画周期约 21 s（VFX 引擎内部 `onFlashAnimationFinishedCallBack`），每周期数字区域重新淡入。
   - 原生小数循环 `BatteryLevelToDecimalHelper` 只在插电时（`updateChargeAnim`）启动，淡出/内核无数据时停止，且只推给一个回调视图。
   - **内核限制**：`/proc/ui_soc_decimal`（`oplus_chg_v2`）每次超充会话只提供约 17 s 小数，之后恒为 `0, 0`，用户层无法重新开启（见 10.10）。
4. **循环维护**：`updateChargeAnim` / 淡入后（延时 300 ms）确保循环运行、回调指向当前可见视图；循环被停且视图仍在屏上时每 3 s 复查（淡出中等待，隐藏/移除/熄屏停止）。
5. **17 s 后的小数接续**（模块直接驱动视图，不依赖会被 ART 内联的数据通路 hook）：
   - 触发：`stopCalculateBatteryRate` 被调用、已有本次会话的测速、视图正在显示。
   - 每步只走 **0.01**；状态栏真实整数电量变化（`BATTERY_PROPERTY_CAPACITY`）时立即从新整数的 **.01** 开始；走到 .99 而真实整数未变时停在 .99，不越过状态栏。
   - 步进间隔 = `每 1% 所需电荷 ÷ 实时充入速度 ÷ 100 × 放慢系数`，限制在 0.2–10 s：
     - 实时充入速度：`BATTERY_PROPERTY_CHARGE_COUNTER`（µAh）在最近 10–60 s 且增量 ≥ 3 mAh 的窗口内计算；内核 17 s 期间就开始采样。
     - 每 1% 所需电荷：初值 `累计电荷 ÷ 当前电量%`；每次真实 +1% 时按实测值更新（新 70% / 旧 30%）。
     - 放慢系数：测到第一次完整真实 1% 前为 1.4，之后为 1.15；小数超过 .80 后再逐步放慢，.99 时为 4 倍（缓出，避免卡在 .99）。
     - 兜底（电荷数据不足时）：用“每 1% 秒数”（初值来自内核期测速，按真实 1% 耗时 70/30 更新）并按充电电流比例（0.5–2 倍）修正。
   - 接续期间把 helper 的 `mCurrentStatus` 置 1，防止原生整数路径覆盖小数；原生循环不再重启。
   - 停止：点击屏幕、熄屏、视图隐藏/移除、电量 ≥ 100；拔电（`ACTION_POWER_DISCONNECTED`）清空本次会话的全部测速数据。

### 11.4 验证结果（OnePlus 12 / OxygenOS 16，Orbitron）

| 项目 | 结果 |
|---|---|
| 锁屏时钟、星期、日期、天气、充电文字 | ✔ |
| AOD 中央时间（保留黄色）、日期、解锁次数、底部电量；跨分钟刷新 | ✔ |
| AOD 息屏 20 s 内 SystemUI 绘制帧数 | 0（无重绘循环）✔ |
| 锁屏通知条字体 | ✔ |
| 充电动画数字字体（普通充电） | ✔ |
| 充电动画显示时长（5 分钟设置，25 s 时仍显示） | ✔ |
| SuperVOOC 17 s 后小数继续跳动、跨闪光周期不冻结 | ✔（用户确认“差不多”） |
| 真实进位时小数位置（第一个 1%） | 实测进位时在 .92，未卡 .99 ✔ |
| SystemUI 启动崩溃修复 | 修复后连续 9 次重启 0 崩溃 ✔ |
| 最终版锁屏、AOD 截图复查 | ✔，无新崩溃 |

### 11.5 已知问题、未验证项与风险

1. **Clockopia 授权未确认**，字体文件已在公开分支和提交历史中；若不可分发，需改为用户导入并清理历史。Clockopia 实机效果未测。
2. 切回“系统字体”能否完全恢复原生显示：未验证（部分路径可能需重启 SystemUI 才完全恢复）。
3. 锁屏通知“解锁后通知栏恢复系统字体”：未人工验证。锁屏媒体卡片不在范围内。
4. 安装新 APK 后的头 1–2 次 SystemUI 启动仍可能出现同类 GC 崩溃后自动恢复（疑为 Vector 刷新模块过渡期）。
5. `Handler.postDelayed` 是全局 hook（仅做身份比较），若将来出现 SystemUI 卡顿/崩溃，优先改为更局部的实现。
6. 充电小数：第一个 1% 偶尔仍可能在 .9x 停顿数秒；17 s 后的小数为模块按电量计数据估算（内核原值同样是电流/容量估算）。
7. 仅验证了 Insight（OpParsonsClock）AOD 样式；其他 AOD 样式、12 小时制、长时间功耗对比均未测。
8. 一次锁屏截图中时钟偏灰，推测为充电动画淡入时的背景压暗，未确认。

### 11.6 构建、安装与排障方法

- 构建：`$env:GRADLE_USER_HOME='D:\AndroidStudio\Oxygen-Customizer\.gradle-home'; & '.\gradlew.bat' :app:assembleDebug`
- 安装：`adb install -r app/build/outputs/apk/debug/OxygenCustomizer.apk`，等待约 20 s 后**只重启一次** SystemUI：`adb shell su -c "kill $(pidof -s com.android.systemui)"`（不要整机重启；`pidof` 可能把 binder 线程列成第二个 PID，用 `-s`）。
- 验证方式：截图检查锁屏（亮屏）和 AOD（熄屏后约 2 s 内截图），不要用反复重启 SystemUI 代替功能验证。
- 模块日志 tag：`VectorLegacyBridge`（前缀 `[OxygenCustomizer-AOD]`、`[OxygenCustomizer-Charging]`、`[OxygenCustomizer-LockNotif]`）；充电动画原生日志 tag：`OplusCharge--`、`SystemUi--Keyguard`；内核小数：`dmesg | grep ui_soc_decimal`。
- 拔掉 USB 测超充时，可在手机上临时持续记录日志：`su -c 'nohup logcat -v time -s OplusCharge--:V VectorLegacyBridge:I -f /data/local/tmp/oc_charge.log -r 4096 -n 2 &'`，测完读取后停止该 logcat 并删除文件。
- 崩溃排查：`logcat -c` 会清空 crash 缓冲区，以 `/data/tombstones/` 为准。

### 11.7 回退方法

- 不影响数据的快速回退：模组页把“全局字体”设为系统字体、“充电动画显示时长”设为默认，然后重启 SystemUI。
- 完全停用：在 Vector 中取消模块对 SystemUI 的作用域或停用模块，重启 SystemUI；无需清除数据。
- 代码回退：`git revert 335d534` 或切回 `beta` 分支重新构建安装。

### 11.8 工作区说明

- 工作区仍保留调试产物（反编译 APK、dexdump、截图、`personality_extract/`、`plugin-apk/`、`style-apk/`、`systemui-apk/`、`.gradle-home/`），未提交、未删除，可作为对照证据；清理前先确认不再需要。
- 手机 `/data/tombstones/` 中保留了历史崩溃记录（12:16 起），未删除。
