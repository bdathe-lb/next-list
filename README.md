# 下次（NextList）

面向朋友、情侣、室友和其他小团体的共享灵感清单 Android 应用。

## 当前状态

项目正在进行 M0“项目骨架”。开发进度见 [PROGRESS.md](./PROGRESS.md)，产品和技术设计见 [docs/README.md](./docs/README.md)。

## 工程组成

```text
app/          Android Kotlin + Jetpack Compose 客户端
functions/    Cloud Functions for Firebase v2
firebase/     Firestore/Storage Rules 与索引
docs/         产品与开发文档
```

## 本地要求

- JDK 17（Android 构建）
- JDK 21（Firebase Emulator）
- Android SDK 37，Android Build Tools 36.0.0
- Node.js 22（Functions 部署运行时）
- npm

本机 Node.js 可以高于 22，但 Functions 的 `engines` 固定为 Node.js 22，CI 也使用 Node.js 22。

## Android

复制 Firebase Android 配置：

```bash
cp app/google-services.json.example app/google-services.json
```

随后把文件内容替换为 Firebase Console 下载的真实配置。真实 `google-services.json` 不提交版本库。

构建：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

没有 `google-services.json` 时应用仍可构建并展示 M0 骨架，但不会初始化 Firebase。存在真实配置时，Debug 构建默认连接本地 Emulator。

## Firebase Emulator

安装依赖：

```bash
npm install
npm --prefix functions install
```

启动 Auth、Firestore、Functions 和 Storage Emulator：

```bash
npm run emulators
```

运行 Functions 与 Rules 检查：

```bash
npm --prefix functions run check
npm run test:rules
```

Emulator 使用本地演示项目 `demo-nextlist`，不会连接生产 Firebase 资源。
