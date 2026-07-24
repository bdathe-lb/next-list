# NextList 本地环境清单与卸载指南

最后盘点：2026-07-24

适用机器：Apple Silicon Mac（arm64）

项目路径：`/Users/bdathe/projects/next-list`

## 1. 用途与原则

本文记录为了开发、构建和本地验证 NextList 安装的软件、SDK、运行时、
项目依赖、模拟器和缓存，并提供未来彻底移除环境的操作顺序。

清理时需要区分三类内容：

1. **项目专用**：可以在不影响其他项目的情况下移除。
2. **可能共享**：Gradle、npm、Android SDK 等可能被其他项目使用，删除前必须确认。
3. **项目源码**：`/Users/bdathe/projects/next-list` 中受 Git 管理的文件不是环境缓存，
   除非决定放弃项目，否则不要删除。

本次盘点包含共享缓存和 Codex 临时验证工具链，总占用约 **18.5 GB**。
这个数字会随着依赖下载、构建和模拟器数据变化。

## 2. 系统级软件

### 2.1 Android Studio

| 项目 | 当前值 |
| --- | --- |
| 产品版本 | Android Studio 2026.1 |
| Build | `AI-261.25134.95.2612.15822958` |
| 应用路径 | `/Applications/Android Studio.app` |
| 应用大小 | 约 3.2 GB |
| 内置 JDK | OpenJDK 21.0.10 |
| 安装方式 | 独立 `.app`，不是 Homebrew Cask |

Android Studio 用户数据：

| 路径 | 当前大小 | 类型 |
| --- | ---: | --- |
| `/Users/bdathe/Library/Application Support/Google/AndroidStudio2026.1.2` | 约 10 MB | 设置、插件和状态 |
| `/Users/bdathe/Library/Caches/Google/AndroidStudio2026.1.2` | 约 985 MB | IDE 缓存 |
| `/Users/bdathe/Library/Logs/Google/AndroidStudio2026.1.2` | 约 8 MB | 日志 |

### 2.2 Java

| 项目 | 当前值 |
| --- | --- |
| 独立 JDK | Eclipse Temurin 21.0.11+10 LTS |
| Homebrew Cask | `temurin@21` |
| 安装路径 | `/Library/Java/JavaVirtualMachines/temurin-21.jdk` |
| 当前大小 | 约 336 MB |
| 用途 | Firebase Emulator；也可运行 Gradle |
| Android Studio 内置 JDK | 21.0.10，位于 Android Studio 应用内 |

NextList 不需要额外安装全局 Gradle。项目的 Java 基线为 17，当前 Android Studio
内置 JDK 21 和独立 Temurin 21 均满足该基线，因此不要求同时保留独立 JDK 17。
Gradle Wrapper 会使用 `JAVA_HOME` 指定的兼容 JDK。

### 2.3 Node.js

| 项目 | 当前值 |
| --- | --- |
| NextList 要求版本 | Node.js 22 |
| Homebrew Formula | `node@22` 22.23.1 |
| 安装路径 | `/opt/homebrew/Cellar/node@22` |
| 当前大小 | 约 57 MB |
| 系统中原有版本 | `node` 26.0.0，位于 `/opt/homebrew/Cellar/node` |

`node` 26.0.0 在 NextList 之前已存在，不属于本项目专用环境。清理 NextList 时只移除
`node@22`，不要因为本项目卸载 `node` 26，除非确认其他项目也不再使用。

全局 npm 目录当前还包含 `@google/gemini-cli`。它不是 NextList 依赖，不应随本项目清理。

## 3. Android SDK 与模拟器

### 3.1 SDK 根目录

```text
/Users/bdathe/Library/Android/sdk
```

主要目录当前约占：

| 目录 | 当前大小 |
| --- | ---: |
| `build-tools` | 380 MB |
| `cmdline-tools` | 173 MB |
| `emulator` | 1.1 GB |
| `platform-tools` | 38 MB |
| `platforms` | 287 MB |
| `sources` | 485 MB |
| `system-images` | 2.9 GB |
| `skins` | 16 MB |

已安装 SDK 包：

