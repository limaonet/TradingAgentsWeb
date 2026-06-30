package com.tradingagents.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 研究经理 Agent
 * 负责整合分析师报告与完整因果图，生成投资计划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchManagerAgent {

    private final ChatLanguageModel deepThinkingModel;
    private final AnalysisProgressHandler progressHandler;
    private final ObjectMapper objectMapper;

    public String generateInvestmentPlan(String analysisId, String symbol, String date,
                                         String marketReport, String sentimentReport,
                                         String newsReport, String fundamentalsReport,
                                         CausalGraph causalGraph) {
        log.info("【研究经理】正在生成投资计划 标的={}", symbol);
        progressHandler.sendAgentStatus(analysisId, "research_manager", "running", "正在整合分析师报告与因果图...");

        try {
            String prompt = buildPrompt(symbol, date, marketReport, sentimentReport,
                    newsReport, fundamentalsReport, causalGraph);

            ResearchManager manager = AiServices.create(ResearchManager.class, deepThinkingModel);
            String plan = manager.generatePlan(prompt);

            progressHandler.sendAgentStatus(analysisId, "research_manager", "completed", "投资计划生成完成");
            progressHandler.sendReport(analysisId, "investment_plan", plan);

            log.info("【研究经理】投资计划已生成 标的={}", symbol);
            return plan;

        } catch (Exception e) {
            log.error("【研究经理】失败 标的={} 原因：{}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "research_manager", e.getMessage());
            throw new RuntimeException("投资计划生成失败", e);
        }
    }

    private String buildPrompt(String symbol, String date,
                               String marketReport, String sentimentReport,
                               String newsReport, String fundamentalsReport,
                               CausalGraph causalGraph) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为股票 ").append(symbol).append(" 在 ").append(date).append(" 生成投资计划。\n\n");

        prompt.append("【市场分析报告】\n");
        prompt.append(marketReport != null ? marketReport : "无数据").append("\n\n");

        prompt.append("【情绪分析报告】\n");
        prompt.append(sentimentReport != null ? sentimentReport : "无数据").append("\n\n");

        prompt.append("【因果链分析报告】\n");
        prompt.append(newsReport != null ? newsReport : "无数据").append("\n\n");

        prompt.append("【基本面分析报告】\n");
        prompt.append(fundamentalsReport != null ? fundamentalsReport : "无数据").append("\n\n");

        prompt.append("【因果图完整 JSON（含节点证据、边置信度与 contradicts 边）】\n");
        prompt.append(serializeGraph(causalGraph)).append("\n\n");

        prompt.append("""
                请基于以上分析师报告与因果图，生成综合投资计划。
                必须引用因果图中的关键路径（事件→因子→指标→结果），并说明如何权衡矛盾边（contradicts）。
                """);

        return prompt.toString();
    }

    private String serializeGraph(CausalGraph graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(graph);
        } catch (Exception e) {
            return graph.getSummary() != null ? graph.getSummary() : "{}";
        }
    }

    interface ResearchManager {
        @SystemMessage("""
            你是一位资深的研究经理，负责整合多位分析师的报告与因果图，生成综合投资计划。
            
            职责：
            1. 综合技术面、情绪面、基本面与因果链多维度分析
            2. 引用因果图中的关键路径与矛盾边
            3. 识别各分析师观点的一致性和分歧
            4. 权衡风险与收益，制定投资策略
            5. 明确建议方向（做多/做空/观望）及置信度（0-100）
            
            输出格式：
            ## 投资计划概述
            [投资方向、核心逻辑、置信度%]
            
            ## 因果链关键路径
            [事件→因子→指标→结果，含矛盾边权衡]
            
            ## 分析师观点汇总
            | 分析师 | 观点 | 置信度 |
            |--------|------|--------|
            
            ## 核心逻辑
            ## 投资策略
            ## 风险因素
            ## 关键监控指标
            """)
        @UserMessage("{{it}}")
        String generatePlan(String prompt);
    }
}
