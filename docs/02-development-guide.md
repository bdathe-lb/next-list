# 「下次」Android MVP 开发文档

本文档描述首版 Android 客户端与 Firebase 后端的推荐实现。它以 [功能文稿](./01-product-spec.md) 为产品依据，以 [Firestore 数据字典](./03-firestore-data-dictionary.md) 为字段依据。

## 1. 技术目标

### 1.1 目标

- 用一套简单、可维护的原生 Android 架构完成 MVP。
- 核心小组数据在多设备间实时同步。
- 权限由 Firebase Security Rules 和服务端函数共同保证。
- 支持 Firestore 本地缓存和有限离线写入。
- 关键服务端操作可重试、幂等并可审计。
- 数据模型允许未来增加第二种登录方式，但不提前实现社交功能。

### 1.2 非目标

- 不为了未来可能的 Web/iOS 客户端引入跨端框架。
- 不建立通用聊天、工作流、投票或推荐引擎。
- 不把所有业务写入都包装成 Cloud Function；适合实时和离线的内容写入仍由客户端直写 Firestore。
- 不在 MVP 内建设独立管理后台。必要运维通过 Firebase Console 和受控脚本完成。

## 2. 技术选型

### 2.1 Android

| 领域 | 选择 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity、分层架构、MVVM/UDF |
| 依赖注入 | Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 导航 | Navigation Compose |
| 图片 | Coil |
| 本地轻量设置 | DataStore |
| 后台任务 | WorkManager，仅用于客户端可重试任务 |
| 序列化 | Kotlin Serialization |
| 日志 | Timber 或轻量封装，Release 移除敏感内容 |
| 单元测试 | JUnit、kotlinx-coroutines-test、Turbine |
| UI 测试 | Compose UI Test |

建议最低系统版本为 Android 8.0（API 26）。`compileSdk`、`targetSdk` 和库版本在初始化项目时选用当时的稳定版本，并统一记录在 Version Catalog，不在业务模块散落版本号。

### 2.2 Firebase / Google Cloud

| 能力 | 服务 |
| --- | --- |
| 邮箱注册登录 | Firebase Authentication |
| 业务数据与实时监听 | Cloud Firestore |
| 头像和图片 | Cloud Storage for Firebase |
| 推送 | Firebase Cloud Messaging |
| 权限、邀请、聚合、通知 | Cloud Functions for Firebase v2（TypeScript） |
| 活动临近任务 | Scheduled Function + Cloud Scheduler |
| 崩溃收集 | Firebase Crashlytics |
| 产品事件 | Google Analytics for Firebase，可按隐私要求关闭 |
| 滥用防护 | Firebase App Check |
| 本地联调 | Firebase Emulator Suite |

邀请链接使用 Android App Links，例如：

```text
https://nextlist.example/invite/{token}
```

域名只承载 App Link 验证文件和未安装应用时的说明页，不视为产品 Web 版本。不要依赖已停止服务的 Firebase Dynamic Links。

### 2.3 首版登录策略

首版实现 Firebase Email/Password：

- 注册。
- 登录。
- 重置密码。
- 邮箱验证。
- 退出登录。

微信登录暂不实现，但领域层不得把用户标识命名为 `emailUserId`；统一使用 Firebase `uid`。

## 3. 工程结构

MVP 推荐先使用单 `app` 模块，通过包分层保持边界。项目明显变大后再按功能拆 Gradle 模块，避免一开始承担多模块成本。

```text
NextList/
├─ app/
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/example/nextlist/
│     │  │  ├─ NextListApp.kt
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ core/
│     │  │  │  ├─ designsystem/
│     │  │  │  ├─ navigation/
│     │  │  │  ├─ result/
│     │  │  │  ├─ time/
│     │  │  │  └─ validation/
│     │  │  ├─ data/
│     │  │  │  ├─ auth/
│     │  │  │  ├─ firestore/
│     │  │  │  ├─ functions/
│     │  │  │  ├─ messaging/
│     │  │  │  ├─ storage/
│     │  │  │  └─ repository/
│     │  │  ├─ domain/
│     │  │  │  ├─ model/
│     │  │  │  ├─ repository/
│     │  │  │  └─ usecase/
│     │  │  └─ feature/
│     │  │     ├─ auth/
│     │  │     ├─ groups/
│     │  │     ├─ ideas/
│     │  │     ├─ random/
│     │  │     ├─ activityfeed/
│     │  │     └─ profile/
│     │  └─ res/
│     ├─ test/
│     └─ androidTest/
├─ functions/
│  ├─ src/
│  │  ├─ callable/
│  │  ├─ triggers/
│  │  ├─ scheduled/
│  │  └─ shared/
│  └─ test/
├─ firebase/
│  ├─ firestore.rules
│  ├─ firestore.indexes.json
│  └─ storage.rules
├─ docs/
└─ gradle/libs.versions.toml
```

