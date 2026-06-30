package com.tradingagents.agents;

import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.data.model.NewsItem;
import com.tradingagents.data.service.EventCollectionService;
import com.tradingagents.service.CausalGraphParser;
import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 因果分析师 Agent
 * 基于真实新闻事件与三份分析师报告，构建因果链图谱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CausalAnalystAgent {

    private final ChatLanguageModel quickThinkingModel;
    private final EventCollectionService eventCollectionService;
    private final CausalGraphParser causalGraphParser;
    private final AnalysisProgressHandler progressHandler;

    public CausalAnalysisResult analyze(String analysisId, String symbol, String date,
                                        String marketReport, String sentimentReport,
                                        String fundamentalsReport) {
        log.info("【因果分析师】开始分析 标的={} 日期={}", symbol, date);
        progressHandler.sendAgentStatus(analysisId, "causal_analyst", "running", "正在采集新闻事件...");

        try {
            List<NewsItem> newsItems = eventCollectionService.collectNews(symbol, 15).block();
            progressHandler.sendAgentStatus(analysisId, "causal_analyst", "running", "正在构建因果链...");

            String prompt = buildPrompt(symbol, date, newsItems, marketReport, sentimentReport, fundamentalsReport);
            CausalAnalyst analyst = AiServices.create(CausalAnalyst.class, quickThinkingModel);
            String rawOutput = analyst.analyze(prompt);
            CausalGraph graph = causalGraphParser.parse(rawOutput);

            progressHandler.sendAgentStatus(analysisId, "causal_analyst", "completed", "因果链分析完成");
            progressHandler.sendReport(analysisId, "causal_report", graph.getSummary());
            progressHandler.sendCausalGraph(analysisId, graph);

            log.info("【因果分析师】完成 标的={} 节点数={} 边数={}", symbol, graph.getNodes().size(), graph.getEdges().size());
            return new CausalAnalysisResult(graph.getSummary(), graph);

        } catch (Exception e) {
            log.error("【因果分析师】失败 标的={} 原因：{}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "causal_analyst", e.getMessage());
            throw new RuntimeException("因果分析失败", e);
        }
    }

    private String buildPrompt(String symbol, String date, List<NewsItem> newsItems,
                               String marketReport, String sentimentReport, String fundamentalsReport) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为股票 ").append(symbol).append(" 在 ").append(date).append(" 构建因果链分析。\n\n");

        prompt.append("【真实新闻事件】\n");
        if (newsItems == null || newsItems.isEmpty()) {
            prompt.append("（暂无新闻数据，请基于分析师报告推断，并降低 confidence）\n");
        } else {
            int idx = 1;
            for (NewsItem item : newsItems) {
                prompt.append(idx++).append(". [").append(item.getPublishedAt() != null ? item.getPublishedAt() : "未知时间")
                        .append("] ").append(item.getTitle());
                if (item.getSource() != null) {
                    prompt.append(" (来源: ").append(item.getSource()).append(")");
                }
                if (item.getUrl() != null && !item.getUrl().isBlank()) {
                    prompt.append(" URL: ").append(item.getUrl());
                }
                prompt.append("\n");
            }
        }
        prompt.append("\n");

        prompt.append("【市场分析报告】\n").append(marketReport != null ? marketReport : "无").append("\n\n");
        prompt.append("【情绪分析报告】\n").append(sentimentReport != null ? sentimentReport : "无").append("\n\n");
        prompt.append("【基本面分析报告】\n").append(fundamentalsReport != null ? fundamentalsReport : "无").append("\n\n");

        prompt.append("""
                请输出严格 JSON（可包在 ```json 代码块中），结构如下：
                {
                  "summary": "因果链一句话摘要",
                  "nodes": [
                    {"id":"evt_1","type":"event","label":"事件标题","description":"说明","eventTime":"2026-06-26","sourceRef":"新闻URL或指标名","confidence":0.9},
                    {"id":"fac_1","type":"factor","label":"传导因子","description":"机制说明","confidence":0.8},
                    {"id":"ind_1","type":"indicator","label":"可观测指标","description":"如PE下行","confidence":0.75},
                    {"id":"out_1","type":"outcome","label":"市场结果","description":"如短期偏多","confidence":0.7}
                  ],
                  "edges": [
                    {"id":"e1","source":"evt_1","target":"fac_1","relation":"causes","strength":0.8,"evidence":"依据","confidence":0.85,"edgeType":"fact"},
                    {"id":"e2","source":"fac_1","target":"ind_1","relation":"leads_to","strength":0.7,"evidence":"依据","confidence":0.7,"edgeType":"inference"}
                  ]
                }
                要求：
                1. 事件节点必须对应上方真实新闻，sourceRef 填 URL
                2. 指标节点应对应报告中的真实数据
                3. edgeType: fact=有明确证据, inference=模型推断
                4. 至少 3 个节点、2 条边，形成可上下追溯的链条
                """);

        return prompt.toString();
    }

    public record CausalAnalysisResult(String summary, CausalGraph graph) {}

    interface CausalAnalyst {
        @SystemMessage("""
            你是因果分析专家，擅长从事件→机制→指标→结果构建因果链。
            必须基于给定的新闻和报告数据，不得编造不存在的事件。
            只输出 JSON，不要输出其他解释文字。
            """)
        @UserMessage("{{it}}")
        String analyze(String prompt);
    }
}
