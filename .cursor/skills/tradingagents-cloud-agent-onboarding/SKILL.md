# TradingAgents Web：云代理入门（运行与测试）

面向在远程/云环境中操作本仓库的代理：先满足**可启动、可验证**，再按需接入密钥与外部站点。按仓库区域组织；无独立「功能开关」服务时，用 **Spring 配置与环境变量** 充当开关。

---

## 0. 云代理先决条件（一次弄清）

| 需求 | 说明 |
|------|------|
| 工作目录 | 仓库根目录含 `tradingagents-ui/`、`tradingagents-server/`、`docker/` |
| Node | `^20.19.0 \|\| >=22.12.0`（见 `tradingagents-ui/package.json` 的 `engines`） |
| Java / Maven | Java 17、Maven 3.9+（根目录 `README.md`） |
| 密钥 | **分析走 LLM 时必须**配置 OpenRouter（或你改配置后对应的 provider）；舆情/股吧等见下文「登录与 Cookie」 |
| 双进程 | 典型本地开发：**后端 8080 + 前端 5173**；前端通过 Vite 代理转发 `/api` 与 `/ws` 到后端（`tradingagents-ui/vite.config.ts`） |

**根目录 `.env.example`**：列出常用变量名；Spring Boot **不会自动读取**仓库根目录的 `.env`，需在 shell 中 `export` 或 IDE 运行配置里注入，或在 `docker-compose` 的 `environment` 中传入。

---

## 1. 仓库根目录（`/`）

**作用**：总览、环境变量清单、Docker 编排入口。

| 步骤 | 命令 / 位置 |
|------|----------------|
| 环境变量模板 | `cp .env.example .env` 后按需编辑；代理执行命令前用 `set -a && source .env && set +a`（bash）或逐条 `export KEY=value` |
| Docker 启动 | `docker compose -f docker/docker-compose.yml up -d`（需本机 Docker；compose 内后端 healthcheck 依赖 actuator，与当前 `pom.xml` 可能不一致，若失败优先用「本地 Maven + Vite」路径） |
| 文档 | `README.md`：快速开始与 YAML 片段说明 |

**最小验证（不启动容器）**：确认 `tradingagents-ui/package.json` 与 `tradingagents-server/pom.xml` 存在即可。

---

## 2. 后端 `tradingagents-server/`（Spring Boot）

**作用**：REST API、WebSocket/STOMP、LangChain4j、数据源与舆情客户端。

### 2.1 配置与「开关」

主配置：`tradingagents-server/src/main/resources/application.yml`。

| 意图 | 环境变量 / 配置键 |
|------|-------------------|
| LLM（OpenRouter） | `OPENROUTER_API_KEY`（必填才能调模型）；`OPENROUTER_BASE_URL`、`DEFAULT_CHAT_MODEL`、`DEFAULT_QUICK_MODEL` 见 `application.yml` 的 `llm.openrouter.*`。**若仓库中默认值为占位符（例如字面量 `[REDACTED]`），必须在环境里显式设为真实 OpenRouter 地址与模型 ID**，否则请求会指向无效 URL。Java 侧默认值见 `LangChain4jConfig`（与 YAML 不一致时以运行时的属性注入为准）。 |
| 东方财富数据源 | `EASTMONEY_ENABLED`（默认 true） |
| 雪球 / 股吧 HTTP | `data.sentiment.xueqiu.enabled` / `guba.enabled`（YAML）；Cookie：`XUEQIU_COOKIE`、`GUBA_COOKIE`（见 `.env.example`） |
| Playwright 舆情客户端 | `PlaywrightSentimentClient` 使用 `@Value("${data.sentiment.xueqiu.enabled:true}")` 等；可选无头：`mvn spring-boot:run -Dspring-boot.run.arguments="--data.sentiment.playwright.headless=false"`（调 UI 自动化时）。无独立 feature flag 包，**禁数据源 = 改 YAML 或 Spring 属性覆盖** |

启动前在 shell 中导出至少：`OPENROUTER_API_KEY`（若要走完整分析链路）。

### 2.2 启动

```bash
cd tradingagents-server
mvn spring-boot:run
```

默认端口 **8080**（`application.yml` 的 `server.port`）。

### 2.3 测试工作流（后端）

```bash
cd tradingagents-server
mvn test
```

- 当前测试位于 `src/test/java/.../SentimentDataServiceTest.java`：**集成风格**，会启动 Playwright 并访问外网；CI 中可能较慢或偶发失败，属环境/网络敏感。
- 定向运行单测：`mvn -Dtest=SentimentDataServiceTest test`
- 打包跳过测试：`mvn -q -DskipTests package`