如果仓库初始化工具生成不同的规则文件路径，可保留 Firebase 默认路径，但 `firebase.json` 必须明确引用。

## 4. 架构约定

### 4.1 分层

```text
Compose Screen
    ↓ UI event / UI state
ViewModel
    ↓ use case
Domain Repository interface
    ↓
Repository implementation
    ↓
Firebase Auth / Firestore / Storage / Functions
```

- Compose 只消费不可变 `UiState`，不直接访问 Firebase SDK。
- ViewModel 负责页面状态与用户意图，不包含 Firestore 路径拼接。
- Repository 将 SDK 数据转成领域模型，并统一错误类型。
- Use case 只在有跨仓库协调或明确业务规则时创建，简单查询无需机械包装。

### 4.2 UI 状态

每个页面至少处理：

```kotlin
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(
        val data: T,
        val hasPendingWrites: Boolean = false
    ) : LoadState<T>
    data class Empty(val reason: EmptyReason) : LoadState<Nothing>
    data class Error(
        val kind: AppError,
        val canRetry: Boolean
    ) : LoadState<Nothing>
}
```

表单提交状态与页面加载状态分离，避免提交时把已有内容替换成全屏加载。

一次性事件（导航、Snackbar、系统设置）使用受控的事件流或导航状态，不把裸 `SharedFlow` 当作可靠消息队列。进程重建后需要继续的意图，例如待处理邀请，保存在 `SavedStateHandle` 或 DataStore。

### 4.3 错误模型

Firebase 异常在 data 层转换为有限的领域错误：

- `Unauthenticated`
- `PermissionDenied`
- `NetworkUnavailable`
- `NotFound`
- `AlreadyExists`
- `GroupFull`
- `InviteExpired`
- `Validation`
- `RateLimited`
- `Unknown`

UI 层只使用本地化错误文案，不显示 SDK 错误码和服务端内部信息。

### 4.4 时间

- 数据库存储 Firestore `Timestamp`，语义为 UTC 瞬间。
- 活动额外存 IANA 时区，例如 `Asia/Shanghai`。
- “完成日期”是日历日期，存 `YYYY-MM-DD` 字符串，并记录填写时区。
- 客户端使用 `java.time.Instant`、`LocalDate`、`ZoneId`。
- 禁止在业务层传递格式化后的日期字符串作为活动时间。
- 测试通过注入 `Clock` 固定当前时间。

## 5. Firebase 环境

### 5.1 环境隔离

至少创建两个 Firebase 项目：

- `nextlist-dev`：开发、Emulator 和内部测试。
- `nextlist-prod`：生产。

可增加 `nextlist-staging` 用于发布候选。Android 使用不同 `applicationIdSuffix` 和独立 `google-services.json`：

```text
debug   → com.example.nextlist.debug
release → com.example.nextlist
```

仓库提交规则、索引和 Functions 源码；不提交服务账号密钥、签名密钥或包含真实凭据的配置。

### 5.2 本地 Emulator

本地开发默认连接：

- Authentication Emulator。
- Firestore Emulator。
- Storage Emulator。
- Functions Emulator。

Debug 构建通过单一配置开关切换 Emulator，不把 `10.0.2.2` 散落在代码中。CI 中使用 Emulator 执行规则和 Functions 集成测试。

### 5.3 App Check

- Debug 使用 App Check debug provider。
- Release 使用 Play Integrity。
- 上线前先观察监控，再开启强制执行。
- Functions、Firestore 和 Storage 分别确认强制策略，避免遗漏。

## 6. Firestore 总体模型

顶层集合：

```text
users/{uid}
  devices/{deviceId}
  feed/{feedId}
  invitations/{invitationId}

groups/{groupId}
  members/{uid}
  ideas/{ideaId}
    reactions/{uid}
    comments/{commentId}
    rsvps/{uid}

groupInvites/{inviteId}
functionEvents/{eventId}
```

字段定义见 [Firestore 数据字典](./03-firestore-data-dictionary.md)。

设计原则：

- 组内业务数据放在 `groups/{groupId}` 下，便于规则判断和级联归档。
- 成员文档 ID 等于 `uid`；表态和参加状态文档 ID 也等于 `uid`，天然保证一人一条。
- 卡片需要的计数冗余在 `group` 和 `idea` 文档中，由 Cloud Functions 维护。
- 个人动态放在用户子集合，避免客户端拼接跨组动态。
- 函数幂等记录仅允许 Admin SDK 访问。
- 业务文档包含 `schemaVersion`，首版为 `1`。

