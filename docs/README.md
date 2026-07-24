# 下次（NextList）文档索引

「下次」是一款面向朋友、情侣、室友及其他小团体的共享灵感清单 Android 应用。

本文档集用于约束首个可发布版本（MVP）的产品范围、交互设计与技术实现。后续开发若需要改变范围、数据模型或权限规则，应先修改对应文档，再开始编码。

## 文档

1. [功能文稿](./01-product-spec.md)
   - 产品定位与设计原则
   - 用户角色和权限
   - 信息架构、页面与完整流程
   - 状态、异常、文案和验收标准
   - 视觉与交互规范
2. [开发文档](./02-development-guide.md)
   - Android 技术栈与工程结构
   - Firebase 服务与环境配置
   - Firestore 数据模型、索引和安全规则
   - 实时同步、通知、图片与邀请实现
   - 测试、发布、里程碑和完成定义
3. [Firestore 数据字典](./03-firestore-data-dictionary.md)
   - 集合与字段定义
   - 枚举、时间和冗余字段约定
   - 查询、索引和数据一致性说明
4. [本地环境清单与卸载指南](./04-local-environment-inventory-and-cleanup.md)
   - 已安装软件、SDK、运行时和模拟器
   - Android、Gradle、Node.js 与 Firebase 依赖版本
   - 项目生成内容、共享缓存和临时工具链路径
   - 安全卸载顺序与卸载后验证

## 已确定的 MVP 决策

- 平台：仅 Android。
- 客户端：Kotlin + Jetpack Compose，单 Activity。
- 登录：首版使用邮箱登录，不接入微信登录。
- 后端：Firebase Authentication、Cloud Firestore、Cloud Storage、Cloud Functions、Firebase Cloud Messaging。
- 小组上限：每组最多 10 名有效成员。
- 小组角色：管理员、普通成员。每组仅有一名管理员。
- 想法状态：想法、已安排、已完成。
- 数据同步：小组内核心数据使用 Firestore 实时监听。
- 删除策略：业务数据默认软删除；解散小组由服务端标记后异步清理。
- 时间存储：Firestore `Timestamp` 统一存 UTC，同时保存活动时区用于展示。
- 图片限制：想法图片和完成照片均至多一张。

## 明确不在 MVP 内

好友、私聊、群聊、公开社区、地图探索、AI 推荐、费用分摊、独立投票、视频、排行榜、积分、支付、广告、Web 和 iOS 均不进入首版。

## 建议阅读顺序

产品、设计和测试先阅读功能文稿；Android 与后端开发先阅读开发文档，再按需查阅数据字典。开始实现前，团队应共同确认开发文档中的“待产品确认项”。
