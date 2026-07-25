# 下次（NextList）

面向朋友、情侣、室友和其他小团体的共享灵感清单 Android 应用。

## 当前状态

**项目已搁置（2026-07-25）。** 功能开发 M0～M6 全部关闭，本地全量验收与远端 CI
均通过，代码处于可继续开发的干净状态。搁置原因在基础设施而非功能：Firebase 在
中国大陆网络不可达，且无法开通 Blaze 付费计划，而本项目架构完全依赖 Cloud
Functions（Spark 免费版不提供）。已论证的迁移方案与恢复路径见
[PROGRESS.md](./PROGRESS.md) 的“搁置说明”。

已关闭的里程碑：M0”项目骨架”、M1”账号与资料”、M2”小组与邀请”、
M3”想法、表态与评论”、M4”安排、随机与完成”、M5”动态与通知”、
M6”发布加固”（结构化可观测性、账号注销、Crashlytics、R8 混淆、无障碍加固与
隐私政策入口）。产品和技术设计见 [docs/README.md](./docs/README.md)。

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

## 动态与通知

- “动态”实时监听当前用户私有 Feed，首屏 30 条并支持游标分页、下拉刷新、单条
  已读和全部已读。Feed 由 Functions 按确定性 ID 写入，客户端不能创建或删除，
  `expiresAt` 通过 Firestore TTL 保留 90 天。
- “我的 → 通知设置”管理小组邀请、新安排、临近提醒和想法评论四类推送；关闭
  推送不会影响应用内动态。Android 13+ 在该页面申请通知权限，拒绝不影响应用使用。
- 每次登录或 FCM token 刷新时注册安装范围随机 `deviceId`；退出时尽力清理当前
  设备，超时或失败不会阻塞退出。通知 data payload 只包含受限类型与目标 ID，
  评论正文不会进入 payload 或锁屏文案。
- 临近提醒 Function 每 5 分钟查询未来 30 分钟内的活动；本地 Functions 集成测试
  直接调用提醒处理器并注入发送适配器。Firebase Emulator 不提供真实 FCM 投递，
  且未启动 Pub/Sub 时不会自动触发 Scheduled Function，因此真实推送、系统托盘
  行为和 Cloud Scheduler 触发仍需在 Firebase 开发项目验证。

本地 M5 验证：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
npm --prefix functions run check
npm run test:functions:integration
npm run test:rules
./gradlew assembleRelease
```