### 6.1 为什么不使用大数组

不把成员、表态、评论或参加状态存进单个数组字段：

- 避免多人同时写同一文档产生冲突。
- 可以为单个成员设置权限。
- 避免文档持续膨胀并接近 Firestore 单文档限制。
- 明细列表可实时监听，聚合计数单独冗余。

### 6.2 快照字段

评论、想法和历史记录可保存创建时的昵称与头像路径快照：

```json
{
  "createdBy": "uid",
  "creatorSnapshot": {
    "nickname": "小林",
    "avatarPath": "users/uid/avatar/current.webp"
  }
}
```

快照保证成员退出后历史仍可读。用户修改昵称后，旧内容不要求批量回写；成员列表始终显示最新资料快照。

## 7. 写入边界

### 7.1 必须由 Callable Function 完成

以下操作涉及多文档一致性、人数上限或高权限，客户端调用 HTTPS Callable Function：

- `createGroup`
- `updateGroupName`
- `getOrCreateInvite`
- `rotateInvite`
- `sendDirectInvite`
- `previewInvite`
- `acceptInvite`
- `acceptDirectInvite`
- `leaveGroup`
- `removeMember`
- `transferAdmin`
- `dissolveGroup`

所有函数：

- 要求 Firebase Auth。
- 验证 App Check。
- 校验输入长度与枚举。
- 对关键操作使用 Firestore Transaction。
- 返回稳定业务错误码。
- 不信任客户端传入的角色、成员数、操作者昵称或时间。

### 7.2 客户端可直写

为了实时和离线体验，以下已实现操作在 Security Rules 保护下直写：

- 创建、编辑、软删除想法。
- 设置想法表态。
- 添加、软删除评论。
- 修改自己的用户资料和通知偏好。
- 注册/刷新自己的 FCM device token。

里程碑未开放的直写能力必须继续由 Rules 拒绝：M3 不允许安排/修改活动、设置
RSVP、标记完成或修改完成记录；M5 开始前也不开放动态或通知业务写入。后续实现
每项能力时，需要同时补齐 Android、Rules、Functions 和 Emulator 测试后再开启。

服务端触发器负责动态、推送、计数和清理，不作为客户端写入成功的前置条件。

### 7.3 写入时间

客户端创建/更新时间统一使用 `FieldValue.serverTimestamp()`。规则检查关键字段等于 `request.time`。UI 可在本地使用当前时间临时展示，但同步后以服务端时间为准。

## 8. Security Rules 设计

### 8.1 核心帮助函数

Firestore Rules 建议封装：

```javascript
function signedIn() {
  return request.auth != null;
}

function memberPath(groupId, uid) {
  return /databases/$(database)/documents/groups/$(groupId)/members/$(uid);
}

function groupPath(groupId) {
  return /databases/$(database)/documents/groups/$(groupId);
}

function isActiveMember(groupId) {
  return signedIn()
    && exists(groupPath(groupId))
    && get(groupPath(groupId)).data.status == "active"
    && exists(memberPath(groupId, request.auth.uid))
    && get(memberPath(groupId, request.auth.uid)).data.status == "active";
}

function isAdmin(groupId) {
  return isActiveMember(groupId)
    && get(memberPath(groupId, request.auth.uid)).data.role == "admin";
}

function unchanged(field) {
  return request.resource.data[field] == resource.data[field];
}
```

正式规则还需校验 `keys()`、字段类型、字符串长度、枚举值和状态迁移，不能只依赖以上身份检查。

### 8.2 权限摘要

| 路径 | 读 | 客户端写 |
| --- | --- | --- |
| `users/{uid}` | 本人；同组成员仅通过快照获取公开资料 | 仅本人可修改允许字段 |
| `users/{uid}/devices` | 本人 | 仅本人 |
| `users/{uid}/feed` | 本人 | 仅本人更新 `readAt`；创建/删除仅服务端 |
| `users/{uid}/invitations` | 本人 | 本人可更新查看/拒绝状态；创建仅服务端 |
| `groups/{groupId}` | 有效成员 | 禁止直写，走 Functions |
| `groups/{groupId}/members` | 有效成员；本人可读取自己的 membership | 禁止直写，走 Functions |
| `ideas` | 有效成员 | 按创建者、管理员及状态字段限制 |
| `reactions/{uid}` | 有效成员 | 仅该 uid 本人 |
| `comments` | 有效成员 | 成员可创建；作者/管理员可软删除 |
| `rsvps/{uid}` | 有效成员 | 仅该 uid 本人 |
| `groupInvites` | 禁止客户端直读 | 禁止 |
| `functionEvents` | 禁止 | 禁止 |

