# 分析流水线（从用户输入到最终决策）

本文档与 `tradingagents-server` 中 `AnalysisService#executeAnalysisFlow` 的**实际执行顺序**一致。GitHub 会渲染文中的 Mermaid 图。

## Agent 一览（谁负责什么）

下表为流水线中的 **LangChain4j Agent**：Spring `@Component` 类名、所用模型档位、**读入什么**、**产出什么**、以及推送到前端的 WS 角色名（与 `analysisStore` 对齐）。编排层 `AnalysisService` 本身不是 Agent。

| # | 展示名 | Java 类 | 模型 | 主要输入 | 主要输出 | WS / 报告类型 |
|---|--------|---------|------|----------|----------|----------------|
| 1 | 市场分析师 | `MarketAnalystAgent` | 快速模型 `quickThinkingModel` | 标的、日期；`StockDataService` 拉行情与技术指标 | 技术分析报告（Markdown） | `market_analyst` → `market_report` |
| 2 | 情绪分析师 | `SentimentAnalystAgent` | 快速模型 | 标的、日期；`SentimentDataService` 拉舆情/社区等 | 情绪与舆情分析报告 | `sentiment_analyst` → `sentiment_report` |
| 3 | 基本面分析师 | `FundamentalsAnalystAgent` | 快速模型 | 标的、日期；财务与基本面数据 | 基本面分析报告 | `fundamentals_analyst` → `fundamentals_report` |
| 4 | 研究经理 | `ResearchManagerAgent` | 深度模型 `deepThinkingModel` | 上述三份报告（当前编排里「新闻」参数传 `null`，由情绪报告覆盖舆情） | **投资计划** `investment_plan` | `research_manager` |
| 5 | 交易员 | `TraderAgent` | 深度模型 | 投资计划全文 | **交易计划** `trader_plan`（可执行层面的方案） | `trader` |
| 6a | 激进派风控 | `RiskManagementAgents#aggressiveAnalysis` | 深度模型；内部 `AggressiveDebater` | 市场/情绪/基本面报告 + 交易计划 | 激进立场风险论述 | `debate` · speaker `aggressive` |
| 6b | 保守派风控 | `RiskManagementAgents#conservativeAnalysis` | 深度模型；`ConservativeDebater` | 同上 | 保守立场风险论述 | `debate` · speaker `conservative` |
| 6c | 中立派风控 | `RiskManagementAgents#neutralAnalysis` | 深度模型；`NeutralDebater` | 同上 + **激进 + 保守** 两段文字 | 折中后的风险综合意见 | `debate` · speaker `neutral` |
| 7 | 组合经理 | `PortfolioManagerAgent` | 深度模型；`PortfolioManager` 接口 | 三份分析师报告 + 投资计划 + 交易计划 + **三份风控观点** | **最终交易决策**（并触发 `sendComplete`） | `portfolio_manager` · `complete` |

**并行关系**：表 1–3 同时跑；表 6a 与 6b 同时跑，二者结束后才跑 6c。

---

## 阶段说明（谁在做什么）

| 阶段 | 角色 | 职责 |
|------|------|------|
| 用户 & 前端 | 用户 | 输入股票代码/名称，可选日期，点击「开始分析」。 |
| 用户 & 前端 | Vue 前端 | 调用 `POST /api/analysis/start`；用返回的 `analysisId` 建立 STOMP 订阅，实时更新进度与报告。 |
| 入口 | `AnalysisController` / `AnalysisService` | 接收请求、解析 ticker 为统一标的代码、创建任务、异步启动流水线。 |
| Phase 1 | 市场 / 情绪 / 基本面分析师（**并行**） | 分别产出技术面、舆情情绪、财务与估值维度的分析报告。 |
| Phase 2 | 研究经理 | 综合三份报告，形成**投资计划**（策略与逻辑）。 |
| Phase 3 | 交易员 | 在投资计划基础上细化**交易计划**（价位、仓位、止盈止损等可执行要素）。 |
| Phase 4 | 激进派 & 保守派风控（**并行**）→ 中立派 | 从风险收益不同立场评估交易计划；中立派在双方结论之上做**折中与综合**。 |
| Phase 5 | 组合经理 | 汇总全部材料，输出**最终交易决策**（可对外展示的综合结论）。 |
| 结束 | 状态 + WebSocket | 任务标记完成，向前端推送 `complete`；界面展示最终结果。 |

---

## 全链路流程图（从用户输入开始）

下图从左到右、从上到下阅读：**实线**为主干数据流；Phase 1 与 Phase 4 中并列的框为**并行**执行，汇入菱形汇合点后再进入下一步。

