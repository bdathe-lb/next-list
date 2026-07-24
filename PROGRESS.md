# NextList 开发进度

最后更新：2026-07-24

## 当前阶段

**M3：想法、表态与评论（已完成）**

M0“项目骨架”、M1“账号与资料”和 M2“小组与邀请”均已关闭。M3 的 Android
实现、可信聚合触发器、Security Rules、单元/集成/设备测试以及两台独立
Pixel_9_Pro API 37 Emulator 烟测已经完成本地验收。实现提交 `83cd9ed` 的
[GitHub Actions CI #30086419538](https://github.com/bdathe-lb/next-list/actions/runs/30086419538)
中 Android 与 Functions and Firebase Rules 两个 Job 均通过，M3 正式关闭。
下一阶段为 M4“安排与完成”。

## M3 已完成

### 想法列表与 CRUD

- [x] 小组详情提供“想法 / 已安排 / 已完成”三标签；默认打开想法，并为每个标签
  保留独立滚动位置。
- [x] 只监听当前标签；支持加载、失败、重试、下拉刷新、通用空状态和分类无结果
  空状态。
- [x] 想法按创建时间倒序；提供全部、地点、餐厅、KTV 歌曲、电影、活动和其他
  横向筛选。
- [x] 创建、详情、编辑和软删除完整闭环；只有创建者能编辑正文，创建者或管理员
  能删除。
- [x] 标题、备注、地址/链接和评论按 Unicode 长度校验；提交期间防重复写入。
- [x] 地址为 `http/https` 时使用系统安全打开，其他内容按普通文本展示。
- [x] 表单离开前提示未保存修改；远端删除或失权时详情页自动退出。

### 图片、实时与离线

- [x] 使用系统 Photo Picker，不申请全量相册权限；复用图片处理器修正 EXIF 方向、
  限制尺寸、重新编码 WebP 并移除元数据。
- [x] 想法封面客户端输出不超过 2 MB，Storage 路径和 Rules 精确限制为
  `groups/{groupId}/ideas/{ideaId}/cover/{fileId}.webp`。
- [x] 图片上传要求服务端确认；上传失败保留表单，可重试或移除。替换或软删除后
  由 Function 清理旧对象。
- [x] 纯文本想法、表态和评论支持 Firestore 离线队列；界面区分首次加载、缓存、
  待同步、失败和重试。

### 表态与评论

- [x] 有效成员可设置、覆盖、取消 `want / ok / not_interested`；三类人数实时更新。
- [x] 成员明细底部弹层按态度分组展示成员快照，并显示未表态人数。
- [x] 想法创建者、表态者和评论者的头像路径按需解析并在详情生命周期内缓存。
- [x] 评论最近 50 条实时监听，服务端按时间倒序查询后在界面按正序显示。
- [x] 评论作者或管理员可二次确认后软删除；评论不可编辑，删除正文不再可读。
- [x] 成员退出或被移除后，Functions 清理其当前表态和 RSVP 占位数据。
- [x] M3 不创建个人动态，也没有 FCM 发送入口；M4/M5 写入继续由 Rules 拒绝。

### 聚合、安全与并发

- [x] Functions 事务从真实子集合精确重算 `ideaCount / scheduledCount /
  completedCount`、`reactionCounts` 和 `commentCount`，避免负数和增量漂移。
- [x] 每个 CloudEvent 使用 `functionEvents` 幂等记录；重复投递、并发创建、表态
  切换/取消及评论软删除均保持精确计数。
- [x] Rules 校验字段白名单、固定枚举、服务端时间、当前 membership 快照和所有
  不可变/聚合字段；非成员和已退出成员不能读取或写入。
- [x] 普通成员不能修改或删除他人想法；管理员只能删除他人想法，不能冒充创建者
  修改正文。
- [x] 表态文档 ID、`userId` 和登录用户一致；评论身份与成员快照不能伪造；已删除
  内容不能继续表态或评论。
- [x] 仅加入当前 M3 查询和成员退出清理所需复合索引；安排、RSVP 和完成写入仍为
  安全占位。

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
| Android 单元测试 | 通过 | 40/40，含 Unicode 校验、DTO、状态、权限、头像、重试和防重复提交 |
| Android Lint | 通过 | 0 errors；仅保留依赖版本提示 |
| Debug APK | 通过 | `assembleDebug` |
| Compose / Android 设备测试 | 通过 | 14/14，在两台 Pixel_9_Pro API 37 上各执行一次，共 28 次通过 |
| Functions lint/typecheck | 通过 | ESLint + TypeScript |
| Functions 单元测试 | 通过 | 10/10，含 M2 基线与 M3 聚合输入/桶规则 |
| Functions Emulator 集成 | 通过 | 2/2 场景；M2 事务基线及 M3 并发、幂等、聚合、清理 |
| Firestore Rules | 通过 | 27/27 allow/deny 用例 |
| Storage Rules | 通过 | 9/9 allow/deny 用例 |
| Release APK | 通过 | `assembleRelease`，未连接 Emulator |
| API 37 M3 双设备烟测 | 通过 | CRUD、双人表态、成员明细、评论、软删除、实时计数 |
| GitHub Actions | 通过 | [CI #30086419538](https://github.com/bdathe-lb/next-list/actions/runs/30086419538)，Android 与 Functions and Firebase Rules 两个 Job |

### API 37 M3 烟测明细

设备：两台独立 `Pixel_9_Pro`，API 37；后端：`demo-nextlist` Firebase Emulator。

- A 与 B 同在一个 2 人小组；A 创建纯文本想法后，B 无需刷新即看到卡片。
- A 修改标题后，B 实时显示新标题。
- A 选择“想参加”、B 选择“都可以”后，两端分别显示 1 人；成员明细正确分组显示
  两位成员，未表态为 0。
- B 发布评论后 A 实时显示 `评论 1`；B 二次确认软删除后 A 实时回到 `评论 0`。
- A 以创建者/管理员身份软删除想法后，两端想法列表立即为空；数据库侧
  `groups/{groupId}.ideaCount` 回到 0。
- 数据库侧确认两位用户的 `feed` 子集合均为 0；M3 Functions 未调用 Messaging，
  因此普通表态没有动态或通知投递。
- 烟测发现并修复了远端软删除导致详情监听返回 `PERMISSION_DENIED` 后未自动退出的
  问题，并补充 ViewModel 回归测试。

### API 37 M2 烟测明细

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
M3 远端 CI：[CI #30086419538](https://github.com/bdathe-lb/next-list/actions/runs/30086419538)，
提交 `83cd9ed` 的 Android 与 Functions and Firebase Rules 两个 Job 均通过。

## 当前限制与外部配置

- 本地使用仓库提供的示例配置和 `demo-nextlist` Emulator 完成开发与验收。
- 尚未创建或接入真实 Firebase 开发/生产项目；真实 `google-services.json`、
  `NEXTLIST_INVITE_SECRET`、Play Integrity 注册和生产 Rules/App Check smoke test
  仍是外部配置事项。
- 邮件发件模板/域名、动态链接落地页、生产监控、隐私政策和账号注销按后续发布
  加固计划处理。
- M3 的 Firestore/Storage Rules、复合索引和 Functions 尚未部署到真实项目；生产
  环境还需部署后执行 App Check、Rules、图片上传和两设备预发布 smoke test。
- M4 的安排、RSVP、随机决定和完成记录，以及 M5 的动态、通知偏好业务、FCM、
  临近提醒和通知导航均未实现；对应客户端写入口仍关闭。

## 下一步

1. 进入 M4“安排与完成”，实现活动安排、RSVP、随机决定和完成记录。
2. 如具备真实 Firebase 项目，部署 M3 Rules、索引和 Functions，配置 Play
   Integrity，并执行生产预发布 App Check / Rules / Storage smoke test。
