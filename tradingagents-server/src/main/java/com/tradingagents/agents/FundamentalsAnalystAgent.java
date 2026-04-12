package com.tradingagents.agents;

import com.tradingagents.data.model.FundamentalData;
import com.tradingagents.data.service.StockDataService;
import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基本面分析师 Agent
 * 负责财务和基本面分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundamentalsAnalystAgent {

    private final ChatLanguageModel quickThinkingModel;
    private final StockDataService stockDataService;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 执行基本面分析
     */
    public String analyze(String analysisId, String symbol, String date) {
        log.info("Fundamentals Analyst starting analysis for {} on {}", symbol, date);
        progressHandler.sendAgentStatus(analysisId, "fundamentals_analyst", "running", "正在获取财务数据...");
        
        try {
            // 解析日期，获取最近财报期
            LocalDate analysisDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String period = getLatestPeriod(analysisDate);
            
            // 获取基本面数据
            FundamentalData fundamentalData = stockDataService.getFundamentalData(symbol, period).block();
            
            progressHandler.sendAgentStatus(analysisId, "fundamentals_analyst", "running", "正在分析财务指标...");
            
            // 构建分析提示
            String prompt = buildAnalysisPrompt(symbol, date, fundamentalData, period);
            
            // 调用 LLM 进行分析
            FundamentalsAnalyst analyst = AiServices.create(FundamentalsAnalyst.class, quickThinkingModel);
            String report = analyst.analyze(prompt);
            
            progressHandler.sendAgentStatus(analysisId, "fundamentals_analyst", "completed", "基本面分析完成");
            progressHandler.sendReport(analysisId, "fundamentals_report", report);
            
            log.info("Fundamentals Analyst completed analysis for {}", symbol);
            return report;
            
        } catch (Exception e) {
            log.error("Fundamentals Analyst failed for {}: {}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "fundamentals_analyst", e.getMessage());
            throw new RuntimeException("基本面分析失败", e);
        }
    }

    /**
     * 构建分析提示
     */
    private String buildAnalysisPrompt(String symbol, String date, FundamentalData data, String period) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对股票 ").append(symbol).append(" 在 ").append(date).append(" 进行基本面分析。\n\n");
        prompt.append("财报期: ").append(period).append("\n\n");
        
        // 盈利能力
        prompt.append("【盈利能力指标】\n");
        if (data.getRoe() != null) {
            prompt.append(String.format("净资产收益率(ROE): %.2f%%\n", data.getRoe()));
        }
        if (data.getRoa() != null) {
            prompt.append(String.format("总资产报酬率(ROA): %.2f%%\n", data.getRoa()));
        }
        if (data.getGrossMargin() != null) {
            prompt.append(String.format("毛利率: %.2f%%\n", data.getGrossMargin()));
        }
        if (data.getNetMargin() != null) {
            prompt.append(String.format("净利率: %.2f%%\n", data.getNetMargin()));
        }
        prompt.append("\n");
        
        // 偿债能力
        prompt.append("【偿债能力指标】\n");
        if (data.getCurrentRatio() != null) {
            prompt.append(String.format("流动比率: %.2f\n", data.getCurrentRatio()));
        }
        if (data.getQuickRatio() != null) {
            prompt.append(String.format("速动比率: %.2f\n", data.getQuickRatio()));
        }
        if (data.getDebtToAsset() != null) {
            prompt.append(String.format("资产负债率: %.2f%%\n", data.getDebtToAsset()));
        }
        prompt.append("\n");
        
        // 估值指标
        prompt.append("【估值指标】\n");
        if (data.getPeRatio() != null) {
            prompt.append(String.format("市盈率(PE): %.2f\n", data.getPeRatio()));
        }
        if (data.getPbRatio() != null) {
            prompt.append(String.format("市净率(PB): %.2f\n", data.getPbRatio()));
        }
        prompt.append("\n");
        
        // 现金流
        prompt.append("【现金流量】\n");
        if (data.getOperatingCashFlow() != null) {
            prompt.append(String.format("经营活动现金流: %.2f 万元\n", data.getOperatingCashFlow()));
        }
        if (data.getInvestingCashFlow() != null) {
            prompt.append(String.format("投资活动现金流: %.2f 万元\n", data.getInvestingCashFlow()));
        }
        if (data.getFinancingCashFlow() != null) {
            prompt.append(String.format("筹资活动现金流: %.2f 万元\n", data.getFinancingCashFlow()));
        }
        prompt.append("\n");
        
        prompt.append("请提供以下分析：\n");
        prompt.append("1. 盈利能力分析（ROE、毛利率、净利率等）\n");
        prompt.append("2. 偿债能力分析（资产负债率、流动比率等）\n");
        prompt.append("3. 成长性分析（收入、利润增长趋势）\n");
        prompt.append("4. 现金流分析（经营现金流质量）\n");
        prompt.append("5. 估值分析（PE、PB 合理性）\n");
        prompt.append("6. 基本面综合评价（看多/看空/中性）\n");
        prompt.append("7. 财务风险提示\n");
        
        return prompt.toString();
    }

    /**
     * 获取最近财报期
     */
    private String getLatestPeriod(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        
        // 根据当前日期返回最近的财报期
        if (month >= 10) {
            return year + "0930";  // 三季报
        } else if (month >= 7) {
            return year + "0630";  // 半年报
        } else if (month >= 4) {
            return year + "0331";  // 一季报
        } else {
            return (year - 1) + "1231";  // 上年年报
        }
    }

    /**
     * 基本面分析师接口定义
     */
    interface FundamentalsAnalyst {
        @SystemMessage("""
            你是一位专业的基本面分析专家，擅长通过财务报表和估值指标分析公司价值。
            
            分析要求：
            1. 客观分析财务数据，关注盈利质量和可持续性
            2. 结合行业特点进行横向对比分析
            3. 关注现金流质量和资本结构
            4. 评估估值合理性
            5. 给出明确的基本面观点（看多/看空/中性）
            6. 提示财务风险（高负债、现金流恶化等）
            
            输出格式：
            ## 盈利能力分析
            [分析内容]
            
            ## 偿债能力分析
            [分析内容]
            
            ## 成长性分析
            [分析内容]
            
            ## 现金流分析
            [分析内容]
            
            ## 估值分析
            [分析内容]
            
            ## 综合评价
            **观点**: [看多/看空/中性]
            **置信度**: [高/中/低]
            **估值状态**: [低估/合理/高估]
            
            ## 风险提示
            [财务风险提示]
            """)
        @UserMessage("{{it}}")
        String analyze(String prompt);
    }
}
