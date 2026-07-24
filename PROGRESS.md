# NextList 开发进度

最后更新：2026-07-24

## 当前阶段

**M0：项目骨架（本地验证与设备烟测完成，待首次远端 CI）**

M0 的代码、本地自动化检查和 API 37 模拟器烟测已经完成。正式关闭 M0 前，
还需要在代码推送后确认 GitHub Actions 首次运行通过。

## 本轮已完成

- [x] 初始化 Git 仓库、Gradle Wrapper 和 Android 单模块工程。
- [x] 建立 Kotlin、Jetpack Compose、Material 3 与 Hilt 基础配置。
- [x] 建立“小组 / 动态 / 我的”三个底部导航入口和 M0 占位页面。
- [x] 建立统一错误、加载状态、文本校验与时间抽象。
- [x] 接入 Firebase Auth、Firestore、Functions、Storage 和 Messaging SDK。
- [x] Debug 构建默认连接本地 Emulator，Release 构建不会连接 Emulator。
- [x] 支持缺少 `google-services.json` 时的安全骨架模式。
- [x] 初始化 Cloud Functions v2、Firestore/Storage Rules、索引与 Emulator 配置。
- [x] 建立 Android 单元测试、Functions 单元测试和 Rules 测试。
- [x] 建立 GitHub Actions，覆盖 Android 与 Firebase 两条检查链。
- [x] 生成 npm 锁文件；Functions 生产依赖审计为 0 个已知漏洞。

## 验证记录

| 检查 | 状态 | 结果 |
| --- | --- | --- |
| Gradle Wrapper 生成 | 通过 | Gradle 9.5.0，发行包启用官方 SHA-256 校验 |
| Android 单元测试 | 通过 | 3/3，`testDebugUnitTest` |
| Android Lint | 通过 | `lintDebug`，报告位于 `app/build/reports/` |
| Debug APK | 通过 | `assembleDebug`，约 19 MB |
| 无 Firebase 配置构建 | 通过 | CI 等价的骨架模式构建成功 |
| 本地 Firebase 配置构建 | 通过 | Google Services 处理和 Emulator 接线构建成功 |
| Functions lint/typecheck | 通过 | ESLint + TypeScript |
| Functions 单元测试 | 通过 | 1/1 |
| Firestore Rules 测试 | 通过 | 3/3 |
| Storage Rules 测试 | 通过 | 3/3 |
| Functions 生产依赖审计 | 通过 | 0 个已知漏洞 |
| GitHub Actions | 待运行 | 仓库尚未推送到远端 |
| Android 设备烟测 | 通过 | `Pixel_9_Pro` API 37；Debug 应用启动、三入口切换和 Emulator 状态正常 |

## 技术基线

- Android Gradle Plugin：9.3.0
- Gradle：9.5.0
- Android 构建 JDK：17
- Firebase Emulator JDK：21
- `compileSdk`：37
- `targetSdk`：36
- `minSdk`：26
- Kotlin：2.3.21
- Hilt：2.60.1
- Compose BOM：2026.06.00
- Firebase Android BoM：34.16.0
- Cloud Functions runtime：Node.js 22
- TypeScript：6.0.3
- Firebase CLI：15.24.0

本轮验证使用了位于临时目录的 JDK、Android SDK、Gradle 和 Node.js 22，
没有向项目写入机器相关的 SDK 路径。开发机仍需按 `README.md` 安装或配置
本地工具链。

## 验证中修正的问题

- Hilt 2.59.2 无法读取 Kotlin 2.3 的 metadata，已升级到 2.60.1。
- `typescript-eslint` 尚不支持 TypeScript 7，已固定到 TypeScript 6.0.3。
- TypeScript 6 不再接受旧的 Node10 模块解析默认值，已切换到 Node16 解析。
- Firebase CLI 15 的 Emulator 运行时要求 Java 21，Firebase CI 已改用 JDK 21。
- Firestore 与 Storage Rules 测试共享同一项目时存在竞争，已改为串行执行。
- Functions 间接依赖中的旧版 `uuid` 存在中危公告，已安全覆盖到 11.1.1。

## 当前限制与下一步

1. 推送仓库并确认 GitHub Actions 两个 Job 均通过，随后关闭 M0。
2. 创建真实 Firebase 项目，将控制台配置保存为本地 `app/google-services.json`。
3. 进入 M1“账号与资料”，实现邮箱登录、用户资料和头像上传。