### 8.3 想法更新规则

更新按字段差异分三类检查：

1. **内容字段**：`title/category/note/media/locationOrLink`，仅创建者可改；管理员
   不能冒充创建者修改他人正文。
2. **活动字段**：`status/schedule/completion`，M3 全部不可改；M4 实现后才允许
   有效成员按合法状态迁移修改。
3. **删除字段**：`isDeleted/deletedAt/deletedBy`，创建者或管理员可改。

永远不可由客户端修改：

- `groupId`
- `createdBy`
- `createdAt`
- `reactionCounts`
- `rsvpCounts`
- `commentCount`
- `reminderClaimedAt`
- `reminderSentAt`
- `reminderSkippedReason`
- 任何服务端聚合字段

M3 中 `status/schedule/completion` 和 RSVP 聚合保持不可变；M4 开启状态流转时，
规则与测试至少覆盖：

- 新建必须为 `idea`。
- `idea → scheduled` 必须带合法 `schedule`。
- `scheduled → completed` 必须带合法 `completion`。
- MVP 禁止 `completed` 回退为其他状态，但允许有效成员修正 `completion` 中的可编辑字段。
- `idea` 状态不能带 RSVP。
- 想法表态只允许在 `idea/scheduled` 状态写入；RSVP 只允许在 `scheduled` 状态写入。

评论更新只能把 `isDeleted` 从 false 变为 true 并写删除审计字段，正文和作者字段不可变。表态/RSVP 仅本人可创建、更新或删除，路径内的 `groupId/ideaId/userId` 必须与实际路径及登录用户一致。

所有由客户端写入的 `createdBy/userId/lastModifiedBy` 必须与 `request.auth.uid` 对应；`creatorSnapshot/userSnapshot` 必须等于当前 membership 中的资料快照，防止成员冒用他人昵称或头像。

### 8.4 规则测试

每条重要允许路径至少有一个 allow 用例和一个 deny 用例：

- 非成员不能读组内数据。
- 被移除成员立即失去访问权限。
- 普通成员不能改组名和成员角色。
- 普通成员不能编辑他人想法正文。
- 普通成员可以安排他人想法。
- 用户不能替他人表态或设置参加状态。
- 用户不能伪造聚合计数、创建者或创建时间。
- 第 11 名成员只能由函数拒绝，规则不得提供绕过函数的直写路径。

CI 必须运行 Emulator Rules 测试后才允许合并。

## 9. Storage

### 9.1 路径

```text
users/{uid}/avatar/{fileId}.webp
groups/{groupId}/ideas/{ideaId}/cover/{fileId}.webp
groups/{groupId}/ideas/{ideaId}/completion/{fileId}.webp
```

Firestore 只存 `storagePath`、宽高、字节数和 MIME 类型，不存 Base64。

### 9.2 客户端处理

- 使用系统 Photo Picker，避免申请全量相册权限。
- 读取 EXIF 方向并纠正。
- 去除不必要 EXIF 元数据。
- 长边压缩至约 2048px。
- 优先输出 WebP/JPEG，目标不超过 2MB。
- 上传前生成 Firestore `ideaId`，保证路径稳定。
- 上传显示进度，可取消和重试。

### 9.3 Storage Rules

- 头像仅用户本人可写。
- 头像仅允许本人读写 `users/{uid}/avatar/{fileId}.webp`，WebP 硬限制为小于
  10 MB。
- 想法封面仅允许有效成员读写
  `groups/{groupId}/ideas/{ideaId}/cover/{fileId}.webp`。
- 想法封面必须是 `image/webp`，Storage Rules 和客户端输出都限制为不超过
  2 MB。
- 删除想法或替换图片后，由 Function 异步清理旧对象。

图片下载 URL 不写进公开日志。若使用长期 download token，需要在威胁模型中确认泄露风险；推荐保存 Storage path，由客户端按需解析并缓存。

## 10. 实时同步与离线

### 10.1 监听范围

| 页面 | 监听 |
| --- | --- |
| 小组列表 | 当前用户有效 membership 的 collection group 查询；对应 group 文档 |
| 小组详情 | 当前标签对应 ideas 查询 |
| 想法详情 | idea 文档、reactions、comments；已安排时加 rsvps |
| 动态 | 当前用户 feed，时间倒序 |
| 成员列表 | group members |

Repository 用 `callbackFlow` 包装监听器，并在 `awaitClose` 移除。ViewModel 使用：

```kotlin
stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = LoadState.Loading
)
```

