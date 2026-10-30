# 映作 Studio · 全栈素材制作

Next.js + Java + MySQL 的可独立运行笔试项目。浏览模板、上传图片、提交制作任务，在后台执行期间继续创建任务，最后查看个人历史与结果。生成是可控的模拟过程，不调用真实 AI。

推荐入口是 Docker Compose。**源码实现、单项测试与容器联调是不同的验收证据**，实际验证情况和投入记录见本文末尾及 [验收清单](docs/acceptance.md)。

## 启动

准备 Docker Engine / Docker Desktop 和 Docker Compose v2。在本目录执行：

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

浏览器打开 [http://localhost:3000](http://localhost:3000)。首次构建需要联网拉取镜像及依赖；完成后图片与业务运行均不依赖外部服务。MySQL 健康后启动 Java，Java 健康后启动前端。首次数据库初始化由 Java 执行版本内附的 schema 与模板种子 SQL。

自定义配置：复制 `.env.example` 为 `.env` 后修改。示例只含本地演示值，无真实凭据。端口冲突时修改 `FRONTEND_PORT`；MySQL 默认只在 Compose 网络内可达。

```bash
docker compose down
# down 保留数据卷；不要加 -v，除非明确要清空全部演示数据。
```

## 技术与目录

| 层 | 选择 | 职责 |
| --- | --- | --- |
| 前端 | Next.js 16.4.3 App Router、React 19.3.1、TypeScript 5.9.3、Node.js 22 | 页面、上传状态、任务跟踪、同源代理 |
| 后端 | Java 17、Spring Boot 3.5.7、MyBatis Starter 3.0.4 | 身份、模板、素材、任务业务、后台执行 |
| 数据库 | MySQL 8.4 | 用户、素材元数据、任务、逐步骤记录 |
| 测试 | Vitest 5.0.5、JUnit 5、Spring Boot Test | 前端状态生命周期、后端流程和接口 |
| 交付 | 多阶段 Dockerfile、Compose、GitHub Actions | 隔离构建、检查、运行 |

前端依赖的准确版本见 `frontend/package.json` 和锁文件，Java 版本固定在 `backend/pom.xml`。Next.js 所需 Node >=20.9，本项目容器使用 Node 22。无 Redis、MQ 或云存储。

```text
frontend/                 Next.js App Router、页面、请求代理、前端行为测试
backend/                  Spring Boot、MyBatis、Java 测试、Dockerfile
  src/main/resources/     MySQL schema/种子、配置、static/demo 本地 JPG
docker-compose.yml        运行前端、Java、MySQL
compose.test.yml          容器内运行各项检查与 API 冒烟测试
.github/workflows/ci.yml  push / pull_request 自动检查
scripts/smoke.py          真实 API 业务闭环检查
docs/                     实施计划、需求验收映射、验证记录
SOURCES.md                依赖和图片来源
```

## 配置与存储

| 配置 | 使用位置 | 含义 |
| --- | --- | --- |
| `JAVA_API_URL` | Next.js | Java 内网地址；Compose 为 `http://backend:8080` |
| `DB_URL` | Java | MySQL JDBC URL，含数据库名、UTF-8 和 UTC 时区 |
| `DB_USERNAME` / `DB_PASSWORD` | Java | 应用数据库用户 |
| `STORAGE_DIR` | Java | 上传及结果目录；容器挂载 `/app/storage` |
| `PORT` | Java | HTTP 端口，默认 8080 |
| `FRONTEND_PORT` | Compose | 浏览器访问端口，默认 3000 |

具体 Compose 变量与默认值以 `.env.example` 为准。浏览器只请求 Next.js 同源路径；代理把 `/api`、`/demo`、`/media` 请求发送给 Java，并传递身份头，禁用业务响应缓存。Next.js 不保存任务、不推进步骤。

MySQL 命名卷保存数据库，素材命名卷保存文件。只备份数据库不能恢复图片，部署时需一起备份。上传使用 UUID 文件名，不使用原文件名拼接路径。服务端校验文件格式、大小、实际可解码内容以及像素上限；创建任务还会检查模板、素材数量、重复素材 ID、素材归属和模板限制。

## 5 分钟演示

1. 打开首页，切换分类，选择一个模板。不同模板要求 1、2 或 3 张图片。
2. 创建一个演示用户（如“体验者 A”）。按上传位选择 `backend/src/main/resources/static/demo/` 中的 JPG，或使用页面的示例素材入口。替换、移除图片，观察按钮是否随素材准备情况变化。
3. 模拟设置保持成功，提交。接口受理后立刻显示任务，按钮离开“提交中”；依次看到素材检查、模拟生成、结果整理，成功后预览结果。
4. 设置每阶段耗时为 5000 ms，连续提交几个任务。切换到个人记录或刷新页面，状态从 Java 恢复，任务独立推进。关闭页面不影响 Java 线程。
5. 分别选择 `CHECKING`、`GENERATING`、`FINALIZING` 失败，提交三次。指定步骤失败，保存原因，后续步骤保持未执行。
6. 新建并切换到“体验者 B”，历史为空；切回 A，原记录恢复。相同显示名不会复用旧身份。
7. 有任务运行时暂时停止 Java：`docker compose stop backend`。页面应保留最近一次任务信息并提示查询异常，而非把任务改为失败。恢复 `docker compose start backend` 后手动恢复查询。**此操作会触发下述重启边界，不用于验证任务续跑。** 只模拟短暂网络异常时，可用浏览器开发工具临时离线后恢复。

生成结果是第一张上传图片的本地拷贝，不代表 AI 生成质量；多个上传位用于验证数量和素材业务流程。耗时为每一步的固定延迟，默认 1500 ms，可设置 500–5000 ms。无随机失败，便于复现。

## 身份与个人记录

`POST /api/identities` 为每个演示用户生成独立随机 Bearer token。浏览器 localStorage 保存用户列表和当前选择，刷新后继续使用；切换用户会取消旧订阅与请求，清理旧任务视图。服务端根据凭据解析用户，**不接受客户端传入的 userId 来决定归属**。

任务查询、历史和提交素材都校验所属用户。显示名不是密码，不可凭相同名字登录旧用户。清空浏览器存储会失去该浏览器持有的凭据，但不删除服务端记录。此方案用于单机演示，不实现密码找回、账号注册验证、令牌撤销或跨设备登录。

媒体文件使用随机不可预测 URL，链接本身可访问，不要求 `<img>` 携带 Bearer 头。这不是私密对象存储：拿到图片链接的人可以访问。个人任务记录仍要求身份验证；真实生产隐私场景应改为短期签名 URL 或鉴权下载。

## 主要接口

接口都由 Java 提供。私有接口头：`Authorization: Bearer <token>`。失败用 HTTP 4xx/5xx 及 `{ "message": "可理解的错误" }`，不把请求失败伪装成业务任务失败。

| 方法与路径 | 输入 | 返回 |
| --- | --- | --- |
| `POST /api/identities` | `{displayName}` | `{id,displayName,token}` |
| `GET /api/me` | Bearer | 当前用户 |
| `GET /api/templates?category=...` | 可选分类 | Template 数组 |
| `GET /api/templates/{id}` | 模板 ID | Template，缺失为 404 |
| `POST /api/assets` | Bearer，multipart `file` | `{id,url,originalName,mimeType,sizeBytes}` |
| `POST /api/tasks` | Bearer，见下例 | HTTP 202，Task |
| `GET /api/tasks` | Bearer | 当前用户 Task 数组，最新优先 |
| `GET /api/tasks/{id}` | Bearer | 当前用户的 Task |
| `GET /actuator/health` | 无 | 容器健康检查 |

Template 字段：`id,title,category,coverUrl,description,requiredImageCount,acceptedMimeTypes,maxFileSizeBytes`。

```json
{
  "templateId": "postcard",
  "assetIds": ["上传返回的素材 ID 1", "上传返回的素材 ID 2"],
  "simulation": {"delayMs": 2000, "failStage": "GENERATING"}
}
```

`simulation` 可省略；成功时 `failStage` 为 `null` 或省略。`assetIds` 数量以模板接口为准。模板示例 ID 为 coast、botanical、city、studio、postcard、collage。

Task 字段：`id,templateId,templateTitle,coverUrl,status,stage,resultUrl,errorMessage,createdAt,updatedAt,steps`。`resultUrl` 仅在完成整理后设置；步骤包含 `stage,status,startedAt,finishedAt,errorMessage`。时间按服务端 UTC 记录，浏览器转换为用户本地时间。

## 后台执行与前端生命周期

```text
提交校验 → 创建 QUEUED 任务及 PENDING 步骤 → 事务提交 → 提交线程池
  → RUNNING / CHECKING     素材检查
  → RUNNING / GENERATING   模拟生成
  → RUNNING / FINALIZING   结果整理
  → SUCCEEDED / COMPLETED + resultUrl

任一步抛错 → FAILED + 保留失败 stage + errorMessage → 停止后续步骤
```

每步开始和结束分别写入数据库，耗时期间可查询到当前阶段。查询仅 SELECT，不触发步骤执行。线程池固定 4 个工作线程、32 个等待槽位，有容量上限，不采用 CallerRunsPolicy 在 HTTP 请求线程执行完整制作。拒绝调度会落库为可见失败；正常提交不等待制作完成。终态不会因查询变回处理中。

前端按任务 ID 保存服务端快照和查询错误，二者分开。仅 QUEUED/RUNNING 任务自动查询，终态停止，没有待处理任务则不继续轮询。一个任务查询失败不会影响另一个任务；已有快照保留并提供恢复入口。页面卸载、用户切换、任务跟踪移除时取消请求并检查请求所属代次，迟到响应不能覆盖新用户的数据。

## 测试与构建

CI 在 push / pull_request 时运行前端 lint、TypeScript、Vitest、production build，Java tests/package，并准备 Compose HTTP 冒烟验收。验收者无需在宿主机运行项目依赖脚本。容器命令以 `compose.test.yml` 中服务为准：

```bash
docker compose -f docker-compose.yml -f compose.test.yml run --rm frontend-check
docker compose -f docker-compose.yml -f compose.test.yml run --rm backend-check
docker compose up --build -d --wait
docker compose -f docker-compose.yml -f compose.test.yml run --rm smoke
```

开发环境已有工具时，各目录内的等价脚本：前端 `npm ci`、`npm run lint`、`npm run typecheck`、`npm test`、`npm run build`、`npm run dev`；Java `mvn clean verify`、`mvn spring-boot:run`。Java 本地需配置可用 MySQL 和 `STORAGE_DIR`，前端 `JAVA_API_URL=http://localhost:8080`。提交含 npm 锁文件；Maven 的直接依赖及父 BOM 固定版本。

测试重点：后端三步顺序、指定任一步失败后停止、终态与身份归属；前端同时跟踪多任务、单个查询异常与任务 FAILED 的区分、终态停止、取消后迟到响应丢弃。HTTP 冒烟脚本针对真实 Java/MySQL 路径，不替代单元测试。

## 边界与取舍

- 不实现服务重启后的自动续跑。内存队列与执行中的工作会随进程退出丢失；记录留在 MySQL，终态结果仍可读。Java 启动时将遗留 QUEUED/RUNNING 标记为失败，记录“服务重启，未完成任务已中断，请重新提交”，避免永久轮询。这要求只运行一个后端实例，不是断点续跑。
- 单实例线程池，无分布式调度、自动重试、断点续跑、提交幂等键。按钮锁防止普通重复点击，不承诺网络响应丢失后的恰好一次受理。
- 示例结果采用确定性本地图片拷贝，不进行真实 AI 推理。失败与耗时控制是演示接口能力，不应用于不受信任的公共服务。
- 未提供任务/素材删除和自动垃圾回收，替换或取消上传后已存文件可能保留；长期运行需增加清理策略与配额。历史不做复杂分页优化，适合笔试规模。
- 不扩展支付、积分、模板后台、视频、账号恢复。文件和数据库均需持久卷，不把浏览器 blob URL 当作后台素材。
- CI 配置交付不等于检查已经通过。最终将按用户授权单次提交并推送到 `cccniuniu-hue/material-craft-station` 的 `main`；验收 commit 与远程 CI 状态以交付消息及验证记录为准。

## 部署与回滚

1. 在目标机器保存当前源码版本/镜像标签，备份 MySQL 和素材卷；设置非默认数据库密码，限制网络入口。
2. 用 `docker compose build` 生成新镜像，再执行 `docker compose up -d --wait`。检查健康与模板页面，运行 smoke 验证基本业务。
3. 回滚时切回上一个源码快照或固定镜像标签，重新 `up -d --wait`，保留原数据卷。本版本初始化 SQL 可重复执行，但不提供通用版本迁移工具；若未来变更了不兼容 schema，必须联动恢复相匹配的数据库与素材备份。
4. 公网生产需要 HTTPS、正式身份与媒体访问策略、资源限额、日志监控；此项目交付目标为本地容器验收，不声称已生产部署。

## AI 工作流与实际投入

使用了 Codex AI 编程工具，并行拆分 Java 业务、前端交互、容器/CI 三个模块；先统一接口契约、状态枚举、归属规则和配置名，再编写代码与针对性测试。主任务负责读取参考图、准备可离线使用的素材、交叉核对接口及整理交付。

关键验证例子是“任务查询失败”：测试先安排 A 任务请求异常、B 任务成功，确认 A 保留原任务状态并仅增加查询错误，B 正常进入终态；再验证取消跟踪后的迟到响应不会通知新用户。后端分别注入三种失败阶段，检查后续步骤没有执行。验证失败时依据输出定位问题，修复后重跑对应检查；未运行的检查单独标注。

实际投入：2026-10-30 16:31–16:54（北京时间），截至本地交付整理完成约 **24 分钟**。开发与素材准备约 14 分钟，集中测试和浏览器验证约 7 分钟，最终审查、README 与提交整理约 3 分钟；三个阶段存在交叠，按墙钟粗分。含 AI 并行代理，不累加为人工工时。推送后的远程 CI 排队及运行等待不计入这份本地开发投入记录。

具体命令、通过数量、容器环境限制与最终投入记录见 [验证记录](docs/verification.md)。素材与主要复用范围见 [SOURCES.md](SOURCES.md)。
