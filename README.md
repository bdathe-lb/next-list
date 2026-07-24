# 下次（NextList）

面向朋友、情侣、室友和其他小团体的共享灵感清单 Android 应用。

## 当前状态

M0“项目骨架”、M1“账号与资料”、M2“小组与邀请”、M3“想法、表态与评论”和
M4“安排、随机与完成”已经完成。下一阶段为 M5“动态与通知”；真实 Firebase
环境部署与预发布验证仍作为外部配置事项跟踪。开发进度见
[PROGRESS.md](./PROGRESS.md)，产品和技术设计见 [docs/README.md](./docs/README.md)。

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

示例文件指向 `demo-nextlist`，可直接用于本地 Emulator 开发。接入真实环境时，
再把内容替换为 Firebase Console 下载的对应配置。真实 `google-services.json`
不提交版本库。

构建：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

没有 `google-services.json` 时应用仍可安全构建，账号页会说明 Firebase 尚未配置并
禁用提交。存在配置时，Debug 构建默认连接本地 Emulator；Release 构建不会连接
Emulator，并使用 Play Integrity App Check。

## Firebase Emulator

安装依赖：

```bash
npm install
npm --prefix functions install
cp functions/.secret.local.example functions/.secret.local
```

启动 Auth、Firestore、Functions 和 Storage Emulator：

```bash
npm run emulators
```

运行 Functions 与 Rules 检查：

```bash
npm --prefix functions run check
npm run test:functions:integration
npm run test:rules
```

`.secret.local` 只保存本地邀请凭证派生密钥并已被 Git 忽略。Emulator 使用本地演示
项目 `demo-nextlist`，不会连接生产 Firebase 资源。Functions 集成测试也会注入独立
的测试密钥，不依赖本机文件。

想法封面和 M4 完成照片都使用系统 Photo Picker，不需要相册权限。客户端会修正
EXIF 方向、限制尺寸、重新编码为 WebP 并移除原始元数据；Storage Emulator 规则将
图片精确限制在各自的 `cover` 或 `completion` 路径且不超过 2 MB。纯文本想法、
表态、评论和 RSVP 可进入 Firestore 离线队列；安排、随机候选读取、完成记录与
图片上传需要联网。