不要在每次 Compose 重组时创建新 listener。

### 10.2 页面查询

- 小组想法：按状态查询；分类筛选作为可选等值条件。
- 已安排：`schedule.startAt` 升序。
- 已完成：`completion.completedOn` 降序。
- 评论：首屏最近 50 条，在 UI 中按正序显示；需要时向前分页。
- 动态：每页 30 条，按 `createdAt` 倒序。

监听实时第一页，较老数据使用游标分页。列表稳定 key 使用文档 ID。

### 10.3 Pending writes

监听快照时读取 `metadata.hasPendingWrites`：

- 本地已写、未同步：卡片显示轻量“等待同步”。
- 服务端拒绝：回滚乐观状态并给出可重试提示。
- 权限变化：停止监听并导航出受保护页面。

### 10.4 离线边界

允许离线排队：

- 纯文本想法。
- 表态。
- 评论。
- RSVP。
- 已缓存内容的正文修改。

要求联网：

- 登录、邮箱验证。
- 创建/加入/退出/解散小组。
- 邀请操作。
- 图片上传。
- 安排或修改活动。
- 标记完成或修改完成记录。
- 涉及严格服务端事务的管理操作。

有图片的想法先上传再写 Firestore，避免其他成员看到永久失效的图片路径。若上传完成而 Firestore 写入失败，把对象记录为 orphan，由定时清理函数回收。

安排和完成记录虽然由客户端直写 Firestore，但应使用 Transaction 读取最新状态和 `schedule.revision` 后再提交。发现版本变化时让用户先查看最新安排，避免离线队列静默覆盖其他成员的修改。

## 11. 聚合计数

以下字段由 Function 维护：

- group：`memberCount`、`ideaCount`、`scheduledCount`、`completedCount`。
- idea：`reactionCounts`、`rsvpCounts`、`commentCount`。

计数触发器必须幂等。建议使用事件 ID：

```text
functionEvents/{cloudEventId}
```

事务流程：

1. 检查事件记录是否存在。
2. 不存在则根据 before/after 差异更新计数。
3. 同一事务写入事件记录。
4. 已存在则直接返回。

定期任务可抽样重算计数并记录差异，作为运维校验。客户端展示时允许短暂最终一致，但详情成员列表是真实来源。

用户昵称或头像更新时，服务端查询该用户的有效 membership 并更新其中的 `profileSnapshot`。已发布的想法和评论保留创建时快照，不做全量历史回写。

成员退出或被移除后，后台函数按 `groupId + userId` 清理其 reaction 和 RSVP 文档，让聚合计数与随机筛选只反映有效成员。清理事件携带退出时间，只删除 `updatedAt` 不晚于退出时间的响应，避免用户重新加入后产生的新响应被旧任务误删；原有想法和评论保留。

## 12. 邀请

### 12.1 通用链接/邀请码

`getOrCreateInvite`：

1. 验证操作者是管理员且小组未满。
2. 有有效邀请时返回同一份凭据；没有时创建邀请。
3. token 使用服务端 Secret 对 `inviteId/groupId/expiresAt` 计算 HMAC-SHA-256 后生成，邀请码从独立的 HMAC 输出映射为 8 位易输入字符。
4. Firestore 只保存 token/code 的 SHA-256 哈希和 Secret 版本，不保存明文。
5. 因凭据可由服务端重新计算，管理员再次打开页面仍能看到同一链接和邀请码。
6. 有效期 7 天。

`rotateInvite` 将旧邀请置为 `revoked` 并创建新邀请。Secret 通过 Functions Secret Manager 管理；轮换 Secret 时必须保留仍在 7 天有效期内的旧版本。

`previewInvite` 接收 token、code 或当前用户自己的定向邀请 ID，只返回加入确认页所需的小组名、当前成员数和最多 5 个成员头像快照。非成员不能据此读取完整小组或成员数据。

`acceptInvite` 接收明文 token 或 code，在服务端计算哈希并查询：

1. 校验登录、验证状态、邀请状态和过期时间。
2. 事务内重新读取 group `memberCount`。
3. 已是成员则幂等返回成功。
4. 满 10 人则返回 `GROUP_FULL`。
5. 创建/恢复 member 文档并增加成员数。
6. 通用邀请增加使用次数；若请求来自定向邀请，则将该定向邀请标记为 accepted。

邀请码避免使用易混淆字符 `0/O/1/I`。生成时检查 `codeHash` 唯一性，碰撞则更换 invite ID 后重试；对连续失败尝试做速率限制。

### 12.2 定向邀请通知

