package com.tradingagents.agents;

import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 研究经理 Agent
 * 负责整合分析师报告，生成投资计划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchManagerAgent {

    private final ChatLanguageModel deepThinkingModel;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 生成投资计划
     */
    public String generateInvestmentPlan(String analysisId, String symbol, String date,
                                         String marketReport, String sentimentReport, 
                                         String newsReport, String fundamentalsReport) {
        log.info("Research Manager generating investment plan for {}", symbol);
        progressHandler.sendAgentStatus(analysisId, "research_manager", "running", "正在整合分析师报告...");
        
        try {
            // 构建提示
            String prompt = buildPrompt(symbol, date, marketReport, sentimentReport, newsReport, fundamentalsReport);
            
            // 调用 LLM
            ResearchManager manager = AiServices.create(ResearchManager.class, deepThinkingModel);
            String plan = manager.generatePlan(prompt);
            
            progressHandler.sendAgentStatus(analysisId, "research_manager", "completed", "投资计划生成完成");
            progressHandler.sendReport(analysisId, "investment_plan", plan);
            
            log.info("Research Manager completed investment plan for {}", symbol);
            return plan;
            
        } catch (Exception e) {
            log.error("Research Manager failed for {}: {}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "research_manager", e.getMessage());
            throw new RuntimeException("投资计划生成失败", e);
        }
    }

    /**
     * 构建提示
     */
    private String buildPrompt(String symbol, String date,
                               String marketReport, String sentimentReport,
                               String newsReport, String fundamentalsReport) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为股票 ").append(symbol).append(" 在 ").append(date).append(" 生成投资计划。\n\n");
        
        prompt.append("【市场分析报告】\n");
        prompt.append(marketReport != null ? marketReport : "无数据").append("\n\n");
        
        prompt.append("【情绪分析报告】\n");
        prompt.append(sentimentReport != null ? sentimentReport : "无数据").append("\n\n");
        
        prompt.append("【新闻分析报告】\n");
        prompt.append(newsReport != null ? newsReport : "无数据").append("\n\n");
        
        prompt.append("【基本面分析报告】\n");
        prompt.append(fundamentalsReport != null ? fundamentalsReport : "无数据").append("\n\n");
        
        prompt.append("请基于以上分析师报告，生成一份综合投资计划。");
        
        return prompt.toString();
    }

    /**
     * 研究经理接口定义
     */
    interface ResearchManager {
        @SystemMessage("""
            你是一位资深的研究经理，负责整合多位分析师的报告，生成综合投资计划。
            
            职责：
            1. 综合技术面、情绪面、基本面等多维度分析
            2. 识别各分析师观点的一致性和分歧
            3. 权衡风险与收益，制定投资策略
            4. 明确建议方向（做多/做空/观望）
            5. 设定目标价位和止损位
            
            输出格式：
            ## 投资计划概述
            [投资方向、核心逻辑]
            
            ## 分析师观点汇总
            | 分析师 | 观点 | 置信度 |
            |--------|------|--------|
            | 技术面 | [看多/看空/中性] | [高/中/低] |
            | 情绪面 | [看多/看空/中性] | [高/中/低] |
            | 基本面 | [看多/看空/中性] | [高/中/低] |
            
            ## 核心逻辑
            [支撑投资决策的关键理由]
            
            ## 投资策略
            **方向**: [做多/做空/观望]
            **建议仓位**: [轻仓/中等/重仓]
            **目标价位**: [价格]
            **止损价位**: [价格]
            **持有周期**: [短期/中期/长期]
            
            ## 风险因素
            [主要风险点]
            
            ## 关键监控指标
            [需要持续关注的指标]
            """)
        @UserMessage("{{it}}")
        String generatePlan(String prompt);
    }
}
