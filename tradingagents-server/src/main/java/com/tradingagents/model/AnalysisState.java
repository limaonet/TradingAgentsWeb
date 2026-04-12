package com.tradingagents.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 分析状态
 * 跟踪整个分析流程的状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisState {
    
    /**
     * 分析ID
     */
    private String analysisId;
    
    /**
     * 股票代码
     */
    private String ticker;
    
    /**
     * 分析日期
     */
    private String date;
    
    /**
     * 状态: pending, running, completed, error
     */
    private String status;
    
    /**
     * 当前执行的 Agent
     */
    private String currentAgent;
    
    /**
     * 进度百分比
     */
    private Integer progress;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 完成时间
     */
    private LocalDateTime endTime;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    // ============ 分析师报告 ============
    
    private String marketReport;
    private String sentimentReport;
    private String newsReport;
    private String fundamentalsReport;
    
    // ============ 研究辩论 ============
    
    private String bullResearch;
    private String bearResearch;
    private String researchManagerDecision;
    
    // ============ 交易计划 ============
    
    private String traderInvestmentPlan;
    
    // ============ 风控辩论 ============
    
    private String aggressiveAnalysis;
    private String conservativeAnalysis;
    private String neutralAnalysis;
    private String portfolioManagerDecision;
    
    // ============ 最终决策 ============
    
    private String finalTradeDecision;
    
    /**
     * Agent 状态映射
     */
    @Builder.Default
    private Map<String, String> agentStatuses = new HashMap<>();
    
    /**
     * 获取所有报告
     */
    public Map<String, String> getAllReports() {
        Map<String, String> reports = new HashMap<>();
        reports.put("marketReport", marketReport);
        reports.put("sentimentReport", sentimentReport);
        reports.put("newsReport", newsReport);
        reports.put("fundamentalsReport", fundamentalsReport);
        reports.put("investmentPlan", researchManagerDecision);
        reports.put("traderInvestmentPlan", traderInvestmentPlan);
        reports.put("finalTradeDecision", finalTradeDecision);
        return reports;
    }
    
    /**
     * 更新 Agent 状态
     */
    public void updateAgentStatus(String agent, String status) {
        agentStatuses.put(agent, status);
    }
    
    /**
     * 获取 Agent 状态
     */
    public String getAgentStatus(String agent) {
        return agentStatuses.getOrDefault(agent, "pending");
    }
}