为了满足“收到小组邀请”通知，同时不建设好友系统，管理员可选填一个已注册用户的邮箱进行定向邀请：

1. `sendDirectInvite` 接收邮箱。
2. 服务端标准化邮箱后使用 Firebase Admin Auth 查找已注册用户。
3. 无论是否找到用户，函数都返回不泄露注册状态的通用结果。
4. 找到时创建 `users/{uid}/invitations/{id}` 并发送 FCM。
5. 未找到时仅允许管理员复制通用邀请链接，不代发营销邮件。

应用内任何页面都不提供用户搜索和好友建议。查找结果、目标 uid 和收件人邮箱都不得写入普通日志。

定向邀请通过 `acceptDirectInvite(invitationId)` 接受。函数验证当前 uid 是收件人，再按与通用邀请相同的事务规则检查小组状态和 10 人上限；定向邀请不依赖当前通用邀请码，因此管理员轮换公开链接不会误伤已发出的定向邀请。

### 12.3 App Link

- Manifest 声明 `https` App Link 和开发环境 custom scheme。
- 生产域名部署 `/.well-known/assetlinks.json`。
- token 仅用于换取邀请信息，不直接授予成员权限。
- 未登录时把 token 加密或以应用私有存储保存，登录后继续确认。
- 日志、Analytics 和崩溃报告中不得记录完整 token。

## 13. 动态与通知

### 13.1 事件生成

Firestore Trigger、Callable Function 和定时函数共同处理：

| 业务事件 | 个人动态 | FCM |
| --- | --- | --- |
| 新想法 | 其他有效成员 | 无 |
| 首次安排 | 其他有效成员 | 开启“新安排”的成员 |
| 安排日期/时间变化 | 其他有效成员 | 开启“新安排”的成员 |
| 评论 | 仅想法创建者，排除本人 | 开启“新评论”的创建者 |
| 标记完成 | 其他有效成员 | 无 |
| 定向邀请 | 被邀请用户 | 开启“邀请”的用户 |
| 活动临近 | 不新增动态 | 开启“临近提醒”且未拒绝参加的成员 |

“新想法”和“完成”虽然进入动态，但不发送推送，符合低噪音原则。

首次安排或开始时间变化时，服务端触发器同时初始化或重置 `reminderClaimedAt/reminderSentAt/reminderSkippedReason`；若新开始时间距当前不足 30 分钟则写入 `too_late`。客户端不能修改这三个字段。

### 13.2 FCM token

每次获得/刷新 token 后写入：

```text
users/{uid}/devices/{deviceId}
```

设备文档包含平台、应用版本、token 和更新时间。发送遇到 unregistered/invalid token 时服务端删除该设备文档。退出登录时删除当前设备 token；服务端仍需容忍删除失败并依赖失效清理。

### 13.3 活动临近

Scheduled Function 每 5 分钟运行：

1. collection group 查询状态为 `scheduled`、`reminderSentAt == null`、开始时间晚于当前且不超过“当前时间 + 30 分钟”的 idea；安排时已标记 `too_late` 的内容排除。
2. 事务内再次检查 `schedule.scheduledAt/updatedAt`：若本次安排产生时距离开始不足 30 分钟则标记 `too_late`；否则抢占提醒并写入 `reminderClaimedAt`。已有抢占未超过 10 分钟时跳过，超时则允许重新抢占。
3. 读取有效成员、RSVP 和通知偏好。
4. 不发送给明确选择“不参加”的成员。
5. 发送完成写 `reminderSentAt`；部分失败记录错误并按受控策略重试，抢占超时机制保证函数异常退出后不会永久丢失提醒。

首次安排时若距离开始不足 30 分钟，直接设置 `reminderSkippedReason = "too_late"`。

如果业务要求严格准点，可在后续改为每个活动创建 Cloud Task；MVP 的 5 分钟粒度已经足够。

### 13.4 通知导航

FCM data payload 使用有限类型：

```json
{
  "type": "schedule_created",
  "groupId": "...",
  "ideaId": "..."
}
```

客户端统一由 `NotificationRouter` 解析：

- 已登录且有权限：进入目标。
- 未登录：登录后恢复目标。
- 小组或内容失效：回到小组列表并提示。
- 未知类型：安全忽略并进入应用首页。

不信任 payload 中的标题或权限信息，详情始终从 Firestore 读取。

## 14. 随机决定

### 14.1 查询

候选条件：

```text
group = 当前小组
status in [idea, scheduled]
isDeleted = false
category = 指定分类（可选）
reactionCounts.want >= 最少感兴趣人数
```

由于 Firestore 不提供直接随机抽样，客户端使用分页查询只收集候选文档的必要字段，再使用 Kotlin `Random` 均匀选择。

