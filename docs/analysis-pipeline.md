# 分析流水线（从用户输入到最终决策）

本文档与 `tradingagents-server` 中 `AnalysisService#executeAnalysisFlow` 的**实际执行顺序**一致。GitHub 会渲染文中的 Mermaid 图。

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

    subgraph G3["④ Phase 1 · 三分析师并行"]
        A1["「市场分析师」<br/>MarketAnalystAgent<br/>────────<br/>K 线、均线、MACD/RSI/KDJ、<br/>趋势与支撑阻力等技术面结论"]
        A2["「情绪分析师」<br/>SentimentAnalystAgent<br/>────────<br/>舆情、新闻倾向、社区情绪、<br/>公告与情绪面结论"]
        A3["「基本面分析师」<br/>FundamentalsAnalystAgent<br/>────────<br/>盈利/偿债/成长/现金流、<br/>估值与财务质量结论"]
        J1{"三份报告<br/>到齐"}
    end

    subgraph G4["⑤ Phase 2"]
        R1["「研究经理」<br/>ResearchManagerAgent<br/>────────<br/>整合三维报告，识别一致与分歧，<br/>输出综合投资计划"]
    end

    subgraph G5["⑥ Phase 3"]
        T1["「交易员」<br/>TraderAgent<br/>────────<br/>把投资计划落实为可执行方案：<br/>入场、止盈止损、仓位与节奏"]
    end

    subgraph G6["⑦ Phase 4 · 风控（并行 → 综合）"]
        V1["「激进派风控」<br/>────────<br/>偏进攻：强调机会与收益弹性"]
        V2["「保守派风控」<br/>────────<br/>偏防守：强调回撤与安全边际"]
        J2{"双方观点<br/>到齐"}
        V3["「中立派风控」<br/>────────<br/>在激进与保守之间折中，<br/>形成平衡风险视图"]
    end

    subgraph G7["⑧ Phase 5 · 收官"]
        P1["「组合经理」<br/>PortfolioManagerAgent<br/>────────<br/>汇总报告、计划与风控意见，<br/>输出最终交易决策（对外结论）"]
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
