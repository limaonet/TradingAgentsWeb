package com.tradingagents.agents;

import com.tradingagents.data.model.StockData;
import com.tradingagents.data.model.TechnicalIndicators;
import com.tradingagents.data.service.StockDataService;
import com.tradingagents.websocket.AnalysisProgressHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 市场分析师 Agent
 * 负责技术分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketAnalystAgent {

    private final ChatLanguageModel quickThinkingModel;
    private final StockDataService stockDataService;
    private final AnalysisProgressHandler progressHandler;

    /**
     * 执行市场分析
     */
    public String analyze(String analysisId, String symbol, String date) {
        log.info("Market Analyst starting analysis for {} on {}", symbol, date);
        progressHandler.sendAgentStatus(analysisId, "market_analyst", "running", "正在获取市场数据...");
        
        try {
            // 解析日期
            LocalDate analysisDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate startDate = analysisDate.minusDays(60); // 获取60天历史数据
            
            // 获取历史行情数据
            List<StockData> historyData = stockDataService.getStockHistory(symbol, startDate, analysisDate).block();
            
            // 获取技术指标
            TechnicalIndicators indicators = stockDataService.getTechnicalIndicators(symbol, analysisDate).block();
            
            progressHandler.sendAgentStatus(analysisId, "market_analyst", "running", "正在分析技术指标...");
            
            // 构建分析提示
            String prompt = buildAnalysisPrompt(symbol, date, historyData, indicators);
            
            // 调用 LLM 进行分析
            MarketAnalyst analyst = AiServices.create(MarketAnalyst.class, quickThinkingModel);
            String report = analyst.analyze(prompt);
            
            progressHandler.sendAgentStatus(analysisId, "market_analyst", "completed", "市场分析完成");
            progressHandler.sendReport(analysisId, "market_report", report);
            
            log.info("Market Analyst completed analysis for {}", symbol);
            return report;
            
        } catch (Exception e) {
            log.error("Market Analyst failed for {}: {}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "market_analyst", e.getMessage());
            throw new RuntimeException("市场分析失败", e);
        }
    }

    /**
     * 构建分析提示
     */
    private String buildAnalysisPrompt(String symbol, String date, 
                                       List<StockData> historyData, 
                                       TechnicalIndicators indicators) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对股票 ").append(symbol).append(" 在 ").append(date).append(" 进行技术分析。\n\n");
        
        // 添加最近5天行情
        prompt.append("【最近5个交易日行情】\n");
        int count = Math.min(5, historyData != null ? historyData.size() : 0);
        for (int i = 0; i < count; i++) {
            StockData data = historyData.get(historyData.size() - count + i);
            prompt.append(String.format("%s: 开盘 %.2f, 收盘 %.2f, 最高 %.2f, 最低 %.2f, 涨跌 %.2f%%\n",
                    data.getTradeDate(),
                    data.getOpen(),
                    data.getClose(),
                    data.getHigh(),
                    data.getLow(),
                    data.getPctChange()));
        }
        
        // 添加技术指标
        if (indicators != null) {
            prompt.append("\n【技术指标】\n");
            if (indicators.getMacd() != null) {
                prompt.append(String.format("MACD: %.4f (信号线: %.4f, 柱状图: %.4f)\n",
                        indicators.getMacd(), indicators.getMacdSignal(), indicators.getMacdHist()));
            }
            if (indicators.getRsi6() != null) {
                prompt.append(String.format("RSI: 6日 %.2f, 12日 %.2f, 24日 %.2f\n",
                        indicators.getRsi6(), indicators.getRsi12(), indicators.getRsi24()));
            }
            if (indicators.getK() != null) {
                prompt.append(String.format("KDJ: K=%.2f, D=%.2f, J=%.2f\n",
                        indicators.getK(), indicators.getD(), indicators.getJ()));
            }
            if (indicators.getMa5() != null) {
                prompt.append(String.format("均线: MA5=%.2f, MA10=%.2f, MA20=%.2f\n",
                        indicators.getMa5(), indicators.getMa10(), indicators.getMa20()));
            }
        }
        
        prompt.append("\n请提供以下分析：\n");
        prompt.append("1. 趋势分析（短期、中期趋势判断）\n");
        prompt.append("2. 技术指标解读（MACD、RSI、KDJ、均线系统等）\n");
        prompt.append("3. 支撑与阻力位分析\n");
        prompt.append("4. 成交量分析\n");
        prompt.append("5. 技术面综合评价（看多/看空/中性）\n");
        prompt.append("6. 风险提示\n");
        
        return prompt.toString();
    }

    /**
     * 市场分析师接口定义
     */
    interface MarketAnalyst {
        @SystemMessage("""
            你是一位专业的技术分析专家，擅长通过技术指标和图表分析股票走势。
            
            分析要求：
            1. 客观分析技术面，不要带有主观臆测
            2. 结合多个技术指标进行综合判断
            3. 明确指出支撑和阻力位
            4. 给出明确的技术面观点（看多/看空/中性）
            5. 列出关键风险提示
            
            输出格式：
            ## 趋势分析
            [分析内容]
            
            ## 技术指标解读
            [分析内容]
            
            ## 支撑与阻力
            [分析内容]
            
            ## 成交量分析
            [分析内容]
            
            ## 综合评价
            **观点**: [看多/看空/中性]
            **置信度**: [高/中/低]
            
            ## 风险提示
            [风险提示]
            """)
        @UserMessage("{{it}}")
        String analyze(String prompt);
    }
}
