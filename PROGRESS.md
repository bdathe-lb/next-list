# NextList 开发进度

最后更新：2026-07-24

## 当前阶段

**M2：小组与邀请（已完成）**

M0“项目骨架”、M1“账号与资料”和 M2“小组与邀请”均已关闭。M2 的 Android
实现、Functions 事务、Security Rules、单元/集成/设备测试、两台独立
Pixel_9_Pro API 37 Emulator 烟测以及 GitHub Actions 远端验收均已完成。
下一阶段为 M3“想法清单”。

## M2 已完成

### 小组与成员

- [x] 创建小组并原子创建唯一管理员成员记录；名称按 2～30 个 Unicode 字符校验。
- [x] “我的小组”实时列表、空状态、成员头像、成员数、想法数和已安排数。
- [x] 小组详情与成员列表；M3/M4 能力显示安全占位，不允许提前写业务数据。
- [x] 管理员重命名、移除成员、转让管理员和解散小组。
- [x] 普通成员退出；管理员必须先转让或解散，不能直接退出。
- [x] 成员上限固定为 10，加入并发由 Firestore 事务串行校验。
- [x] 成员被移除、退出或小组解散后，详情监听立即失权并返回小组列表。
- [x] 用户昵称或头像变化由 Functions 触发器同步到所有活跃成员快照。

### 邀请

- [x] 管理员获取同一有效邀请、主动轮换邀请，并立即撤销旧凭证。
- [x] 通用邀请同时提供不可枚举链接 token 和 8 位短码；短码排除易混字符。
- [x] Firestore 只保存 token/code 的 SHA-256 摘要、密钥版本和状态，不保存明文。
- [x] 邀请明文由 HMAC 密钥和不可预测邀请 ID 派生；密钥使用 Functions Secret。
- [x] 链接、短码和定向邀请先预览小组，再由用户明确确认加入。
- [x] Android App Link、自定义 scheme 和 DataStore 待处理邀请恢复。
- [x] 定向邀请按已注册邮箱查找，但响应始终统一，不泄露邮箱是否注册。
- [x] 定向邀请仅写入目标用户私有子集合，客户端可接受或拒绝。
- [x] 邀请默认 7 天有效；无效短码按用户/实例限流并返回安全中文文案。

### 架构、安全与容错

- [x] 所有小组业务写入都通过 Cloud Functions v2；客户端对小组、成员和通用邀请
  文档无直接写权限。
- [x] 生产 callable 强制 App Check；Release 使用 Play Integrity，Emulator
  明确绕过强制并只在 Debug 允许本地明文地址。
- [x] 创建和轮换使用 request ID；加入、退出、移除、转让和解散均可安全重试。
- [x] 事务保持 `group.adminId` 与唯一活跃 `admin` 成员一致，并原子维护成员数。
- [x] 业务错误码统一映射为有限 `AppError` 和中文文案，不向界面暴露异常正文。
- [x] 提交期间禁用操作，危险操作包含二次确认，解散必须输入小组名称。
- [x] Firestore collection-group 索引支持按用户查询活跃小组。

### Security Rules

- [x] 活跃成员可读小组与成员；非成员、已退出/移除成员和解散后成员不可读小组。
- [x] 用户可读取自己的成员记录，以支持 collection-group 实时查询和失权移除。
- [x] 通用邀请与 Functions 幂等事件集合完全禁止客户端读写。
- [x] 定向邀请仅目标用户可读；客户端只允许把自己的 `pending` 邀请改为
  `declined` 并使用服务端时间。
- [x] M1 用户资料和 Storage 保护规则保持不变。

## 验证记录

| 检查 | 状态 | 结果 |
| --- | --- | --- |
| Android 单元测试 | 通过 | 23/23，校验、DTO、错误映射、加载/错误状态和防重复提交 |
| Android Lint | 通过 | 0 errors；仅保留依赖版本提示 |
| Debug APK | 通过 | `assembleDebug`，约 19 MB |
| Compose / Android 设备测试 | 通过 | 6/6，在两台 Pixel_9_Pro API 37 上各执行一次 |
| Functions lint/typecheck | 通过 | ESLint + TypeScript |
| Functions 单元测试 | 通过 | 6/6，邀请加密、输入校验和限流 |
| Functions Emulator 集成 | 通过 | 12 个账号覆盖事务、并发、幂等、权限和竞态 |
| Firestore / Storage Rules | 通过 | 25/25 allow/deny 用例 |
| API 37 M2 双设备烟测 | 通过 | 创建、邀请预览、加入、实时 2 人、移除与失权自动返回 |
| GitHub Actions | 通过 | Android 与 Functions and Firebase Rules 两个 Job |

### API 37 烟测明细

设备：两台独立 `Pixel_9_Pro`，API 37；后端：`demo-nextlist` Firebase Emulator。

- 两个已验证账号分别显示 M2 要求的空小组状态。
- 管理员创建 `M2SmokeGroup` 后进入详情页并显示 1 位成员。
- 邀请页生成 8 位短码和遮蔽后的分享链接，未在日志中输出完整 token。
- 第二台设备输入短码后先看到小组预览，确认加入后两端实时显示 2 位成员。
- 管理员成员页正确显示管理员/成员角色，以及转让和移除动作。
- 管理员确认移除后，第二台设备的详情监听收到 `PERMISSION_DENIED`，立即返回空
  小组页并显示“你已不在这个小组中”。
- 本轮烟测发现并修正了 Debug 本地 Functions 明文访问策略，以及示例
  `google-services.json` 占位 API key 格式不合法的问题；Release 安全策略未放宽。

## M1 与 M0 基线

- [x] 邮箱注册、登录、验证、重置密码、退出和登录状态恢复。
- [x] 用户昵称、默认头像、Photo Picker、WebP 压缩、上传、修改和移除。
- [x] 用户资料与 Storage 的本人权限、字段形状、类型、时间和文件约束。
- [x] Kotlin、Jetpack Compose、Material 3、Hilt 与单 Activity 架构。
- [x] Firebase Auth、Firestore、Functions、Storage、Messaging SDK 与 Emulator。
- [x] Android / Functions / Rules CI 检查链。

M0 关闭提交：`daaf13f chore: close M0`。
M1 远端 CI：[CI #30070567072](https://github.com/bdathe-lb/next-list/actions/runs/30070567072)，
提交 `cd71306` 的 Android 与 Functions and Firebase Rules 两个 Job 均通过。
M2 远端 CI：[CI #30078762899](https://github.com/bdathe-lb/next-list/actions/runs/30078762899)，
提交 `fb8286a` 的 Android 与 Functions and Firebase Rules 两个 Job 均通过。

## 当前限制与外部配置

- 本地使用仓库提供的示例配置和 `demo-nextlist` Emulator 完成开发与验收。
- 尚未创建或接入真实 Firebase 开发/生产项目；真实 `google-services.json`、
  `NEXTLIST_INVITE_SECRET`、Play Integrity 注册和生产 Rules/App Check smoke test
  仍是外部配置事项。
- 邮件发件模板/域名、动态链接落地页、生产监控、隐私政策和账号注销按后续发布
  加固计划处理。
- M2 未实现想法 CRUD、状态流转、动态或通知投递；界面仅展示 M3/M4 安全占位。

## 下一步

1. 进入 M3“想法清单”，实现小组内想法 CRUD、分类、投票和权限规则。
2. 如具备真实 Firebase 项目，配置生产密钥与 Play Integrity，并执行生产预发布
   App Check / Rules smoke test。
