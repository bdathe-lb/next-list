# 「下次」Firestore 数据字典

本文档定义 MVP 的 Firestore 结构。字段名是实现约定；若编码时调整，应同步更新本文档、Security Rules、索引和迁移策略。

## 1. 通用约定

### 1.1 类型

本文使用以下简写：

- `string`：Firestore String。
- `bool`：Boolean。
- `int`：Integer。
- `timestamp`：Firestore Timestamp。
- `map`：Map。
- `null`：字段允许显式为空；未注明可空的必填。

### 1.2 通用字段

主要业务文档包含：

```json
{
  "schemaVersion": 1,
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

约定：

- 客户端写时间使用 server timestamp。
- 软删除文档增加 `isDeleted/deletedAt/deletedBy`。
- ID 由 Firestore 自动 ID 生成，用户关联文档除外。
- 枚举在数据库中使用稳定英文值，中文仅在 UI 映射。
- 缺省与 `null` 需统一：可选展示字段首版统一使用 `null`，计数字段统一为 `0`。

### 1.3 快照对象

`UserSnapshot`：

```json
{
  "nickname": "小林",
  "avatarPath": "users/uid/avatar/file.webp"
}
```

`avatarPath` 可为 `null`。

## 2. `users/{uid}`

用途：用户私有资料与通知偏好。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `nickname` | string | ✓ | 2～20 字符 |
| `avatarPath` | string/null | ✓ | Storage path |
| `emailVerified` | bool | ✓ | 服务端或登录时同步快照 |
| `notificationPrefs` | map | ✓ | 四类推送开关 |
| `notificationPrefs.groupInvite` | bool | ✓ | 默认 true |
| `notificationPrefs.newSchedule` | bool | ✓ | 默认 true |
| `notificationPrefs.upcomingReminder` | bool | ✓ | 默认 true |
| `notificationPrefs.ideaComment` | bool | ✓ | 默认 true |
| `status` | string | ✓ | `active/deleted` |
| `createdAt` | timestamp | ✓ | 创建时间 |
| `updatedAt` | timestamp | ✓ | 更新时间 |
| `schemaVersion` | int | ✓ | 首版 1 |

客户端不得修改邮箱目录或其他用户资料。邮箱本身由 Firebase Auth 管理，不需要复制到公开用户文档。

### 2.1 `users/{uid}/devices/{deviceId}`

用途：FCM 设备 token。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `token` | string | ✓ | FCM token |
| `platform` | string | ✓ | 首版固定 `android` |
| `appVersion` | string | ✓ | 诊断使用 |
| `locale` | string | ✓ | 如 `zh-CN` |
| `updatedAt` | timestamp | ✓ | token 刷新时间 |
| `createdAt` | timestamp | ✓ | 首次注册时间 |

`deviceId` 使用应用安装范围随机 ID，不使用硬件标识。

### 2.2 `users/{uid}/feed/{feedId}`

用途：个人动态收件箱。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `type` | string | ✓ | 见动态枚举 |
| `groupId` | string | ✓ | 目标小组 |
| `groupNameSnapshot` | string | ✓ | 生成时组名 |
| `ideaId` | string/null | ✓ | 目标想法 |
| `ideaTitleSnapshot` | string/null | ✓ | 生成时标题 |
| `actorId` | string | ✓ | 操作者 uid |
| `actorSnapshot` | map | ✓ | `UserSnapshot` |
| `createdAt` | timestamp | ✓ | 事件时间 |
| `readAt` | timestamp/null | ✓ | 未读为 null |
| `expiresAt` | timestamp | ✓ | 默认 90 天 |
| `schemaVersion` | int | ✓ | 首版 1 |

动态枚举：

- `idea_created`
- `schedule_created`
- `schedule_updated`
- `idea_commented`
- `idea_completed`
- `group_invited`

`feedId` 建议由 `{cloudEventId}_{recipientUid}` 的哈希确定，保证触发器重试不重复创建。

### 2.3 `users/{uid}/invitations/{invitationId}`

用途：定向小组邀请，只对收件人可见。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `groupId` | string | ✓ | 小组 ID |
| `groupNameSnapshot` | string | ✓ | 小组名 |
| `invitedBy` | string | ✓ | 邀请人 uid |
| `inviterSnapshot` | map | ✓ | `UserSnapshot` |
| `status` | string | ✓ | `pending/accepted/declined/expired/revoked` |
| `expiresAt` | timestamp | ✓ | 过期时间 |
| `createdAt` | timestamp | ✓ | 创建时间 |
| `respondedAt` | timestamp/null | ✓ | 接受或拒绝时间 |
| `schemaVersion` | int | ✓ | 首版 1 |

文档不得包含明文 token、邀请码或收件人邮箱。

## 3. `groups/{groupId}`

用途：小组主体和列表聚合。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `name` | string | ✓ | 2～30 字符 |
| `adminId` | string | ✓ | 当前唯一管理员 uid |
| `status` | string | ✓ | `active/dissolved` |
| `memberCount` | int | ✓ | 有效成员数，0～10 |
| `ideaCount` | int | ✓ | `idea` 状态未删除数量 |
| `scheduledCount` | int | ✓ | `scheduled` 状态未删除数量 |
| `completedCount` | int | ✓ | `completed` 状态未删除数量 |
| `activeInviteId` | string/null | ✓ | 当前通用邀请，仅服务端修改 |
| `createdBy` | string | ✓ | 创建人 uid |
| `lastActivityAt` | timestamp | ✓ | 用于小组列表排序 |
| `createdAt` | timestamp | ✓ | 创建时间 |
| `updatedAt` | timestamp | ✓ | 更新时间 |
| `dissolvedAt` | timestamp/null | ✓ | 解散时间 |
| `schemaVersion` | int | ✓ | 首版 1 |

计数和 `adminId/status` 仅允许服务端修改。

### 3.1 `groups/{groupId}/members/{uid}`

用途：成员身份与权限来源。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `userId` | string | ✓ | 与文档 ID 相同 |
| `groupId` | string | ✓ | 冗余，供 collection group 查询 |
| `role` | string | ✓ | `admin/member` |
| `status` | string | ✓ | `active/left/removed` |
| `profileSnapshot` | map | ✓ | `UserSnapshot` |
| `joinedAt` | timestamp | ✓ | 首次加入时间 |
| `updatedAt` | timestamp | ✓ | 状态或资料快照更新时间 |
| `leftAt` | timestamp/null | ✓ | 主动退出时间 |
| `removedAt` | timestamp/null | ✓ | 被移除时间 |
| `removedBy` | string/null | ✓ | 操作者 uid |
| `schemaVersion` | int | ✓ | 首版 1 |

退出后保留文档并修改 `status`。重新加入时复用文档，更新状态和快照，不删除历史内容。

## 4. `groups/{groupId}/ideas/{ideaId}`

用途：想法主体、安排和完成记录。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `groupId` | string | ✓ | 冗余，供 collection group 查询 |
| `title` | string | ✓ | 1～80 字符 |
| `category` | string | ✓ | 分类枚举 |
| `note` | string/null | ✓ | 最多 1,000 字符 |
| `media` | map/null | ✓ | 想法图片 |
| `locationOrLink` | string/null | ✓ | 最多 500 字符 |
| `createdBy` | string | ✓ | 创建人 uid |
| `creatorSnapshot` | map | ✓ | `UserSnapshot` |
| `status` | string | ✓ | `idea/scheduled/completed` |
| `schedule` | map/null | ✓ | 已安排信息 |
| `completion` | map/null | ✓ | 完成信息 |
| `reactionCounts` | map | ✓ | 服务端聚合 |
| `reactionCounts.want` | int | ✓ | 想参加 |
| `reactionCounts.ok` | int | ✓ | 都可以 |
| `reactionCounts.notInterested` | int | ✓ | 不感兴趣 |
| `rsvpCounts` | map | ✓ | 服务端聚合 |
| `rsvpCounts.going` | int | ✓ | 参加 |
| `rsvpCounts.maybe` | int | ✓ | 待定 |
| `rsvpCounts.notGoing` | int | ✓ | 不参加 |
| `commentCount` | int | ✓ | 未删除评论数 |
| `reminderClaimedAt` | timestamp/null | ✓ | 临近任务抢占 |
| `reminderSentAt` | timestamp/null | ✓ | 已发送时间 |
| `reminderSkippedReason` | string/null | ✓ | 如 `too_late` |
| `lastModifiedBy` | string | ✓ | 最后业务修改人 |
| `createdAt` | timestamp | ✓ | 创建时间 |
| `updatedAt` | timestamp | ✓ | 更新时间 |
| `isDeleted` | bool | ✓ | 默认 false |
| `deletedAt` | timestamp/null | ✓ | 软删除时间 |
| `deletedBy` | string/null | ✓ | 删除者 uid |
| `schemaVersion` | int | ✓ | 首版 1 |

分类枚举：

- `place`
- `restaurant`
- `ktv_song`
- `movie`
- `activity`
- `other`

### 4.1 `media`

```json
{
  "storagePath": "groups/gid/ideas/iid/cover/file.webp",
  "mimeType": "image/webp",
  "width": 1600,
  "height": 1200,
  "byteSize": 456789
}
```

### 4.2 `schedule`

```json
{
  "startAt": "timestamp",
  "timezone": "Asia/Shanghai",
  "meetingPoint": "地铁 2 号口",
  "note": "提前十分钟集合",
  "scheduledBy": "uid",
  "schedulerSnapshot": {
    "nickname": "小林",
    "avatarPath": null
  },
  "scheduledAt": "timestamp",
  "updatedBy": "uid",
  "updatedAt": "timestamp",
  "revision": 1
}
```

- `meetingPoint` 最多 200 字符，可为 null。
- `note` 最多 500 字符，可为 null。
- `revision` 每次修改加 1，可用于冲突提示。

### 4.3 `completion`

```json
{
  "completedOn": "2026-07-23",
  "timezone": "Asia/Shanghai",
  "photo": {
    "storagePath": "groups/gid/ideas/iid/completion/file.webp",
    "mimeType": "image/webp",
    "width": 1600,
    "height": 1200,
    "byteSize": 456789
  },
  "review": "天气很好，下次还来。",
  "rating": 5,
  "completedBy": "uid",
  "completerSnapshot": {
    "nickname": "小林",
    "avatarPath": null
  },
  "completedAt": "timestamp",
  "updatedBy": "uid",
  "updatedAt": "timestamp"
}
```

- `photo/review/rating` 均可为 null。
- `review` 最多 500 字符。
- `rating` 仅允许整数 1～5 或 null。

### 4.4 `groups/{groupId}/ideas/{ideaId}/reactions/{uid}`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `groupId` | string | ✓ | 与父级小组 ID 相同 |
| `ideaId` | string | ✓ | 与父级想法 ID 相同 |
| `userId` | string | ✓ | 与文档 ID 相同 |
| `value` | string | ✓ | `want/ok/not_interested` |
| `userSnapshot` | map | ✓ | `UserSnapshot` |
| `createdAt` | timestamp | ✓ | 首次表态 |
| `updatedAt` | timestamp | ✓ | 最近修改 |
| `schemaVersion` | int | ✓ | 首版 1 |

移除表态时删除该文档。只有本人可写。

### 4.5 `groups/{groupId}/ideas/{ideaId}/comments/{commentId}`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `content` | string | ✓ | 1～500 字符 |
| `createdBy` | string | ✓ | 评论人 uid |
| `creatorSnapshot` | map | ✓ | `UserSnapshot` |
| `createdAt` | timestamp | ✓ | 评论时间 |
| `isDeleted` | bool | ✓ | 默认 false |
| `deletedAt` | timestamp/null | ✓ | 删除时间 |
| `deletedBy` | string/null | ✓ | 删除者 uid |
| `schemaVersion` | int | ✓ | 首版 1 |

评论不支持编辑，因此无 `updatedAt`。软删除后 UI 不展示正文。

### 4.6 `groups/{groupId}/ideas/{ideaId}/rsvps/{uid}`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `groupId` | string | ✓ | 与父级小组 ID 相同 |
| `ideaId` | string | ✓ | 与父级想法 ID 相同 |
| `userId` | string | ✓ | 与文档 ID 相同 |
| `value` | string | ✓ | `going/maybe/not_going` |
| `scheduleRevision` | int | ✓ | 表态时对应的安排版本 |
| `userSnapshot` | map | ✓ | `UserSnapshot` |
| `createdAt` | timestamp | ✓ | 首次选择 |
| `updatedAt` | timestamp | ✓ | 最近修改 |
| `schemaVersion` | int | ✓ | 首版 1 |

修改安排时不自动清空 RSVP；UI 可通过 `scheduleRevision < schedule.revision` 标记“安排已变化，请确认”。MVP 默认保留原选择，日期或时间改变时通知成员重新确认。

## 5. `groupInvites/{inviteId}`

用途：通用邀请凭据，仅服务端可读写。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `groupId` | string | ✓ | 小组 ID |
| `tokenHash` | string | ✓ | SHA-256 |
| `codeHash` | string | ✓ | 标准化邀请码 SHA-256 |
| `secretVersion` | string | ✓ | 用于重新计算凭据的 Secret 版本 |
| `status` | string | ✓ | `active/revoked/expired` |
| `createdBy` | string | ✓ | 管理员 uid |
| `expiresAt` | timestamp | ✓ | 默认 7 天 |
| `useCount` | int | ✓ | 成功加入次数 |
| `createdAt` | timestamp | ✓ | 创建时间 |
| `revokedAt` | timestamp/null | ✓ | 撤销时间 |
| `schemaVersion` | int | ✓ | 首版 1 |

数据库绝不保存明文 token/code。服务端用 HMAC 和 `secretVersion` 重新计算凭据，因此管理员可重复打开分享页；Secret 必须通过受控 Secret Manager 管理。

## 6. `functionEvents/{eventId}`

用途：Cloud Functions 幂等和审计。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | :---: | --- |
| `functionName` | string | ✓ | 处理函数 |
| `sourcePath` | string | ✓ | 触发文档路径，可脱敏 |
| `processedAt` | timestamp | ✓ | 完成时间 |
| `expiresAt` | timestamp | ✓ | TTL 清理时间 |
| `schemaVersion` | int | ✓ | 首版 1 |

配置 Firestore TTL 自动删除过期幂等记录。保留时长需覆盖 CloudEvent 最大重试窗口，建议至少 7 天。

## 7. 查询与索引

实际索引以 Emulator 报错和最终查询实现为准，初始至少准备：

| 范围 | 过滤 | 排序 |
| --- | --- | --- |
| collection group `members` | `userId ==`, `status ==` | `updatedAt desc` |
| group `ideas` | `isDeleted ==`, `status ==` | `createdAt desc` |
| group `ideas` | `isDeleted ==`, `status ==`, `category ==` | `createdAt desc` |
| group `ideas` | `isDeleted ==`, `status ==` | `schedule.startAt asc` |
| group `ideas` | `isDeleted ==`, `status ==`, `category ==` | `schedule.startAt asc` |
| group `ideas` | `isDeleted ==`, `status ==` | `completion.completedOn desc` |
| group `ideas` | `isDeleted ==`, `status ==`, optional `category ==`, `reactionCounts.want >=` | `reactionCounts.want asc` 或文档 ID |
| collection group `ideas` | `isDeleted ==`, `status ==`, `reminderSentAt ==` | `schedule.startAt asc` |
| collection group `reactions` | `groupId ==`, `userId ==` | `updatedAt asc` |
| collection group `rsvps` | `groupId ==`, `userId ==` | `updatedAt asc` |
| idea `comments` | `isDeleted ==` | `createdAt desc` |
| user `feed` | — | `createdAt desc` |
| user `feed` | `readAt == null` | `createdAt desc` |
| user `invitations` | `status ==` | `createdAt desc` |
| `groupInvites` | `tokenHash ==`, `status ==` | — |
| `groupInvites` | `codeHash ==`, `status ==` | — |

注意：

- 不对只包含等值且 Firestore 自动索引已满足的查询重复建索引。
- 随机查询使用 `>=` 后，首个 `orderBy` 必须与范围字段兼容。
- 临近提醒 collection group 查询要求 idea 文档冗余 `groupId`，便于服务端后续读取小组。
- 每次新增查询都应在 `firestore.indexes.json` 中审查并提交。
- M3 实际索引仅开放三状态基础列表、想法分类、最近评论，以及成员退出时清理
  reaction/RSVP 所需查询；分类后的已安排/已完成、随机筛选和临近提醒等 M4/M5
  复合索引在对应查询实现前不提前创建。

## 8. 数据一致性

### 9.1 强一致操作

以下操作在服务端 Transaction 中完成：

- 创建小组及首位管理员。
- 接受邀请与 10 人上限。
- 移除/退出与成员计数。
- 管理员转让。
- 解散小组。

### 9.2 最终一致字段

以下聚合允许短暂延迟：

- 小组状态计数。
- 表态计数。
- RSVP 计数。
- 评论数。
- 动态和推送生成。

详情子集合是真实来源；聚合可通过定期任务重算。

### 9.3 状态变化与计数

idea 变化时按 before/after 计算桶：

```text
isDeleted = true → 不计入任何桶
status = idea → ideaCount
status = scheduled → scheduledCount
status = completed → completedCount
```

只做桶之间的 `-1/+1`，不得对每次普通字段编辑改变计数。

### 9.4 解散

Firestore 删除父文档不会自动删除子集合。解散流程：

1. Transaction 把 group 标记 `dissolved`。
2. 所有规则立即通过 group status 拒绝业务访问。
3. 后台函数递归归档或删除子集合和 Storage 文件。
4. 清理任务可重试并记录进度。

默认先逻辑解散，避免在用户请求线程内执行大规模递归删除。

## 9. 迁移

- 每种文档带 `schemaVersion`。
- 新增可选字段优先在读取层提供默认值，避免立即全量迁移。
- 修改字段语义或索引前先部署向后兼容读取，再回填，再切换写入。
- 回填脚本必须支持 dry-run、分页、断点续跑和明确项目 ID。
- 生产迁移前在 Emulator 或 staging 数据副本验证。
- 不允许客户端在读取时顺手迁移其他用户文档。