```mermaid
flowchart TB
    classDef user fill:#e8f5e9,stroke:#1b5e20,stroke-width:1px,color:#1b3310
    classDef fe fill:#e3f2fd,stroke:#0d47a1,stroke-width:1px,color:#0d1740
    classDef gate fill:#fce4ec,stroke:#880e4f,stroke-width:1px,color:#3e0d24
    classDef agent fill:#fff8e1,stroke:#e65100,stroke-width:1px,color:#3e2723
    classDef done fill:#ede7f6,stroke:#4527a0,stroke-width:1px,color:#311b92

    subgraph G0["① 用户"]
        U1["输入标的<br/>代码或名称（如 600519 / 茅台）"]
        U2["可选：分析日期<br/>未选则前端用默认/当日"]
        U3["点击「开始分析」"]
    end

    subgraph G1["② 前端 Vue"]
        F1["组装请求体<br/>ticker + date"]
        F2["HTTP POST<br/>/api/analysis/start"]
        F3["收到 analysisId 后<br/>STOMP 订阅 /topic/analysis/{id}"]
        F4["Pinia 消费 WS 消息<br/>进度条、时间线、报告弹窗"]
    end

    subgraph G2["③ 后端入口（编排，非 Agent）"]
        E1["AnalysisController<br/>校验并转发"]
        E2["解析 ticker → 标准标的代码<br/>SymbolResolver"]
        E3["创建 AnalysisState<br/>返回 analysisId，异步执行流水线"]
    end

    subgraph G3["④ Phase 1 · Agent 并行（快速模型）"]
        A1["Agent · 市场分析师<br/>MarketAnalystAgent<br/>────────<br/>读：行情与技术指标<br/>写：技术面 Markdown 报告"]
        A2["Agent · 情绪分析师<br/>SentimentAnalystAgent<br/>────────<br/>读：舆情与社区数据<br/>写：情绪面 Markdown 报告"]
        A3["Agent · 基本面分析师<br/>FundamentalsAnalystAgent<br/>────────<br/>读：财务与估值数据<br/>写：基本面 Markdown 报告"]
        J1{"三份报告<br/>到齐"}
    end

    subgraph G4["⑤ Phase 2 · Agent（深度模型）"]
        R1["Agent · 研究经理<br/>ResearchManagerAgent<br/>────────<br/>读：市场+情绪+基本面报告<br/>写：综合投资计划 investment_plan"]
    end

    subgraph G5["⑥ Phase 3 · Agent（深度模型）"]
        T1["Agent · 交易员<br/>TraderAgent<br/>────────<br/>读：投资计划<br/>写：可执行交易计划 trader_plan"]
    end

    subgraph G6["⑦ Phase 4 · Agent 风控（深度模型）"]
        V1["Agent · 激进派<br/>RiskManagementAgents + AggressiveDebater<br/>────────<br/>读：三报告 + 交易计划<br/>写：进攻型风险观点"]
        V2["Agent · 保守派<br/>RiskManagementAgents + ConservativeDebater<br/>────────<br/>读：同上<br/>写：防守型风险观点"]
        J2{"双方观点<br/>到齐"}
        V3["Agent · 中立派<br/>RiskManagementAgents + NeutralDebater<br/>────────<br/>读：同上 + 激进/保守全文<br/>写：折中风险综合意见"]
    end

    subgraph G7["⑧ Phase 5 · Agent 收官（深度模型）"]
        P1["Agent · 组合经理<br/>PortfolioManagerAgent<br/>────────<br/>读：三报告 + 两计划 + 三风控<br/>写：最终决策 + WS complete"]
        Z1["持久化状态 · 推送 WS complete<br/>前端展示完成态"]
    end

    U1 --> U2 --> U3
    U3 --> F1 --> F2
    F2 --> E1 --> E2 --> E3
    E3 --> A1
    E3 --> A2
    E3 --> A3
    A1 --> J1
    A2 --> J1
    A3 --> J1
    J1 --> R1 --> T1
    T1 --> V1
    T1 --> V2
    V1 --> J2
    V2 --> J2
    J2 --> V3
    V3 --> P1 --> Z1
    Z1 -.-> F4
    F2 -.-> F3

    class U1,U2,U3 user
    class F1,F2,F3,F4 fe
    class E1,E2,E3 gate
    class A1,A2,A3,R1,T1,V1,V2,V3,P1 agent
    class J1,J2 gate
    class Z1 done
```

说明：

- **虚线** `-.->`：`analysisId` 一旦可用即可建立 WebSocket；分析过程中消息持续推送到 `F4`。
- **并行**：`A1/A2/A3` 与 `V1/V2` 在代码中分别为 `Mono.zipDelayError` / `Mono.zip` 语义。

---

## 前后端时序（补充）

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant UI as 浏览器 Vue
    participant API as REST /api
    participant WS as STOMP /topic/analysis
    participant SVC as AnalysisService

    User->>UI: 输入标的、日期，点击开始分析
    UI->>API: POST /analysis/start {ticker, date}
    API->>SVC: startAnalysis()
    API-->>UI: 200 + analysisId
    UI->>WS: CONNECT + SUBSCRIBE /topic/analysis/{id}
    Note over SVC: 异步流水线运行…
    loop 分析进行中
        SVC-->>WS: progress / agent_status / report / debate
        WS-->>UI: JSON
        UI-->>User: 更新进度与内容
    end
    SVC-->>WS: complete（或 error）
    WS-->>UI: 最终态
    UI-->>User: 展示完成或错误
```

## 相关代码

- 编排：`tradingagents-server/src/main/java/com/tradingagents/service/AnalysisService.java`
- 推送：`tradingagents-server/src/main/java/com/tradingagents/websocket/AnalysisProgressHandler.java`
- 前端：`tradingagents-ui/src/stores/analysisStore.ts`、`tradingagents-ui/src/composables/useWebSocket.ts`
