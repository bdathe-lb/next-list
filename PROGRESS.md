# NextList 开发进度

最后更新：2026-07-25

## 当前阶段

**M6：发布加固（已关闭）**

M0～M6 已关闭。M6 实现提交已推送至
[PR #3](https://github.com/bdathe-lb/next-list/pull/3)；本地全量验收和远端
CI（Android + Functions and Firebase Rules 两个 Job）均已通过。真实 Firebase
环境部署与隐私政策正式文案仍作为外部配置事项跟踪。

## M6 已完成

### 可观测性与账号注销

- [x] Functions 新增结构化可观测性模块：`wrapCallable` 统一记录
  `function_completed / function_rejected`，带严重级别、耗时和 requestId；
  `createHealthPayload` 输出稳定的模拟器健康契约。单元测试 19/19 通过。
- [x] 新增 `deleteAccount` Callable：匿名化用户资料与设备、清理有效
  membership（唯一管理员的小组会阻断并返回 `ADMIN_CANNOT_LEAVE`）、删除
  Firebase Auth 账号；使用 requestId 保证幂等。集成测试覆盖管理员阻断、
  幂等重放与完整删除三种场景，6/6 通过。
- [x] Android 侧 `AuthRepository.deleteAccount` 调用 Callable 后登出；
  `DeleteAccountViewModel` 提供二次确认弹层，区分"唯一管理员阻断"与
  一般错误，注销前尽力清理设备 token。"我的"页新增危险操作入口。

### 无障碍、隐私与发布构建

- [x] 集成 Crashlytics 依赖与插件（随 `google-services.json` 条件启用）；
  仅在已配置且非模拟器构建中开启采集，调试与模拟器运行保持静默。
- [x] 开启 release R8 混淆，补充 Firebase、Hilt、协程、Coil 与 Firestore
  反序列化数据类的 keep 规则；`assembleRelease` 通过并上传映射文件。
- [x] "我的"页新增"隐私政策"（打开外部链接）与"开源许可"入口；新增
  `OssLicensesScreen`，每条声明带 TalkBack 语义说明。

## M5 已完成

### 个人动态与导航

- [x] `users/{uid}/feed/{feedId}` 按 `createdAt` 倒序实时监听第一页，每页 30 条，
  使用 `createdAt + documentId` 游标加载更早数据；支持加载、空、失败、重试、
  下拉刷新、离线缓存和实时插入去重。
- [x] 六类动态均有中文图标、文案、小组、时间和 TalkBack 未读说明；单条点击先
  使用服务端时间标记已读，全部标记已读会查询并批量处理所有未读文档，而不只处理
  当前页。
- [x] Feed 只由 Functions 写入；ID 使用 CloudEvent、事件类型和收件人 uid 的摘要，
  重试不重复。`expiresAt` 固定为事件时间后 90 天并启用 Firestore TTL。
- [x] Feed 与推送共用有限 ID 目标；想法、小组或邀请失效时返回安全页面并显示中文
  提示。通知目标保存在 DataStore，未登录可恢复，最近 50 个 message ID 防止重复
  点击再次执行导航。

### 偏好、设备与系统通知

- [x] “我的 → 通知设置”实现 `groupInvite/newSchedule/upcomingReminder/
  ideaComment` 四类开关，默认均为 true；包含加载、保存中、成功、失败重试、
  Firestore pending 与离线缓存状态。
- [x] Android 13+ 使用 `POST_NOTIFICATIONS` 运行时权限；拒绝后应用与 Feed 正常，
  页面持续提供重新申请和打开系统通知设置入口。
- [x] 安装范围 `UUID` 作为 `deviceId`，登录和 token 刷新注册
  `token/platform/appVersion/locale/createdAt/updatedAt`；退出清理限制在 1.5 秒，
  失败或离线不阻塞 Firebase Auth 退出。
- [x] `FirebaseMessagingService` 处理前台/后台 data message，系统通知只按受限
  类型生成固定安全文案；payload 不信任标题、昵称、权限或正文，评论全文不会进入
  payload、通知正文或日志。

### Functions、提醒与安全

- [x] 新想法和完成只写 Feed；首次安排及任意安排 revision 写 Feed，但仅首次或
  `schedule.startAt` 实际变化发送安排推送；评论仅写给想法创建者并排除本人；
  定向邀请仅写目标用户。表态、RSVP、随机和资料修改没有通知触发器。
- [x] 推送先按当前有效 membership、四类偏好和合法 Android device 计算收件设备；
  每用户只读取最近更新的 20 台设备，FCM 每批最多 500 条；每设备投递带 10 分钟
  租约与成功记录。部分失败只重试失败设备，无效 token 被删除，原业务写入不因
  Feed/FCM 失败回滚。
- [x] 每 5 分钟 Scheduled Function 查询未来 30 分钟的 `scheduled` ideas；事务内
  复查删除状态、开始时间、revision、提醒字段和抢占租约。不通知 `not_going`，
  尊重偏好；按安排实际保存的 `schedule.updatedAt` 判断不足 30 分钟并标记
  `too_late`，不会因触发器启动延迟误判。
- [x] Firestore Rules 将 Feed 限制为本人读取与一次合法 `readAt` 更新，严格校验
  device 字段、平台、时间和所有权，通知偏好只允许四个布尔值；所有客户端仍不能
  写提醒、聚合、Feed 或投递记录。
- [x] 只新增 M5 临近提醒所需 collection-group 复合索引，并为 Feed、幂等事件和
  每设备投递的 `expiresAt` 启用 TTL；未加入 M6 预留索引。

## M4 已完成

### 安排与列表

- [x] 想法详情可创建安排；所有活跃成员可修改已安排内容，日期、时间、时区必填，
  集合地点和备注按 Unicode 长度校验。
- [x] 安排写入使用 Firestore Transaction 和 `schedule.revision`；首次为 1，修改
  原子加 1，并保留首次安排人与时间。过期 revision 返回明确冲突并载入服务器最新
  内容，不静默覆盖。
- [x] 安排与完成必须联网保存并显示明确状态；提交期间禁用重复操作，普通内容编辑
  与 RSVP 仍可使用 Firestore 离线队列。
- [x] “已安排”按 `schedule.startAt` 升序，显示本地时区时间、集合地点、可信 RSVP
  聚合和过期“待完成”；“已完成”按 `completion.completedOn` 倒序，显示日期、评分
  和可选缩略图。
- [x] 详情页保留原安排审计；完成后继续展示最终 RSVP 分组与完成记录审计。

### RSVP 与随机决定

- [x] 活跃成员可选择、切换或取消 `going / maybe / not_going`；文档绑定本人 uid、
  当前成员快照和选择时的 `scheduleRevision`。
- [x] 修改安排不清空 RSVP；旧 revision 在详情和成员明细中显示“安排已变化，请
  确认”，用户再次选择可刷新 revision。
- [x] Functions 从真实 RSVP 子集合事务重算三类人数，并用 `functionEvents` 保证
  重复 CloudEvent 幂等；成员退出或被移除后清理其 RSVP 并回算计数。
- [x] 随机页支持全部/分类及最少“想参加”人数筛选；分别分页读取 `idea` 与
  `scheduled` 候选，每页最多 200 条，在内存中使用 Kotlin `Random` 等概率抽取。
- [x] “换一个”在存在多个候选时排除当前结果；打开安排前从服务器复查结果，若已
  完成或删除则自动移除并重抽，离线时给出可恢复错误。

### 完成、安全与清理

- [x] 已安排内容可标记完成；默认当天，支持合法时区、可选单张完成照片、500 字
  评价和 1～5 整数评分，已完成内容可继续修改完成记录。
- [x] 完成写入使用 Transaction，原子切换状态并保留首次完成人/时间；修改只更新
  修改审计。完成照片复用 WebP/EXIF/2 MB 处理流程，写入失败清理新上传对象并保留
  表单，替换或移除旧照片由客户端与 Function 安全清理。
- [x] Firestore Rules 精确校验安排、完成、RSVP 字段形状、枚举、快照、服务端时间、
  状态转换、revision 与聚合不可变性；Storage Rules 只允许活跃成员写精确完成照片
  路径、WebP 和不超过 2 MB 的对象。
- [x] 新增 M4 列表与随机分页所需索引；M4 不创建个人动态、不发送 FCM，也未实现
  M5 临近提醒。

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
- [x] M3 不创建个人动态，也没有 FCM 发送入口；M3 关闭时 M4/M5 写入仍由 Rules
  拒绝。

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
- [x] M3 关闭时仅加入该阶段查询和成员退出清理所需复合索引；安排、RSVP 和完成
  写入当时仍为安全占位。

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
| Android 单元测试 | 通过 | 65/65；新增 Feed 实时/分页/已读、六类文案目标、偏好失败重试、非法 payload、登录设备注册和退出清理失败不阻塞 |
| Android Lint | 通过 | 0 errors；仅保留 targetSdk 与依赖版本提示 |
| Debug APK | 通过 | `assembleDebug` |
| Compose / Android 设备测试 | 通过 | 28/28，在两个只读 Pixel_9_Pro API 37 实例上并行执行，共 56 次通过 |
| Functions lint/typecheck | 通过 | ESLint + TypeScript，0 errors |
| Functions 单元测试 | 通过 | 16/16；新增确定性 ID、无效 token 分类、提醒窗口、租约和 `too_late` 精确边界 |
| Functions Emulator 集成 | 通过 | 5/5；新增完整 Feed 类型、偏好、部分失败重试、无效 token、临近提醒与 `too_late` |
| Firestore Rules | 通过 | 35/35 allow/deny 用例 |
| Storage Rules | 通过 | 11/11 allow/deny 用例 |
| Release APK | 通过 | `assembleRelease`；Release 不连接 Emulator |
| API 37 M5 双设备自动化 | 通过 | 两个只读 Pixel_9_Pro 实例并行执行完整 28 条设备用例 |
| M5 真实两账号/FCM 烟测 | 待外部环境 | Emulator 无真实 FCM，且本轮未配置真实 Firebase 项目；不得记为生产通过 |
| GitHub Actions | 通过 | [CI #30102400540](https://github.com/bdathe-lb/next-list/actions/runs/30102400540)：Android 2m41s；Functions and Firebase Rules 1m29s |

### API 37 M5 设备验证明细

设备：两个并行只读 `Pixel_9_Pro` AVD，API 37。

- 两台设备各执行 28 条 Compose/Android 测试且 0 失败，共 56 次通过；M0～M4 的
  22 条既有设备基线全部保留。
- 新增 6 条 M5 用例覆盖 Feed 未读文案、小组与分页操作、空/离线状态、系统通知
  权限关闭说明、权限允许状态、四类开关 TalkBack 标签、系统设置入口、2 倍大字体
  滚动可达，以及待处理通知跨 Repository 重建恢复且消费后不重放。
- Functions Emulator 的 5 个集成场景另行验证两成员事件矩阵、成员移除/重新加入、
  Feed 收件人、推送偏好、部分失败只重试失败设备、无效 token 清理、`not_going`、
  `too_late` 和重复提醒。
- 本轮没有把以上自动化测试冒充为真实 FCM 或完整手工两账号 smoke。真实系统托盘
  投递、Google FCM token、Cloud Scheduler 自动唤起及通知点击后的端到端 Firestore
  权限复查，仍需真实 Firebase 开发项目。

### API 37 M4 烟测明细

设备：两个并行只读 `Pixel_9_Pro` 实例，API 37；后端：同一套 `demo-nextlist`
Auth、Firestore、Functions 与 Storage Emulator。

- A 与 B 使用两个已验证虚拟账号进入同一 2 人小组；A 安排想法后，B 的“想法”
  标签实时移除该卡片，“已安排”显示时间、集合地点和 0/0 RSVP。
- A 选择“参加”、B 选择“待定”，两端均实时显示 1 人参加、1 人待定和 0 人未选择。
- A 把时间从 18:30 修改为 19:15 后，两端均显示第 2 版与“安排已变化，请确认”。
- A、B 同时载入第 2 版并分别改为 20:00/20:30；A 先保存为第 3 版，B 后提交时
  显示明确冲突，并自动载入 20:00 最新安排，没有静默覆盖。
- 随机决定可在未完成想法和已安排内容间抽取；“换一个”在两个候选间不重复，
  已安排结果的“安排这个”载入现有 revision。烟测发现并修复了返回随机页时一次性
  导航未消费导致循环进入安排页的问题，并增加单元回归测试。
- B 标记完成并保存评价与 4 星，A 实时看到卡片从“已安排”消失并进入“已完成”；
  B 再改为 5 星后，A 的完成卡片实时更新。
- 最终 Emulator 数据为小组 `idea/scheduled/completed = 1/0/1`、安排 revision 3、
  RSVP `going/maybe/notGoing = 1/1/0`；两位用户的 `feed` 和 `notifications`
  子集合均为 0。
- 除真实联动流程外，每台还执行 22 条 Compose/Android 设备用例且 0 失败，共
  44 次通过。

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
M4 远端 CI：[CI #30094931847](https://github.com/bdathe-lb/next-list/actions/runs/30094931847)，
提交 `e5b3a35` 的 Android 与 Functions and Firebase Rules 两个 Job 均通过。
合并到 `main` 后的 [CI #30095825232](https://github.com/bdathe-lb/next-list/actions/runs/30095825232)
也已通过。
M5 远端 CI：[CI #30102400540](https://github.com/bdathe-lb/next-list/actions/runs/30102400540)，
实现提交 `6a8040c` 的 Android 与 Functions and Firebase Rules 两个 Job 均通过；
M5 随后正式关闭。

## 当前限制与外部配置

- 本地使用仓库提供的示例配置和 `demo-nextlist` Emulator 完成开发与验收。
- 尚未创建或接入真实 Firebase 开发/生产项目；真实 `google-services.json`、
  `NEXTLIST_INVITE_SECRET`、Play Integrity 注册和生产 Rules/App Check smoke test
  仍是外部配置事项。
- 邮件发件模板/域名、动态链接落地页、生产监控、隐私政策和账号注销按后续发布
  加固计划处理。
- M3～M5 的 Firestore/Storage Rules、复合索引和 Functions 尚未部署到真实项目；
  生产环境还需部署后执行 App Check、Rules、图片上传和两设备预发布 smoke test。
- Firebase Emulator 不提供真实 FCM 投递；Functions 集成测试使用可替换发送器验证
  payload、偏好、幂等、部分失败和 token 清理。未运行 Pub/Sub Emulator 时 Scheduled
  Function 不会自动按 5 分钟触发，集成测试直接调用同一提醒处理器。
- 真实 Android 通知权限、系统托盘、token 刷新、通知点击冷启动与 Cloud Scheduler
  仍需在真实 Firebase 开发项目完成两账号预发布 smoke。

## 下一步

1. 如具备真实 Firebase 项目，部署 M3～M5 Rules、索引和 Functions，配置 Play
   Integrity，并执行生产预发布 App Check / Rules / Storage 双设备 smoke test。
