package com.tradingagents.agents;

import com.tradingagents.data.model.SentimentData;
import com.tradingagents.data.service.SentimentDataService;
import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 情绪分析师 Agent
 * 负责舆情和情绪分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentAnalystAgent {

    private final ChatLanguageModel quickThinkingModel;
    private final SentimentDataService sentimentDataService;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 执行情绪分析
     */
    public String analyze(String analysisId, String symbol, String date) {
        log.info("Sentiment Analyst starting analysis for {} on {}", symbol, date);
        progressHandler.sendAgentStatus(analysisId, "sentiment_analyst", "running", "正在获取舆情数据...");
        
        try {
            // 解析日期
            LocalDate analysisDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // 获取舆情数据
            SentimentData sentimentData = sentimentDataService.getComprehensiveSentiment(symbol, analysisDate).block();
            
            progressHandler.sendAgentStatus(analysisId, "sentiment_analyst", "running", "正在分析市场情绪...");
            
            // 构建分析提示
            String prompt = buildAnalysisPrompt(symbol, date, sentimentData);
            
            // 调用 LLM 进行分析
            SentimentAnalyst analyst = AiServices.create(SentimentAnalyst.class, quickThinkingModel);
            String report = analyst.analyze(prompt);
            
            progressHandler.sendAgentStatus(analysisId, "sentiment_analyst", "completed", "情绪分析完成");
            progressHandler.sendReport(analysisId, "sentiment_report", report);
            
            log.info("Sentiment Analyst completed analysis for {}", symbol);
            return report;
            
        } catch (Exception e) {
            log.error("Sentiment Analyst failed for {}: {}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "sentiment_analyst", e.getMessage());
            throw new RuntimeException("情绪分析失败", e);
        }
    }

    /**
     * 构建分析提示
     */
    private String buildAnalysisPrompt(String symbol, String date, SentimentData data) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对股票 ").append(symbol).append(" 在 ").append(date).append(" 进行舆情情绪分析。\n\n");
        
        // 市场情绪
        prompt.append("【市场情绪数据】\n");
        prompt.append(String.format("上涨家数: %d\n", data.getMarketUpCount()));
        prompt.append(String.format("下跌家数: %d\n", data.getMarketDownCount()));
        prompt.append(String.format("涨停家数: %d\n", data.getMarketLimitUpCount()));
        prompt.append(String.format("跌停家数: %d\n", data.getMarketLimitDownCount()));
        prompt.append(String.format("涨跌比: %.2f\n", data.getMarketUpDownRatio()));
        prompt.append(String.format("整体情绪: %s\n\n", data.getMarketSentiment()));
        
        // 新闻舆情
        prompt.append("【新闻舆情统计】\n");
        prompt.append(String.format("新闻总数: %d\n", data.getNewsTotalCount()));
        prompt.append(String.format("正面新闻: %d\n", data.getNewsPositiveCount()));
        prompt.append(String.format("负面新闻: %d\n", data.getNewsNegativeCount()));
        prompt.append(String.format("中性新闻: %d\n\n", data.getNewsNeutralCount()));
        
        // 公告舆情
        prompt.append("【公告舆情统计】\n");
        prompt.append(String.format("公告总数: %d\n\n", data.getAnnouncementTotalCount()));
        
        // 社交媒体热度
        if (data.getXueqiuHotRank() != null || data.getGubaHotRank() != null) {
            prompt.append("【社交媒体热度】\n");
            if (data.getXueqiuHotRank() != null) {
                prompt.append(String.format("雪球热股排名: %d\n", data.getXueqiuHotRank()));
            }
            if (data.getGubaHotRank() != null) {
                prompt.append(String.format("东方财富股吧排名: %d\n", data.getGubaHotRank()));
            }
            prompt.append("\n");
        }
        
        // 综合情感得分
        prompt.append(String.format("【综合情感得分】%.2f (%s)\n\n", 
                data.getOverallSentiment(), data.getSentimentLabel()));
        
        prompt.append("请提供以下分析：\n");
        prompt.append("1. 市场情绪分析（整体氛围、投资者情绪）\n");
        prompt.append("2. 新闻舆情分析（媒体报道倾向、重要新闻影响）\n");
        prompt.append("3. 公告解读（重要公告类型及影响）\n");
        prompt.append("4. 社交媒体热度分析（投资者关注度、讨论热度）\n");
        prompt.append("5. 情绪面综合评价（看多/看空/中性）\n");
        prompt.append("6. 情绪风险提示（情绪过热/过冷风险）\n");
        
        return prompt.toString();
    }

    /**
     * 情绪分析师接口定义
     */
    interface SentimentAnalyst {
        @SystemMessage("""
            你是一位专业的市场情绪分析专家，擅长通过舆情数据、新闻、社交媒体等渠道分析投资者情绪。
            
            分析要求：
            1. 客观分析市场情绪，识别情绪极端情况
            2. 关注新闻舆情的倾向性和重要事件
            3. 分析社交媒体热度和投资者关注度
            4. 给出明确的情绪面观点（看多/看空/中性）
            5. 提示情绪过热或过冷的风险
            
            输出格式：
            ## 市场情绪分析
            [分析内容]
            
            ## 新闻舆情分析
            [分析内容]
            
            ## 公告解读
            [分析内容]
            
            ## 社交媒体热度
            [分析内容]
            
            ## 综合评价
            **观点**: [看多/看空/中性]
            **置信度**: [高/中/低]
            **情绪状态**: [正常/过热/过冷]
            
            ## 风险提示
            [情绪风险提示]
            """)
        @UserMessage("{{it}}")
        String analyze(String prompt);
    }
}
