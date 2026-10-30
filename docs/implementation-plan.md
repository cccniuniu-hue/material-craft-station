# 素材工坊实施计划

开始：2026-10-30 16:31（Asia/Shanghai）。用户授权按完整笔试要求实现。

## 架构与取舍

Next.js App Router 负责交互与同源代理；Spring Boot + MyBatis 负责全部业务；MySQL 8.4 持久化。Java 有界线程池按素材检查、模拟生成、结果整理顺序执行，提交在数据库提交后调度。轮询只读状态。无需 Redis 或消息队列。身份为服务端签发随机 Bearer token 的演示用户，浏览器保存多份身份供切换，服务端按用户隔离素材与任务。

## 共同接口契约

- `POST /api/identities` `{displayName}` -> `{id,displayName,token}`。`GET /api/me` 返回用户。
- 私有接口请求头 `Authorization: Bearer <token>`。
- `GET /api/templates?category=...` -> 模板数组；`GET /api/templates/{id}` -> 模板。
- 模板：`id,title,category,coverUrl,description,requiredImageCount,acceptedMimeTypes,maxFileSizeBytes`。
- `POST /api/assets` multipart `file` -> `{id,url,originalName,mimeType,sizeBytes}`。
- `POST /api/tasks` `{templateId,assetIds,simulation?:{delayMs?:number,failStage?:"CHECKING"|"GENERATING"|"FINALIZING"|null}}` -> 202 Task。
- `GET /api/tasks` -> Task[]，最新优先；`GET /api/tasks/{id}` -> Task。
- Task：`id,templateId,templateTitle,coverUrl,status,stage,resultUrl,errorMessage,createdAt,updatedAt,steps`。
- status：`QUEUED,RUNNING,SUCCEEDED,FAILED`；stage：`WAITING,CHECKING,GENERATING,FINALIZING,COMPLETED`。
- steps：`[{stage,status,startedAt,finishedAt,errorMessage}]`；步骤状态 `PENDING,RUNNING,SUCCEEDED,FAILED`。
- 错误：HTTP 4xx/5xx + `{message}`；图片 `/demo/...`、`/media/...` 通过同源代理。

## 分工与验收

- [x] backend/：Java 17、Spring Boot、MyBatis、MySQL schema/种子、上传校验、身份校验、事务后调度、阶段持久化、失败停止；先编写流程与权限测试。
- [x] frontend/：Next.js、TS、列表/详情/历史；上传替换删除；可取消请求；按任务隔离查询错误；仅活跃任务轮询；切换用户清理；行为测试。
- [x] 根目录：Compose、CI、素材、README、验证记录；完整源码审查与接口契约对齐。
- [x] 验证 lint/typecheck/test/build、Java tests/package、成功/三个失败阶段/多任务/身份隔离/刷新恢复；Compose 实际运行未在本机完成，已交付 CI 路径。

容器不可用时明确记录未验证部分，不将源码实现等同运行通过。实际投入按并行墙钟时间记录，不虚构人工工时。