**无密钥时的后端自检**：能启动即表示依赖与编译正常；完整分析需 LLM 与数据源配置。

---

## 3. 前端 `tradingagents-ui/`（Vue 3 + Vite）

**作用**：界面、Pinia、`/api` 调用、WebSocket 客户端。

### 3.1 环境变量

- 可选：`VITE_API_BASE_URL`（默认 `/api`，开发时走 Vite 代理到 `http://localhost:8080`）

### 3.2 启动

```bash
cd tradingagents-ui
npm install
npm run dev
```

默认 **http://localhost:5173**；需**先起后端**再测分析/搜索等接口。

### 3.3 测试工作流（前端）

```bash
cd tradingagents-ui
npm run lint          # ESLint + Oxlint
npm run build         # vue-tsc + Vite 生产构建
npm run type-check    # 仅类型检查
```

无 `npm test` 脚本；**手工烟测**：打开 dev 页面，触发股票搜索与分析，观察网络面板中 `/api` 与 `/ws` 是否 200/连接成功。

---

## 4. Docker `docker/`

**作用**：生产风格镜像与 `docker-compose.yml`（后端、前端、可选 Redis）。

- 构建与启动：`docker compose -f docker/docker-compose.yml up -d`
- Compose 中后端环境变量以 `OPENAI_*` 等为主，与当前 `application.yml` 强调的 OpenRouter 变量**可能不完全一致**；以实际运行所用的 `application.yml` 与容器 `environment` 为准。
- **健康检查**：compose 使用 `curl ... /actuator/health`；若后端未引入 actuator，健康检查会失败——此时改用本地 Maven 运行或调整 compose。

---

## 5. 登录、Cookie 与「模拟」外部依赖

本仓库**没有**单独的功能标志微服务；以下为云代理常见实操。

### 5.1 雪球 / 东方财富股吧（浏览器登录）

1. 用浏览器登录 `https://xueqiu.com`、`https://guba.eastmoney.com`。
2. 开发者工具 → Network → 任意请求 → 复制 **Cookie** 请求头。
3. 写入环境变量：`XUEQIU_COOKIE`、`GUBA_COOKIE`（见 `.env.example`），再启动后端。

未设置 Cookie 时，部分字段可能为空；Playwright 路径仍可能拉取公开页片段（行为依赖目标站与无头环境）。

### 5.2 模拟或弱化外部调用

- **关东方财富 HTTP**：`export EASTMONEY_ENABLED=false`（若代码路径尊重该开关）。
- **关雪球/股吧**：在 `application.yml` 将 `data.sentiment.xueqiu.enabled` / `guba.enabled` 设为 `false`，或通过 Spring 命令行属性覆盖：  
  `mvn spring-boot:run -Dspring-boot.run.arguments="--data.sentiment.xueqiu.enabled=false"`  
- **LLM**：无有效 `OPENROUTER_API_KEY` 时，启动可能仍成功，但分析请求会失败；代理任务若只需 UI，可只起前端并 mock 网络（不在仓库内建 mock 服务时需自行代理）。

---

## 6. 云代理推荐最小闭环

1. `export OPENROUTER_API_KEY=...`（及需要的 `OPENROUTER_BASE_URL` / 模型名，若与默认不同）。
2. 终端 A：`cd tradingagents-server && mvn spring-boot:run`
3. 终端 B：`cd tradingagents-ui && npm install && npm run dev`
4. 浏览器或 HTTP 客户端访问 `http://localhost:5173`，走一遍分析与搜索。
5. 可选质量门：`mvn test`（后端）、`npm run lint && npm run build`（前端）。

---

## 7. 如何更新本技能（发现新技巧或手册变更时）

在**同一文件**（本 `SKILL.md`）中增量修改，并遵守：

1. **先对照代码再写**：启动命令、端口、环境变量名以 `package.json`、`pom.xml`、`application.yml`、`vite.config.ts`、`.env.example` 为准；README 与 compose 冲突时**以代码为准**，并在技能里用一句话标注冲突点。
2. **按区域追加**：新内容放入对应章节（根目录 / server / ui / docker）；新增集成测试类时，在 §2.3 补上类名与 `mvn -Dtest=...` 示例。
3. **标注敏感度**：外网、Playwright、付费 API 的测试标为「环境敏感」，避免默认在 CI 中全量运行而未说明。
4. **提交**：与本仓库其他文档一样走 git；变更一句话摘要写入 commit message，便于人类审阅。

保持本节简短；正文保持「可执行步骤」优先于长背景说明。
