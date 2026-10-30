# 实际验证记录

日期：2026-10-30（北京时间）。本文件记录提交前完成的检查，不把未运行的容器配置写成通过。

## 本地已执行

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| `npm run lint` | 通过，exit 0 | ESLint 9.39.5 |
| `npm run typecheck` | 通过，exit 0 | TypeScript 5.9.3 |
| `npm test` | 3 文件、7 测试通过 | 多任务隔离、查询错误/业务失败、取消迟到请求、终态停询、快照时序、上传校验 |
| `npm run build` + postbuild | 通过 | Next.js 16.4.3 production / standalone，静态资源拷贝 |
| Java `mvn package` | 19 测试通过、0 failure/error/skipped，JAR 构建成功 | Workflow 5、UploadValidation 5、ApiIntegration 9 |
| Python HTTP smoke | 两次通过，exit 0 | Next.js → Java → H2（MySQL mode），真实本地文件 |
| YAML 与 Python 静态检查 | 通过 | 三份 YAML、smoke 编译、PNG 校验、容器配置断言 |
| 浏览器桌面/窄屏 | 已操作并截图 | 1440 与 390 像素，图库、详情制作、任务历史 |

Java 测试用 H2，仅限 test scope，生产配置与 Compose 始终使用 MySQL。开发机原有 Maven 3.6.1 不满足工具链要求，临时下载官方 Maven 3.9.9 到忽略目录 `.tools`；Java 为 17.0.13，前端本机 Node 24。CI 与容器前端使用 Node 22，Java 使用 17。缓存、工具、测试数据库与构建产物不提交。

执行的 Maven 命令为 `mvn package`，附本机 Maven settings 与本地仓库路径参数；这些环境路径没有固化到生产配置中。首次红测因业务实现尚未存在而失败，随后实现流程并修复直到通过。

## HTTP 闭环证据

`BASE_URL=http://127.0.0.1:3000 python scripts/smoke.py` 实际调用 Java 业务接口，上传完整 PNG；没有用 Next.js 或 mock server 替代 Java。

```text
PASS identity, server templates, category filter, real PNG upload and input validation
PASS async 202 (0.03s), concurrent tasks, three phases, all failure stages and stable terminal reads
PASS persisted history, identity switch, task access isolation and asset ownership isolation
HTTP acceptance smoke passed.
```

0.03 秒为首次本机实测提交延迟，不是性能承诺。一个任务按每阶段 2 秒设置，在 7 秒不查询期间继续执行并成功；另有成功任务及三个指定阶段失败任务，验证独立任务 ID、逐步时间戳、失败停止、终态查询稳定及历史归属。

## 浏览器验收与问题修正

- 实际操作：创建 A 身份、切换旅行分类（6 → 2 个模板）、打开双图模板、示例图片经 Java 上传、提交后立即出现后台阶段、成功预览。
- 指定生成阶段失败，刷新后从 Java 恢复正在处理的任务；终态显示失败阶段，另一个成功任务保持成功。
- 创建 B 后历史为空，切回 A 恢复两条记录。
- 前端服务重启期间创建身份请求失败，页面显示请求错误并可重试；未将已有任务改为执行失败。
- 390 像素下历史和详情可堆叠操作，测得 document.scrollWidth=384 <= innerWidth=390；1440 下无页面横向溢出。
- 独立审阅者查看桌面与小屏历史截图及参考图，未发现实质性可用性阻碍。未把截图审阅描述为完整键盘或真机无障碍认证。

审查修复了：迟到轮询覆盖新终态、可变精度时间戳比较、示例加载期间拖放竞争、批量示例部分失败后的迟到覆盖、分类失败后点击同分类显示旧结果、终态手动恢复后查询错误未清理。相应核心状态时序加入回归测试。

截图在 [screenshots](screenshots/)：desktop-gallery、desktop-composer、desktop-history、mobile-composer、mobile-history。它们是本地 H2 联调截图，不是公网部署或 MySQL 容器运行证明。

## 未在本机验证

本机没有 Docker Engine，WSL 不能运行容器，因此 Dockerfile 镜像构建、Compose 健康检查链、MySQL 8.4 实际 SQL 与重启持久卷恢复尚未在本机执行。已交付 GitHub Actions 的容器检查与 Compose smoke，推送后可查看相应 run；远端结果以 GitHub Checks 和交付消息为准。

本机未使用已有 MySQL 服务创建或修改数据库。第一次尝试通过 test profile 启动联调没有加载测试资源，应用连接默认数据库被拒绝；随后显式配置 H2 JDBC URL、driver 和测试 classpath，健康检查成功。此问题属于本地联调启动参数，不通过改写生产 MySQL 配置规避。

服务重启自动续跑未实现。单实例启动时把未完成任务标记失败，已完成任务及图片在持久化配置下保留。H2 内存联调重启会丢失演示身份，后续检查改用忽略目录下的 H2 文件；这些数据均不进入交付。

## 投入与发布

按单次协作的墙钟时间记录，含 AI 并行代理，不将多代理时间累加为人工工时。开发、测试与文档有交叠；精确起止和阶段估算见 README 的实际投入段。

发布采用用户授权的 SSH 远端 `git@github.com:cccniuniu-hue/material-craft-station.git`，在远端初始 README 提交基础上合成一次交付提交，直接推送 main，不创建 PR、不强推。提交署名 `cccniuniu-hue <cccniuniu@gmail.com>`。验收版本取本目录 `git rev-parse HEAD`，具体哈希在最终交付消息中列出。
