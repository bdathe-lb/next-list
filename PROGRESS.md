# NextList 开发进度

最后更新：2026-07-24

## 当前阶段

**M1：账号与资料（已完成）**

M0“项目骨架”已关闭。M1 的实现、Android 单元测试、Compose 设备测试、
Firebase Rules 测试以及 Pixel_9_Pro API 37 Emulator 烟测均已完成。
下一阶段为 M2“小组与邀请”。

## M1 已完成

### 账号

- [x] 邮箱注册，包含昵称、邮箱、密码与确认密码校验。
- [x] 邮箱密码登录及安全的中文错误文案。
- [x] Firebase 登录状态恢复与根级登录态路由。
- [x] 首次注册后进入可恢复的资料完善流程。
- [x] 邮箱验证状态展示、状态刷新与验证邮件重新发送。
- [x] 忘记密码与重置邮件发送，使用不泄露邮箱注册状态的统一成功文案。
- [x] 退出登录并清除受保护页面。
- [x] 提交期间立即禁用操作，避免按钮连点和重复提交。

### 用户资料

- [x] 按数据字典写入 `users/{uid}`，包含昵称、头像路径、验证状态、
  默认通知偏好、状态、服务端时间与 `schemaVersion`。
- [x] 昵称按 2～20 个 Unicode 字符校验并去除首尾空格。
- [x] 未上传头像时使用昵称首字符生成默认头像。
- [x] 使用系统 Photo Picker 选择头像，不申请全量相册权限。
- [x] 客户端读取 EXIF 方向、缩放长边、重新编码 WebP 并去除原始元数据，
  输出目标不超过 2 MB。
- [x] 头像上传至 `users/{uid}/avatar/{fileId}.webp`，Firestore 只保存
  Storage path。
- [x] 支持查看、修改和移除昵称/头像；替换成功后清理旧头像对象。

### 架构与错误处理

- [x] Compose 页面只消费不可变 `UiState`，不直接调用 Firebase SDK。
- [x] Auth、Firestore 与 Storage 调用位于 data 层，并通过 repository/use case
  暴露给 ViewModel。
- [x] Firebase Auth 状态与 `users/{uid}` 文档共同决定
  “登录 / 完善资料 / 主界面”路由。
- [x] Firebase 异常统一转换为有限 `AppError` 和中文文案，不展示错误码、
  异常正文或堆栈。
- [x] 加载、失败、重试、局部提交与网络错误状态均有明确界面反馈。
- [x] Debug 继续使用 `10.0.2.2` Emulator；Release 的 Emulator 开关保持关闭。
- [x] 缺少 `google-services.json` 时仍可构建，并在账号页安全禁用提交。

### Security Rules

- [x] Firestore 用户资料仅本人可读写。
- [x] 用户文档严格校验字段集合、类型、昵称、头像路径、默认结构、
  `schemaVersion` 和服务端时间。
- [x] `emailVerified` 创建/更新值必须与 Auth token 声明一致。
- [x] 不允许客户端修改用户状态、创建时间或写入邮箱等数据字典外字段。
- [x] Storage 头像仅本人可写/删，未登录访问被拒绝。
- [x] 头像只接受安全文件名、`image/webp` 且小于 10 MB；其他路径、
  类型和超限对象被拒绝。

## 验证记录

| 检查 | 状态 | 结果 |
| --- | --- | --- |
| Android 单元测试 | 通过 | 14/14，校验、错误文案、登录态路由和防重复提交 |
| Android Lint | 通过 | 0 errors；仅保留既有 SDK/依赖版本提示 |
| Debug APK | 通过 | `assembleDebug`，约 20 MB |
| Compose / Android 设备测试 | 通过 | 3/3，Pixel_9_Pro API 37 |
| Functions lint/typecheck | 通过 | ESLint + TypeScript |
| Functions 单元测试 | 通过 | 1/1 |
| Firestore / Storage Rules | 通过 | 15/15 allow/deny 用例 |
| API 37 M1 设备烟测 | 通过 | 注册、资料、头像、恢复、编辑、验证、重置、退出与重新登录 |

### API 37 烟测明细

设备：`Pixel_9_Pro`，API 37；后端：`demo-nextlist` Firebase Emulator。

- 未登录冷启动进入登录页。
- Emulator 邮箱账号注册成功，随后进入资料完善页。
- 昵称保存到正确的 `users/{uid}` 文档。
- 头像成功压缩为 `image/webp`（本次 32,850 bytes），上传到
  `users/{uid}/avatar/{UUID}.webp`，文档保存相同 `avatarPath`。
- 强制停止并冷启动后恢复登录状态，直接进入空的小组列表。
- “我的”页面正确显示邮箱、昵称、头像和未验证状态。
- 昵称修改为 `SmokeUser2`、头像上传和远端展示均成功。
- 验证邮件重新发送成功；通过 Auth Emulator 标记验证后，客户端刷新为
  “邮箱已验证”，Firestore `emailVerified` 同步为 `true`。
- 忘记密码请求成功并显示统一文案。
- 退出后返回登录页；使用同一邮箱密码重新登录并恢复资料。
- 设备烟测发现并修正了 bounds-only 图片解码返回 `null` 被误判为文件不可读
  的问题，随后新增内容 URI 图片处理设备回归并通过。

## M0 基线

- [x] Kotlin、Jetpack Compose、Material 3、Hilt 与单 Activity 架构。
- [x] Firebase Auth、Firestore、Functions、Storage、Messaging SDK 与 Emulator。
- [x] Android / Functions / Rules CI 检查链。
- [x] `main` 的 M0 关闭提交为 `daaf13f chore: close M0`。

此前远端 CI 记录：[CI #30066326401](https://github.com/bdathe-lb/next-list/actions/runs/30066326401)。
M1 本地验收结果见上表；远端复验由 GitHub Actions 的 M1 收尾提交运行记录提供。

## 当前限制与外部配置

- 本地使用仓库提供的示例配置和 `demo-nextlist` Emulator 完成开发与验收。
- 尚未创建或接入真实 Firebase 开发/生产项目；真实 `google-services.json`、
  邮件发件模板/域名和生产 Rules smoke test 仍是外部配置事项。
- App Check 强制执行、生产监控、隐私政策和账号注销按 M6 发布加固计划处理。
- M1 未实现好友、聊天、小组、想法、动态或通知业务。

## 下一步

1. 如具备真实 Firebase 项目，按 Debug/Release 包名接入本地配置并验证邮件模板。
2. 进入 M2“小组与邀请”，实现创建、成员、邀请码与角色权限。
