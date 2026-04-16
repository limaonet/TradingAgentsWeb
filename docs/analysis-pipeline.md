# 分析流水线（后端编排）

本文档描述一次股票分析从 API 进入到最终决策的**实际执行顺序**，与 `tradingagents-server` 中 `AnalysisService#executeAnalysisFlow` 一致。GitHub 会自动渲染文中的 Mermaid 图。

## 总览

1. **Phase 1**：市场、情绪、基本面三位分析师**并行**执行（`Mono.zipDelayError`）。
2. **Phase 2**：研究经理综合三份报告，生成投资计划。
3. **Phase 3**：交易员基于投资计划生成交易计划。
4. **Phase 4**：风控 — 激进派与保守派**并行**，再由中立派综合两方观点。
5. **Phase 5**：组合经理生成最终交易决策；完成后通过 WebSocket 推送 `complete`。

进度与中间结果由 `AnalysisProgressHandler` 推送到 STOMP 主题 `/topic/analysis/{analysisId}`。

## 编排流程图

```mermaid
flowchart TD
    subgraph API["入口"]
        A["POST /api/analysis/start"]
        B["解析 ticker → 标的代码"]
        C["创建 AnalysisState，返回 analysisId"]
    end

    subgraph P1["Phase 1 · 并行分析师"]
        M["市场分析师 · MarketAnalyst"]
        S["情绪分析师 · SentimentAnalyst"]
        F["基本面分析师 · FundamentalsAnalyst"]
    end

    subgraph P2["Phase 2"]
        RM["研究经理 · ResearchManager<br/>投资计划"]
    end

    subgraph P3["Phase 3"]
        TR["交易员 · Trader<br/>交易计划"]
    end

    subgraph P4["Phase 4 · 风控"]
        AG["激进派风控"]
        CS["保守派风控"]
        NT["中立派风控<br/>（读取激进+保守结论）"]
    end

    subgraph P5["Phase 5"]
        PM["组合经理 · PortfolioManager<br/>最终决策"]
        DONE["状态 completed · WS complete"]
    end

    A --> B --> C --> P1
    M --> RM
    S --> RM
    F --> RM
    RM --> TR
    TR --> AG
    TR --> CS
    AG --> NT
    CS --> NT
    NT --> PM --> DONE
```

## 客户端与实时推送（简图）

```mermaid
sequenceDiagram
    participant UI as 浏览器 Vue
    participant API as Spring REST
    participant WS as STOMP /ws/websocket
    participant SVC as AnalysisService

    UI->>API: POST /api/analysis/start
    API->>SVC: startAnalysis
    API-->>UI: analysisId
    UI->>WS: 连接并 SUB /topic/analysis/{id}
    SVC-->>WS: progress / agent_status / report / debate / complete
    WS-->>UI: JSON 消息 → Pinia 更新界面
```

## 相关代码

- 编排：`tradingagents-server/src/main/java/com/tradingagents/service/AnalysisService.java`
- 推送：`tradingagents-server/src/main/java/com/tradingagents/websocket/AnalysisProgressHandler.java`
- 前端消费：`tradingagents-ui/src/stores/analysisStore.ts`、`tradingagents-ui/src/composables/useWebSocket.ts`