小组数据规模较小时可一次加载；实现仍应按 200 条/页遍历，避免隐含无限单次查询。随机页不需要为候选建立实时监听，用户点击“换一个”前可检查当前文档仍有效。

### 14.2 一致性

`reactionCounts.want` 由触发器维护，可能短暂落后于表态明细。这个最终一致性对轻量随机功能可接受。若候选已有安排，“安排这个”打开现有安排；若候选在点击前已被其他成员完成，安排写入会被规则拒绝，客户端提示后重新抽取。

## 15. 表单与图片写入流程

### 15.1 新增纯文本想法

1. 本地校验。
2. 预生成 idea document ID。
3. 写入 Firestore，使用 server timestamp。
4. 立即返回详情，显示 pending 状态。

### 15.2 新增带图片想法

1. 本地校验并预生成 idea ID。
2. 压缩图片。
3. 上传到稳定 Storage 路径。
4. 成功后创建 idea 文档并引用路径。
5. Firestore 写失败时保存可重试草稿并登记 orphan 路径。

### 15.3 替换图片

先上传新文件，再事务性更新 Firestore 路径，最后异步删除旧文件。禁止先删旧文件，以免更新失败后页面失图。

## 16. 导航

建议路由：

```text
auth/login
auth/register
auth/forgot-password
home/groups
home/feed
home/profile
group/create
group/join?token={token}
group/{groupId}?tab={tab}
group/{groupId}/members
group/{groupId}/settings
group/{groupId}/invite
group/{groupId}/idea/new
group/{groupId}/idea/{ideaId}
group/{groupId}/idea/{ideaId}/edit
group/{groupId}/idea/{ideaId}/schedule
group/{groupId}/idea/{ideaId}/complete
group/{groupId}/random
profile/edit
profile/notifications
about
```

路由参数只传 ID，不传整个可变对象。目标 ViewModel 从 SavedState 取 ID 并通过 Repository 获取最新数据。

## 17. 性能与成本

- group 卡片计数使用冗余字段，避免每张卡片执行 count 聚合。
- 列表只监听当前可见小组和页面，不在后台监听用户所有 ideas。
- 成员头像限制首屏数量，图片使用缩略尺寸和磁盘缓存。
- 评论、动态使用分页。
- Functions 批量发送 FCM，并限制并发。
- 为所有集合组查询建立明确索引。
- Crashlytics、日志和 Analytics 均不得记录用户内容或邀请 token。

建议在开发环境建立预算告警；生产上线前评估 Firestore 读取、Functions 调用、Storage 流量和 Cloud Scheduler 的计费要求。

## 18. 安全与隐私

### 18.1 最小权限

- Firebase Console 管理员权限不共享个人账号，使用团队角色。
- CI 使用 Workload Identity 或受控密钥，密钥不得进仓库。
- 客户端不包含 Admin SDK 凭据。
- 所有管理操作在服务端重新校验当前角色。

### 18.2 用户内容

- 评论、备注、图片、地址和链接视为私密组内内容。
- 通知锁屏不显示完整评论正文。
- 日志仅记录 document ID、错误类别和追踪 ID，避免记录正文。
- 图片 EXIF 位置信息在上传前移除。
- 正式发布前补齐隐私政策、数据保留和账号注销实现。

### 18.3 滥用控制

- 邀请码尝试、定向邀请、评论创建设置服务端/规则限流。
- 昵称、标题和评论按纯文本处理，不渲染 HTML。
- 链接只允许系统安全打开 `http/https`，打开前展示域名。
- 上传同时验证 MIME、大小和文件签名；不能只信扩展名。

## 19. 测试策略

### 19.1 单元测试

- 字段校验和中文错误文案映射。
- 状态迁移。
- 时区和活动临近时间窗。
- 随机筛选和“换一个”不重复逻辑。
- Repository DTO 与领域模型转换。
- ViewModel loading/content/error/pending 状态。

### 19.2 Firestore / Storage Rules 测试

使用 Emulator 和 Rules Unit Testing Library，覆盖第 8.4 节所有权限场景以及字段类型、长度、枚举和不可变字段。

### 19.3 Functions 集成测试

- 并发第 10/11 人加入。
- 重复接受同一邀请。
- 重复 CloudEvent 不重复计数和通知。
- 管理员转让与退出竞态。
- 评论通知排除评论者本人。
- 安排时间变化才触发第二次通知。
- 临近任务不通知“不参加”成员。
- 解散后所有业务函数拒绝操作。

### 19.4 Android UI 测试

关键 happy path：

