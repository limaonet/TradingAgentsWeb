package com.tradingagents.data.service;

import com.tradingagents.data.model.SentimentData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Random;

/**
 * 舆情数据服务
 * 整合东方财富、雪球、股吧等数据源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataService {

    private final Random random = new Random();

    /**
     * 获取综合舆情数据
     */
    public Mono<SentimentData> getComprehensiveSentiment(String symbol, LocalDate date) {
        return Mono.fromCallable(() -> {
            // 模拟生成舆情数据
            // 实际项目中应从东方财富股吧、雪球等平台获取
            return generateMockSentimentData(symbol, date);
        });
    }

    /**
     * 获取市场情绪统计
     */
    public Mono<SentimentData> getMarketSentiment(LocalDate date) {
        return Mono.fromCallable(() -> {
            SentimentData data = new SentimentData();
            data.setTradeDate(date);
            
            // 模拟市场涨跌统计
            int upCount = 2500 + random.nextInt(1000);
            int downCount = 2000 + random.nextInt(1000);
            int limitUp = 50 + random.nextInt(100);
            int limitDown = 20 + random.nextInt(50);
            
            data.setMarketUpCount(upCount);
            data.setMarketDownCount(downCount);
            data.setMarketLimitUpCount(limitUp);
            data.setMarketLimitDownCount(limitDown);
            
            BigDecimal ratio = BigDecimal.valueOf(upCount)
                    .divide(BigDecimal.valueOf(downCount), 2, RoundingMode.HALF_UP);
            data.setMarketUpDownRatio(ratio);
            
            // 判断市场情绪
            if (ratio.compareTo(new BigDecimal("1.5")) > 0) {
                data.setMarketSentiment("极度乐观");
            } else if (ratio.compareTo(new BigDecimal("1.2")) > 0) {
                data.setMarketSentiment("乐观");
            } else if (ratio.compareTo(new BigDecimal("0.8")) > 0) {
                data.setMarketSentiment("中性");
            } else if (ratio.compareTo(new BigDecimal("0.5")) > 0) {
                data.setMarketSentiment("悲观");
            } else {
                data.setMarketSentiment("极度悲观");
            }
            
            return data;
        });
    }

    /**
     * 生成模拟舆情数据
     */
    private SentimentData generateMockSentimentData(String symbol, LocalDate date) {
        SentimentData data = new SentimentData();
        data.setTsCode(symbol);
        data.setTradeDate(date);
        
        // 新闻舆情
        int totalNews = 20 + random.nextInt(30);
        int positiveNews = (int) (totalNews * (0.3 + random.nextDouble() * 0.4));
        int negativeNews = (int) (totalNews * (0.2 + random.nextDouble() * 0.3));
        int neutralNews = totalNews - positiveNews - negativeNews;
        
        data.setNewsTotalCount(totalNews);
        data.setNewsPositiveCount(positiveNews);
        data.setNewsNegativeCount(negativeNews);
        data.setNewsNeutralCount(neutralNews);
        
        // 公告舆情
        data.setAnnouncementTotalCount(5 + random.nextInt(10));
        
        // 社交媒体热度
        data.setXueqiuHotRank(random.nextInt(100) + 1);
        data.setGubaHotRank(random.nextInt(100) + 1);
        data.setXueqiuDiscussionCount(1000 + random.nextInt(5000));
        data.setGubaDiscussionCount(500 + random.nextInt(3000));
        
        // 计算综合情感得分 (-1 到 1)
        BigDecimal sentimentScore = calculateSentimentScore(data);
        data.setOverallSentiment(sentimentScore);
        data.setSentimentLabel(getSentimentLabel(sentimentScore));
        
        // 市场情绪
        data.setMarketUpCount(2500 + random.nextInt(1000));
        data.setMarketDownCount(2000 + random.nextInt(1000));
        data.setMarketLimitUpCount(50 + random.nextInt(100));
        data.setMarketLimitDownCount(20 + random.nextInt(50));
        
        BigDecimal ratio = BigDecimal.valueOf(data.getMarketUpCount())
                .divide(BigDecimal.valueOf(data.getMarketDownCount()), 2, RoundingMode.HALF_UP);
        data.setMarketUpDownRatio(ratio);
        data.setMarketSentiment(getMarketSentimentLabel(ratio));
        
        return data;
    }

    /**
     * 计算情感得分
     */
    private BigDecimal calculateSentimentScore(SentimentData data) {
        if (data.getNewsTotalCount() == 0) return BigDecimal.ZERO;
        
        BigDecimal positiveWeight = BigDecimal.valueOf(data.getNewsPositiveCount())
                .multiply(new BigDecimal("1.0"))
                .divide(BigDecimal.valueOf(data.getNewsTotalCount()), 4, RoundingMode.HALF_UP);
        
        BigDecimal negativeWeight = BigDecimal.valueOf(data.getNewsNegativeCount())
                .multiply(new BigDecimal("-1.0"))
                .divide(BigDecimal.valueOf(data.getNewsTotalCount()), 4, RoundingMode.HALF_UP);
        
        return positiveWeight.add(negativeWeight);
    }

    /**
     * 获取情感标签
     */
    private String getSentimentLabel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("0.3")) > 0) return "积极";
        if (score.compareTo(new BigDecimal("-0.3")) < 0) return "消极";
        return "中性";
    }

    /**
     * 获取市场情绪标签
     */
    private String getMarketSentimentLabel(BigDecimal ratio) {
        if (ratio.compareTo(new BigDecimal("1.5")) > 0) return "极度乐观";
        if (ratio.compareTo(new BigDecimal("1.2")) > 0) return "乐观";
        if (ratio.compareTo(new BigDecimal("0.8")) > 0) return "中性";
        if (ratio.compareTo(new BigDecimal("0.5")) > 0) return "悲观";
        return "极度悲观";
    }
}