| SDK 包 | 版本 |
| --- | --- |
| Android SDK Build-Tools | 36.0.0 |
| Android SDK Build-Tools | 37.0.0 |
| Android SDK Command-line Tools | 22.0 / latest |
| Android Emulator | 36.6.11 |
| Android SDK Platform-Tools | 37.0.0 |
| Android SDK Platform | 36.1 |
| Android SDK Platform | 37.0 |
| Android Sources | 36.1 |
| Android Sources | 37.0 |
| Google Play ARM64 16 KB System Image | Android 37.0，revision 6 |

### 3.2 Android Virtual Device

| 项目 | 当前值 |
| --- | --- |
| AVD 名称 | `Pixel_9_Pro` |
| 设备 | Pixel 9 Pro |
| 系统 | Android 37.0 |
| ABI | `arm64-v8a` |
| 镜像 | Google Play，16 KB page size |
| AVD 路径 | `/Users/bdathe/.android/avd/Pixel_9_Pro.avd` |
| AVD 索引 | `/Users/bdathe/.android/avd/Pixel_9_Pro.ini` |
| `.android` 当前总大小 | 约 550 MB |

不要直接删除整个 `/Users/bdathe/.android`。该目录还可能包含 ADB 密钥和其他
Android 工具状态。只删除上面两个 `Pixel_9_Pro` 目标。

## 4. Gradle 与 Android 项目依赖

### 4.1 构建工具

| 项目 | 版本 |
| --- | --- |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.0 |
| Kotlin | 2.3.21 |
| KSP | 2.3.9 |
| Google Services Gradle Plugin | 4.5.0 |

Gradle 的完整版本来源：

- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/libs.versions.toml`

### 4.2 Android 直接依赖

| 依赖 | 版本 |
| --- | --- |
| Hilt / Dagger | 2.60.1 |
| AndroidX Hilt | 1.4.0 |
| AndroidX Activity | 1.13.0 |
| AndroidX Core | 1.18.0 |
| AndroidX DataStore | 1.2.1 |
| AndroidX Lifecycle | 2.11.0 |
| AndroidX Navigation | 2.9.8 |
| AndroidX WorkManager | 2.11.2 |
| Compose BOM | 2026.06.00 |
| Firebase Android BoM | 34.16.0 |
| Coil | 3.5.0 |
| JUnit | 4.13.2 |
| AndroidX Test JUnit | 1.2.1 |
| Espresso | 3.6.1 |

Firebase Android 模块：

- Authentication
- Cloud Firestore
- Cloud Functions
- Cloud Messaging
- Cloud Storage

Compose / AndroidX 模块：

- Activity Compose
- Compose Foundation
- Material Icons Core
- Material 3
- Compose UI、Preview、Tooling 和测试模块
- Lifecycle Runtime Compose
- Lifecycle ViewModel Compose
- Navigation Compose
- DataStore Preferences
- WorkManager Runtime

Gradle 下载的传递依赖由 Gradle 缓存管理，不在本文逐个复制。精确声明以
`gradle/libs.versions.toml` 和 Gradle 锁定的解析结果为准。

## 5. Node.js 与 Firebase 依赖

### 5.1 根项目

根目录直接开发依赖：

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `firebase-tools` | 15.24.0 | Firebase CLI 和 Emulator Suite |

`package-lock.json` 当前记录约 670 个传递包。完整版本以该锁文件为唯一来源。

根项目完整 `npm audit` 目前会报告 7 个中危项，均位于 `firebase-tools` 开发工具链；
`npm audit --omit=dev` 的生产依赖结果为 0。不要使用 `npm audit fix --force`
强行降级 Firebase CLI。

### 5.2 Cloud Functions

生产依赖：

| 依赖 | 版本 |
| --- | --- |
| `firebase-admin` | 14.2.0 |
| `firebase-functions` | 7.3.0 |

开发依赖：

| 依赖 | 版本 |
| --- | --- |
| `@eslint/js` | 10.0.1 |
| `@firebase/rules-unit-testing` | 5.0.1 |
| `@types/node` | 22.20.1 |
| `eslint` | 10.7.0 |
| `firebase` | 12.16.0 |
| `typescript` | 6.0.3 |
| `typescript-eslint` | 8.65.0 |

安全覆盖：

| 依赖 | 固定版本 | 原因 |
| --- | --- | --- |
| `uuid` | 11.1.1 | 避免旧版 buffer bounds 安全问题 |

`functions/package-lock.json` 当前记录约 411 个传递包。完整版本以锁文件为唯一来源。

## 6. Firebase Emulator 下载

Firebase CLI 下载的 Emulator 文件位于：

```text
/Users/bdathe/.cache/firebase/emulators
```

当前文件：

- `cloud-firestore-emulator-v1.21.0.jar`
- `cloud-storage-rules-runtime-v1.1.3.jar`

当前整个 Firebase 缓存约 182 MB。该缓存可能被其他 Firebase 项目共享。

## 7. 项目本地生成内容

这些内容都可以重新生成，不属于源码：

| 路径 | 当前大小 | 生成方式 |
| --- | ---: | --- |
| `/Users/bdathe/projects/next-list/node_modules` | 约 272 MB | 根目录 `npm ci` |
| `/Users/bdathe/projects/next-list/functions/node_modules` | 约 275 MB | Functions `npm ci` |
| `/Users/bdathe/projects/next-list/functions/lib` | 约 72 KB | TypeScript 编译 |
| `/Users/bdathe/projects/next-list/.gradle` | 约 6.8 MB | Gradle 项目缓存 |
| `/Users/bdathe/projects/next-list/app/build` | 约 97 MB | Android 构建 |
| `/Users/bdathe/projects/next-list/build` | 约 1.2 MB | 根项目构建报告 |
| `/Users/bdathe/projects/next-list/local.properties` | 很小 | 本机 Android SDK 路径 |

将来还可能出现以下被 `.gitignore` 排除的本地文件：

- `app/google-services.json`
- `.firebase/`
- `firebase-debug.log`
- `firestore-debug.log`
- `ui-debug.log`
- `*.tsbuildinfo`

## 8. 用户级共享缓存

| 路径 | 当前大小 | 注意事项 |
| --- | ---: | --- |
| `/Users/bdathe/.gradle` | 约 1.4 GB | 所有 Gradle 项目共享 |
| `/Users/bdathe/.npm` | 约 926 MB | 所有 npm 项目共享 |
| `/Users/bdathe/.cache/firebase` | 约 182 MB | 所有 Firebase 项目共享 |

只有在确认没有其他项目需要这些缓存时才删除。删除缓存不会删除源码，但后续构建会重新下载。

## 9. Codex 临时验证工具链

M0 验证期间，Codex 在以下专用临时目录下载了独立工具链：

```text
/private/tmp/nextlist-toolchain
```

当前约 4.8 GB，包括：

- Microsoft OpenJDK 17.0.20 和 21.0.12
- Node.js 22.23.1 ARM64
- Gradle 9.5.0 和发行压缩包
- 独立 Android SDK、Platform 37 和 Build Tools
- Android Command-line Tools 压缩包
- npm 下载缓存
- Gradle 验证缓存
- Firebase CLI 临时配置

该目录仅服务于 NextList 的 M0 验证，不被正式 Android Studio、Homebrew Node 22
或系统 Temurin 使用。确认不再需要复现 M0 验证后，可以单独删除。

## 10. Shell 配置变更

当前 `/Users/bdathe/.zshrc` 中与 NextList 环境有关的内容：

```bash
export PATH="/opt/homebrew/opt/node@22/bin:$PATH"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

其中 Node 22 的 `PATH` 行目前重复出现两次。环境仍在使用期间可以只保留一行；
彻底卸载时应删除两行以及上面其他 NextList 环境行。

## 11. 安全卸载顺序

### 11.1 停止运行中的服务

1. 在 Firebase Emulator 所在终端按 `Control-C`。
2. 关闭正在运行的 Android 模拟器。
3. 退出 Android Studio。
4. 停止 ADB：

```bash
/Users/bdathe/Library/Android/sdk/platform-tools/adb kill-server
```

确认常用端口没有监听：

```bash
lsof -nP -iTCP:4000 -sTCP:LISTEN
lsof -nP -iTCP:4400 -sTCP:LISTEN
lsof -nP -iTCP:5001 -sTCP:LISTEN
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:9099 -sTCP:LISTEN
lsof -nP -iTCP:9199 -sTCP:LISTEN
```

### 11.2 只清理项目生成内容，保留开发软件

使用 Finder 的“前往文件夹”逐个定位，并将以下目标移到废纸篓：

