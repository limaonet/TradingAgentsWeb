package com.tradingagents.agents;

import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 组合经理 Agent
 * 负责生成最终交易决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioManagerAgent {

    private final ChatLanguageModel deepThinkingModel;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 生成最终交易决策
     */
    public String generateFinalDecision(String analysisId, String symbol, String date,
                                        String marketReport, String sentimentReport,
                                        String fundamentalsReport, String investmentPlan,
                                        String tradePlan, String aggressiveView,
                                        String conservativeView, String neutralView) {
        log.info("【组合经理】正在生成最终决策 标的={}", symbol);
        progressHandler.sendAgentStatus(analysisId, "portfolio_manager", "running", "正在生成最终决策...");
        
        try {
            // 构建提示
            String prompt = buildPrompt(symbol, date, marketReport, sentimentReport,
                                        fundamentalsReport, investmentPlan, tradePlan,
                                        aggressiveView, conservativeView, neutralView);
            
            // 调用 LLM
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

    /**
     * 构建提示
     */
    private String buildPrompt(String symbol, String date,
                               String marketReport, String sentimentReport,
                               String fundamentalsReport, String investmentPlan,
                               String tradePlan, String aggressiveView,
                               String conservativeView, String neutralView) {
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
        
        prompt.append("作为组合经理，请综合考虑以上所有信息，生成最终交易决策。");
        
        return prompt.toString();
    }

    /**
     * 组合经理接口定义
     */
    interface PortfolioManager {
        @SystemMessage("""
            你是一位资深的组合经理，负责做出最终的交易决策。
            
            职责：
            1. 综合技术面、情绪面、基本面等多维度分析
            2. 权衡激进派、保守派、中立派的风险观点
            3. 做出明确的交易决策（做多/做空/观望）
            4. 确定具体的交易参数（价格、仓位等）
            5. 对决策负责，给出充分的理由
            
            输出格式：
            # 最终交易决策
            
            ## 决策概述
            **交易方向**: [做多/做空/观望]
            **决策信心**: [高/中/低]
            **建议执行时间**: [立即/择时/观望等待]
            
            ## 决策依据
            ### 技术面支撑
            [技术面分析要点]
            
            ### 情绪面支撑
            [情绪面分析要点]
            
            ### 基本面支撑
            [基本面分析要点]
            
            ### 风险评估
            [综合考虑三派观点后的风险评估]
            
            ## 交易执行方案
            **入场价位**: [具体价格或区间]
            **目标价位**: [目标价格]
            **止损价位**: [止损价格]
            **建议仓位**: [占总资金比例]
            **持有周期**: [短期/中期/长期]
            
            ## 风险管控
            **主要风险**: [列出主要风险点]
            **应对措施**: [风险发生时的应对]
            **最大可承受亏损**: [金额或比例]
            
            ## 监控要点
            [需要持续监控的关键指标和事件]
            
            ## 免责声明
            本决策基于AI分析生成，仅供参考，不构成投资建议。投资有风险，入市需谨慎。
            """)
        @UserMessage("{{it}}")
        String makeDecision(String prompt);
    }
}
