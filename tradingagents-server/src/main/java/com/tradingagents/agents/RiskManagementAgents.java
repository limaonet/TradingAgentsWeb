package com.tradingagents.agents;

import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 风控辩论 Agents
 * 激进派、保守派、中立派
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskManagementAgents {

    private final ChatLanguageModel deepThinkingModel;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 激进派分析
     */
    public String aggressiveAnalysis(String analysisId, String symbol, String date, 
                                      String marketReport, String sentimentReport,
                                      String fundamentalsReport, String tradePlan) {
        log.info("【风控-激进派】辩论分析中 标的={}", symbol);
        progressHandler.sendDebateUpdate(analysisId, "risk", "aggressive", "激进派正在分析...", 1);
        
        try {
            String prompt = buildDebatePrompt(symbol, date, marketReport, sentimentReport, 
                                              fundamentalsReport, tradePlan, "aggressive");
            
            AggressiveDebater debater = AiServices.create(AggressiveDebater.class, deepThinkingModel);
            String analysis = debater.debate(prompt);
            
            progressHandler.sendDebateUpdate(analysisId, "risk", "aggressive", analysis, 1);
            return analysis;
            
        } catch (Exception e) {
            log.error("【风控-激进派】失败 原因：{}", e.getMessage());
            throw new RuntimeException("激进派分析失败", e);
        }
    }

    /**
     * 保守派分析
     */
    public String conservativeAnalysis(String analysisId, String symbol, String date,
                                        String marketReport, String sentimentReport,
                                        String fundamentalsReport, String tradePlan) {
        log.info("【风控-保守派】辩论分析中 标的={}", symbol);
        progressHandler.sendDebateUpdate(analysisId, "risk", "conservative", "保守派正在分析...", 1);
        
        try {
            String prompt = buildDebatePrompt(symbol, date, marketReport, sentimentReport,
                                              fundamentalsReport, tradePlan, "conservative");
            
            ConservativeDebater debater = AiServices.create(ConservativeDebater.class, deepThinkingModel);
            String analysis = debater.debate(prompt);
            
            progressHandler.sendDebateUpdate(analysisId, "risk", "conservative", analysis, 1);
            return analysis;
            
        } catch (Exception e) {
            log.error("【风控-保守派】失败 原因：{}", e.getMessage());
            throw new RuntimeException("保守派分析失败", e);
        }
    }

    /**
     * 中立派分析
     */
    public String neutralAnalysis(String analysisId, String symbol, String date,
                                   String marketReport, String sentimentReport,
                                   String fundamentalsReport, String tradePlan,
                                   String aggressiveView, String conservativeView) {
        log.info("【风控-中立派】辩论分析中 标的={}", symbol);
        progressHandler.sendDebateUpdate(analysisId, "risk", "neutral", "中立派正在整合观点...", 2);
        
        try {
            String prompt = buildNeutralPrompt(symbol, date, marketReport, sentimentReport,
                                               fundamentalsReport, tradePlan, 
                                               aggressiveView, conservativeView);
            
            NeutralDebater debater = AiServices.create(NeutralDebater.class, deepThinkingModel);
            String analysis = debater.debate(prompt);
            
            progressHandler.sendDebateUpdate(analysisId, "risk", "neutral", analysis, 2);
            return analysis;
            
        } catch (Exception e) {
            log.error("【风控-中立派】失败 原因：{}", e.getMessage());
            throw new RuntimeException("中立派分析失败", e);
        }
    }

    /**
     * 构建辩论提示
     */
    private String buildDebatePrompt(String symbol, String date,
                                     String marketReport, String sentimentReport,
                                     String fundamentalsReport, String tradePlan,
                                     String stance) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对股票 ").append(symbol).append(" 的交易计划进行");
        prompt.append(stance.equals("aggressive") ? "激进派" : "保守派").append("风险分析。\n\n");
        
        prompt.append("【市场分析】\n").append(marketReport).append("\n\n");
        prompt.append("【情绪分析】\n").append(sentimentReport).append("\n\n");
        prompt.append("【基本面分析】\n").append(fundamentalsReport).append("\n\n");
        prompt.append("【交易计划】\n").append(tradePlan).append("\n\n");
        
        return prompt.toString();
    }

    /**
     * 构建中立派提示
     */
    private String buildNeutralPrompt(String symbol, String date,
                                      String marketReport, String sentimentReport,
                                      String fundamentalsReport, String tradePlan,
                                      String aggressiveView, String conservativeView) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对股票 ").append(symbol).append(" 的交易计划进行中立派风险分析。\n\n");
        
        prompt.append("【市场分析】\n").append(marketReport).append("\n\n");
        prompt.append("【情绪分析】\n").append(sentimentReport).append("\n\n");
        prompt.append("【基本面分析】\n").append(fundamentalsReport).append("\n\n");
        prompt.append("【交易计划】\n").append(tradePlan).append("\n\n");
        prompt.append("【激进派观点】\n").append(aggressiveView).append("\n\n");
        prompt.append("【保守派观点】\n").append(conservativeView).append("\n\n");
        
        return prompt.toString();
    }

    /**
     * 激进派接口
     */
    interface AggressiveDebater {
        @SystemMessage("""
            你是一位激进派风险分析师，倾向于承担更高风险以追求更高收益。
            
            观点特点：
            1. 看好市场机会，愿意承担风险
            2. 关注潜在收益，对风险容忍度较高
            3. 支持积极的交易策略
            4. 认为当前风险可控，机会大于风险
            
            请从激进派角度分析交易计划，重点阐述：
            - 为什么当前是入场好时机
            - 潜在收益空间有多大
            - 风险是否被过度担忧
            - 如何优化交易计划以获取更高收益
            """)
        @UserMessage("{{it}}")
        String debate(String prompt);
    }

    /**
     * 保守派接口
     */
    interface ConservativeDebater {
        @SystemMessage("""
            你是一位保守派风险分析师，优先考虑资金安全，风险控制第一。
            
            观点特点：
            1. 谨慎对待市场风险，宁可错过也不做错
            2. 关注下行风险，对不确定性保持警惕
            3. 建议保守的交易策略
            4. 认为当前风险不容忽视
            
            请从保守派角度分析交易计划，重点阐述：
            - 当前存在哪些风险隐患
            - 为什么需要更谨慎
            - 潜在亏损可能有多大
            - 如何加强风险控制
            """)
        @UserMessage("{{it}}")
        String debate(String prompt);
    }

    /**
     * 中立派接口
     */
    interface NeutralDebater {
        @SystemMessage("""
            你是一位中立派风险分析师，负责平衡激进派和保守派的观点。
            
            职责：
            1. 客观评估激进派和保守派的论点
            2. 识别双方观点的合理性和盲点
            3. 提出平衡的风险评估
            4. 给出综合性的风险建议
            
            请从平衡角度分析，重点阐述：
            - 激进派观点的合理之处
            - 保守派观点的合理之处
            - 双方观点的盲点
            - 平衡后的风险评估
            - 折中的交易建议
            """)
        @UserMessage("{{it}}")
        String debate(String prompt);
    }
}
