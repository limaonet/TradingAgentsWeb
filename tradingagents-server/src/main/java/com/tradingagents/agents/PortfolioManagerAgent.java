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
 * 组合经理 Agent
 * 负责结合因果图生成最终交易决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioManagerAgent {

    private final ChatLanguageModel deepThinkingModel;
    private final AnalysisProgressHandler progressHandler;
    private final ObjectMapper objectMapper;

    public String generateFinalDecision(String analysisId, String symbol, String date,
                                        String marketReport, String sentimentReport,
                                        String fundamentalsReport, String investmentPlan,
                                        String tradePlan, String aggressiveView,
                                        String conservativeView, String neutralView,
                                        CausalGraph causalGraph) {
        log.info("【组合经理】正在生成最终决策 标的={}", symbol);
        progressHandler.sendAgentStatus(analysisId, "portfolio_manager", "running", "正在结合因果图生成最终决策...");

        try {
            String prompt = buildPrompt(symbol, date, marketReport, sentimentReport,
                    fundamentalsReport, investmentPlan, tradePlan,
                    aggressiveView, conservativeView, neutralView, causalGraph);

            PortfolioManager manager = AiServices.create(PortfolioManager.class, deepThinkingModel);
            String decision = manager.makeDecision(prompt);

            progressHandler.sendAgentStatus(analysisId, "portfolio_manager", "completed", "最终决策生成完成");
            progressHandler.sendComplete(analysisId, decision);

            log.info("【组合经理】最终决策已生成 标的={}", symbol);
            return decision;

        } catch (Exception e) {
            log.error("【组合经理】失败 标的={} 原因：{}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "portfolio_manager", e.getMessage());
            throw new RuntimeException("最终决策生成失败", e);
        }
    }

    private String buildPrompt(String symbol, String date,
                               String marketReport, String sentimentReport,
                               String fundamentalsReport, String investmentPlan,
                               String tradePlan, String aggressiveView,
                               String conservativeView, String neutralView,
                               CausalGraph causalGraph) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为股票 ").append(symbol).append(" 在 ").append(date).append(" 生成最终交易决策。\n\n");

        prompt.append("【市场分析报告】\n").append(marketReport).append("\n\n");
        prompt.append("【情绪分析报告】\n").append(sentimentReport).append("\n\n");
        prompt.append("【基本面分析报告】\n").append(fundamentalsReport).append("\n\n");
        prompt.append("【投资计划】\n").append(investmentPlan).append("\n\n");
        prompt.append("【交易计划】\n").append(tradePlan).append("\n\n");
        prompt.append("【激进派风险观点】\n").append(aggressiveView).append("\n\n");
        prompt.append("【保守派风险观点】\n").append(conservativeView).append("\n\n");
        prompt.append("【中立派风险观点】\n").append(neutralView).append("\n\n");
        prompt.append("【因果图完整 JSON】\n").append(serializeGraph(causalGraph)).append("\n\n");

        prompt.append("""
                作为组合经理，请综合考虑以上所有信息与因果图中的 outcome 节点，生成最终交易决策。
                必须包含决策方向、置信度（0-100%）及对 contradicts 边的仓位影响说明。
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
            return "{}";
        }
    }

    interface PortfolioManager {
        @SystemMessage("""
            你是一位资深的组合经理，负责做出最终的交易决策。
            
            职责：
            1. 综合技术面、情绪面、基本面与因果图 outcome 节点
            2. 权衡激进派、保守派、中立派的风险观点
            3. 结合因果图中的 contradicts 边调整仓位建议
            4. 做出明确的交易决策（做多/做空/观望）及置信度（0-100%）
            
            输出格式：
            # 最终交易决策
            ## 决策概述
            **交易方向**: [做多/做空/观望]
            **决策信心**: [0-100%]
            ## 因果链依据
            ## 决策依据
            ## 交易执行方案
            ## 风险提示
            """)
        @UserMessage("{{it}}")
        String makeDecision(String prompt);
    }
}
