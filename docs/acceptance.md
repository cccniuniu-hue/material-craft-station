# 验收对应表与复现方法

此表区分代码覆盖范围与实际执行证据。HTTP smoke 使用新建演示身份和有效 PNG，不删除已有数据；每次运行会留下少量验收素材和任务。

| 编号 | 要求 | 实现与检查入口 |
| --- | --- | --- |
| R01 | Next.js App Router / React / TypeScript；Java Spring Boot / MyBatis / MySQL | `frontend/`、`backend/`、`docker-compose.yml`；CI 在隔离容器编译两端 |
| R02 | 服务端模板、分类与详情 | 模板 API 与前端列表；smoke 验证多分类、分类过滤、详情字段、封面可访问 |
| R03 | 素材上传、替换、删除与双端校验 | 前端选择素材和 Java 上传校验；smoke 上传真实 PNG、拒绝伪装图片/错误类型/缺少素材 |
| R04 | 异步提交 | `POST /api/tasks` 返回 202；smoke 设置每阶段 2 秒，要求提交在 4 秒内返回非终态 |
| R05 | 三个独立持久化阶段、查询无副作用 | Java 工作流与 MyBatis；smoke 在不轮询的 7 秒内等待任务自主完成，校验阶段顺序/耗时/时间戳及终态重复读取不变化 |
| R06 | 可控制耗时与失败阶段 | `simulation.delayMs/failStage`；smoke 覆盖三个失败点并验证后续阶段均未启动 |
| R07 | 上传/提交/排队/运行/成功/失败与查询错误独立 | 前端状态组件和行为测试；人工观察错误提示与操作恢复 |
| R08 | 多任务独立、轮询生命周期、身份切换无过期覆盖 | 前端行为测试；smoke 连续提交 5 个任务并按 ID 独立校验；人工检查切换与卸载时取消请求 |
| R09 | 刷新恢复与历史 | MySQL 历史 API、前端历史；smoke 核对完整且倒序的任务列表；浏览器刷新验证需人工执行 |
| R10 | 演示身份与隔离 | 随机 Bearer token、用户约束；smoke 验证未授权请求、跨用户任务读取、素材复用和历史隔离 |
| R11 | 参考风格、响应式与语义可访问性 | 前端 UI；人工在桌面/窄屏检查布局、键盘导航、输入标签、焦点和状态播报 |
| R12 | CI：前端 lint/typecheck/test/build；Java 测试构建；关键链路 | `.github/workflows/ci.yml`、`compose.test.yml`、`scripts/smoke.py` |
| R13 | README：版本、结构、配置、数据库/素材、API、演示、边界、实际投入、部署回滚与 AI 过程 | 根目录 `README.md`；结合真实命令输出核验，不能用预期结果代替执行证据 |
| R14 | Docker Compose 一键部署 | MySQL 8.4、Java、Next.js，依赖健康检查，数据库与素材命名卷；仅对外发布前端 3000 |

## 使用容器复现

以下命令均在项目根目录执行。仅需要 Docker Engine / Desktop 与 Docker Compose v2；不需要在宿主机安装 Node、Java、Maven 或 Python。

```sh
# 可选：复制 .env.example 为 .env 后调整端口和本地密码。
docker compose up --build --detach --wait --wait-timeout 240

# 两个检查服务均把只读源码复制到容器内执行，产物不会写回宿主机。
docker compose -f docker-compose.yml -f compose.test.yml --profile checks run --rm --no-deps frontend-check
docker compose -f docker-compose.yml -f compose.test.yml --profile checks run --rm --no-deps backend-check

# 在独立 Python 容器中经过 Next.js 同源代理检查完整 HTTP 链路。
docker compose -f docker-compose.yml -f compose.test.yml --profile smoke run --rm smoke
docker compose ps
docker compose logs --no-color backend frontend

# 停止服务但保留数据库与上传素材；下次启动可恢复。
docker compose down
```

CI 对每次 push 和 pull request 执行前端 Node 22 容器检查、Java 17 / Maven 3.9 容器检查，通过后再启动完整 Compose 环境并运行 smoke。CI 使用临时数据库和卷，结束后清除；本地停止命令默认保留卷。

## 需要浏览器检查的行为

1. 桌面与手机宽度下打开首页，切换分类，进入详情；使用键盘完成主要操作。
2. 上传、替换、删除图片；观察类型/大小/数量提示，防止上传期间重复提交。
3. 提交默认成功任务与三个不同失败阶段任务；确认失败原因停留在对应阶段。
4. 快速提交多个任务、在详情与历史之间切换；终态停止轮询，查询失败不将任务改成业务失败。
5. 刷新页面，查看历史并重新打开任务；再切换身份，确认旧用户请求不能覆盖当前用户状态。
6. `docker compose down` 后重新启动，验证身份、历史、上传图片仍可使用。

## 当前环境的执行边界

交付环境没有可用 Docker Engine，WSL 也不可用于运行容器。因此 **Compose 启动、镜像构建、容器内测试和 MySQL 容器 HTTP smoke 尚未在此环境执行**。脚本与配置的静态检查不代表上述验收已通过。本地 Java/H2 联调 HTTP smoke 已通过；它不替代 MySQL 容器验证。实际命令与结果见 [verification.md](verification.md)；需要在具备 Docker 的机器或 GitHub Actions 中完成容器验收。

身份机制是演示用途；浏览器保存 token，切换身份不是完整注册/登录产品。任务读取和素材引用受用户隔离；演示图片与已知媒体 URL 可公开读取，不能作为私有文件存储使用。