1. 注册并创建小组。
2. 用邀请码加入。
3. 新增想法。
4. 另一用户实时看到并表态。
5. 评论和接收动态。
6. 安排活动并 RSVP。
7. 随机抽取并安排。
8. 标记完成并在历史中查看。

同时覆盖大字体、TalkBack 标签、旋转/进程重建、离线重连和通知深链。

### 19.5 手工多设备检查

至少使用两台设备或两个 Emulator：

- A 新增/编辑/删除，B 实时更新。
- A 被移除后正在打开的详情立即退出。
- 两人同时表态，计数最终正确。
- 两人同时安排同一想法，后提交者看到最新结果或明确冲突。
- 离线设备恢复后 pending write 正确同步。

## 20. 可观测性

- 每个 Callable Function 生成或透传 `requestId`。
- 结构化日志包含 function、result、duration、errorKind，不含用户正文。
- Functions 监控错误率、P95 延迟和重试次数。
- Crashlytics 按应用版本、页面和领域错误分类。
- 对聚合计数偏差、临近通知积压、无效 FCM token 数设置告警。

## 21. CI/CD

每个 Pull Request 至少执行：

1. Kotlin 格式和静态检查。
2. Android 单元测试。
3. Debug assemble。
4. Functions lint、typecheck、test。
5. Emulator Rules 测试。
6. 检查 `firestore.indexes.json` 和规则文件已纳入版本控制。

Release：

- 使用 Play App Signing。
- CI 从安全存储读取发布配置。
- 首先发布到 Internal testing。
- 通过 Crashlytics、核心流程和规则 smoke test 后再逐步放量。
- Firebase Rules、Indexes、Functions 与客户端版本应有可追踪的同一 release tag。

## 22. 开发里程碑

### M0：项目骨架

- 初始化 Android、Functions 和 Firebase 配置。
- 建立主题、导航、错误模型和 Emulator 环境。
- 配置 CI。

完成标准：空壳应用可在 Debug 连接 Emulator，CI 全绿。

### M1：账号与资料

- 邮箱注册、登录、重置、验证、退出。
- 昵称和头像。
- 登录态路由。

完成标准：新用户能完成资料并进入空的小组列表。

### M2：小组与邀请

- 创建、列表、成员、改名。
- 链接/邀请码和定向邀请。
- 加入、退出、移除、转让、解散。

完成标准：10 人上限和全部角色权限通过并发及 Rules 测试。

### M3：想法、表态与评论

- 三标签基础页面。
- 添加、编辑、删除和分类筛选。
- 表态、成员明细和评论。
- 实时监听、离线状态和聚合计数。

完成标准：两设备实时流程通过，普通表态不产生动态或推送。

### M4：安排、随机与完成

- 安排信息和 RSVP。
- 随机筛选。
- 完成记录和历史。

完成标准：完整核心链路可从创建想法走到完成。

### M5：动态与通知

- 个人动态。
- 四类通知偏好。
- FCM、临近任务和通知导航。

完成标准：通知矩阵逐项验证，无额外噪音通知。

### M6：发布加固

- 无障碍、性能、隐私、App Check。
- 崩溃和日志审查。
- 内测、修复和商店素材。

完成标准：满足本节发布清单和功能文稿验收标准。

## 23. Definition of Done

一项功能只有同时满足以下条件才算完成：

- 符合功能文稿及数据字典。
- 正常、空、加载、错误、无权限和离线状态均有处理。
- 权限不是只靠 UI 隐藏，Rules/Functions 有对应校验。
- 核心逻辑有自动化测试。
- 多设备实时行为经过验证。
- 没有把用户正文、邮箱或邀请 token 写入日志/埋点。
- 新增查询已提供索引。
- 新增字段已更新数据字典和 `schemaVersion`/迁移说明。
- Compose Preview 或截图覆盖主要视觉状态。
- 通过 CI、代码评审和对应验收标准。

## 24. 发布前清单

- [ ] 生产 Firebase 项目与包名确认。
- [ ] Release SHA-256、App Links 和 Play Integrity 配置完成。
- [ ] Firestore/Storage Rules 已部署并通过生产 smoke test。
- [ ] 必需复合索引状态为 Enabled。
- [ ] Functions 区域、时区、预算和告警已确认。
- [ ] FCM 四类通知与 Android 13+ 权限流程通过。
- [ ] 隐私政策、账号注销和数据保留方案已完成。
- [ ] 头像/图片上传、清理和内容大小限制通过。
- [ ] 两设备完整核心流程通过。
- [ ] 无障碍与大字体检查通过。
- [ ] Crashlytics 未记录用户私密内容。
- [ ] Internal testing 稳定后再逐步放量。
