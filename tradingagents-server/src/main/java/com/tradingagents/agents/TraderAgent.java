package com.tradingagents.agents;

import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 交易员 Agent
 * 负责生成具体交易计划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraderAgent {

    private final ChatLanguageModel deepThinkingModel;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 生成交易计划
     */
    public String generateTradePlan(String analysisId, String symbol, String date, String investmentPlan) {
        log.info("【交易员】正在生成交易计划 标的={}", symbol);
        progressHandler.sendAgentStatus(analysisId, "trader", "running", "正在制定交易计划...");
        
        try {
            // 构建提示
            String prompt = buildPrompt(symbol, date, investmentPlan);
            
            // 调用 LLM
            Trader trader = AiServices.create(Trader.class, deepThinkingModel);
            String plan = trader.generatePlan(prompt);
            
            progressHandler.sendAgentStatus(analysisId, "trader", "completed", "交易计划生成完成");
            progressHandler.sendReport(analysisId, "trader_plan", plan);
            
            log.info("【交易员】交易计划已生成 标的={}", symbol);
            return plan;
            
        } catch (Exception e) {
            log.error("【交易员】失败 标的={} 原因：{}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "trader", e.getMessage());
            throw new RuntimeException("交易计划生成失败", e);
        }
    }

    /**
     * 构建提示
     */
    private String buildPrompt(String symbol, String date, String investmentPlan) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为股票 ").append(symbol).append(" 在 ").append(date).append(" 制定具体交易计划。\n\n");
        
        prompt.append("【投资计划】\n");
        prompt.append(investmentPlan != null ? investmentPlan : "无数据").append("\n\n");
        
        prompt.append("请基于以上投资计划，制定具体的交易执行方案。");
        
        return prompt.toString();
    }

    /**
     * 交易员接口定义
     */
    interface Trader {
        @SystemMessage("""
            你是一位专业的交易员，负责将投资计划转化为具体的交易执行方案。
            
            职责：
            1. 制定具体的入场策略（分批建仓、一次性建仓等）
            2. 设定明确的止损和止盈点位
            3. 规划仓位管理方案
            4. 制定风险控制措施
            5. 考虑交易成本和滑点
            
            输出格式：
            ## 交易计划概述
            [交易方向、核心策略]
            
            ## 入场策略
            **入场价位**: [具体价格区间]
            **入场方式**: [一次性/分批建仓]
            **分批方案**: [如分批，说明每批比例和价格]
            
            ## 出场策略
            **止盈价位**: [目标价格]
            **止损价位**: [止损价格]
            **移动止损**: [是否启用及触发条件]
            
            ## 仓位管理
            **建议仓位**: [占总资金比例]
            **最大仓位**: [上限]
            **加仓条件**: [什么情况下加仓]
            **减仓条件**: [什么情况下减仓]
            
            ## 风险控制
            **单笔最大亏损**: [金额或比例]
            **总风险敞口**: [占总资金比例]
            **黑天鹅应对**: [极端情况处理]
            
            ## 执行时间表
            [建议的执行时间节点]
            
            ## 注意事项
            [执行中的关键注意点]
            """)
        @UserMessage("{{it}}")
        String generatePlan(String prompt);
    }
}