```text
/Users/bdathe/projects/next-list/node_modules
/Users/bdathe/projects/next-list/functions/node_modules
/Users/bdathe/projects/next-list/functions/lib
/Users/bdathe/projects/next-list/.gradle
/Users/bdathe/projects/next-list/app/build
/Users/bdathe/projects/next-list/build
/Users/bdathe/projects/next-list/local.properties
/Users/bdathe/projects/next-list/app/google-services.json
/Users/bdathe/projects/next-list/.firebase
```

同时可移除项目根目录下的 Firebase debug 日志。不要删除 `package-lock.json`、
`functions/package-lock.json`、Gradle Wrapper、源码或 `docs/`。

### 11.3 删除 AVD

推荐使用 Android Studio：

1. 打开 Device Manager。
2. 找到 `Pixel_9_Pro`。
3. 打开更多菜单，选择 Delete。

删除后确认以下两个目标不存在：

```text
/Users/bdathe/.android/avd/Pixel_9_Pro.avd
/Users/bdathe/.android/avd/Pixel_9_Pro.ini
```

### 11.4 删除 Android SDK

如果还有其他 Android 项目，使用 `Tools > SDK Manager` 只卸载第 3 节列出的包。

只有在确认这台机器不再进行任何 Android 开发时，才将整个目录移到废纸篓：

```text
/Users/bdathe/Library/Android/sdk
```

不要在未确认其他项目的情况下删除整个 SDK。

### 11.5 卸载 Node 22 和 Temurin 21

```bash
brew uninstall node@22
brew uninstall --cask temurin@21
```

不要卸载 Homebrew 的 `node` 26.0.0；它不是 NextList 专用安装。

### 11.6 卸载 Android Studio

Android Studio 不是通过 Homebrew 安装。退出应用后，将以下目标移到废纸篓：

```text
/Applications/Android Studio.app
/Users/bdathe/Library/Application Support/Google/AndroidStudio2026.1.2
/Users/bdathe/Library/Caches/Google/AndroidStudio2026.1.2
/Users/bdathe/Library/Logs/Google/AndroidStudio2026.1.2
```

不要删除整个 `Google` 目录，因为其中可能存在其他 Google 应用的数据。

### 11.7 删除共享缓存

仅在确认其他项目不再使用时，将以下目标移到废纸篓：

```text
/Users/bdathe/.gradle
/Users/bdathe/.npm
/Users/bdathe/.cache/firebase
```

如果只想移除 Firebase Emulator 下载而保留其他 Firebase 缓存，只删除：

```text
/Users/bdathe/.cache/firebase/emulators
```

### 11.8 删除 Codex 临时工具链

先检查目标大小和内容：

```bash
du -sh /private/tmp/nextlist-toolchain
find /private/tmp/nextlist-toolchain -maxdepth 1 -mindepth 1 -print
```

确认路径准确后，可以删除这个项目专用临时目录：

```bash
rm -rf /private/tmp/nextlist-toolchain
```

不要将删除目标扩大到 `/private/tmp`。

### 11.9 清理 Shell 配置

编辑 `/Users/bdathe/.zshrc`，删除第 10 节列出的环境变量和重复的 Node 22 `PATH` 行，
然后重新打开终端。

## 12. 卸载后验证

以下命令应该找不到 NextList 专用安装，或者返回对应目标不存在：

```bash
brew list --versions node@22
brew list --cask --versions temurin@21
test -e "/Applications/Android Studio.app"
test -e "/Users/bdathe/Library/Android/sdk"
test -e "/Users/bdathe/.android/avd/Pixel_9_Pro.avd"
test -e "/private/tmp/nextlist-toolchain"
```

检查 Shell 中不再残留环境变量：

```bash
env | grep -E '^(JAVA_HOME|ANDROID_HOME|ANDROID_SDK_ROOT)='
echo "$PATH" | tr ':' '\n' | grep -E 'node@22|Library/Android/sdk|Android Studio'
```

若保留了 Homebrew Node 26，卸载后 `node --version` 可能重新显示 `v26.0.0`，
这是预期行为。

## 13. 源码去留

以上步骤不会删除 Git 源码。如果决定连项目本身也放弃，先确认代码已备份或推送，
再单独处理：

```text
/Users/bdathe/projects/next-list
```

不要把“删除开发环境”和“删除项目源码”混为一个操作。
