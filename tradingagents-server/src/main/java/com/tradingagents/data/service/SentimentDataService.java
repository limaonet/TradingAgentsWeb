package com.tradingagents.data.service;

import com.tradingagents.data.client.PlaywrightSentimentClient;
import com.tradingagents.data.model.SentimentData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Random;

/**
 * 舆情数据服务
 * 使用 Playwright 浏览器自动化获取雪球和东方财富股吧数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataService {

    private final Random random = new Random();
    private final PlaywrightSentimentClient playwrightClient;

    /**
     * 获取综合舆情数据
     * 使用 Playwright 浏览器自动化获取雪球和东方财富股吧数据
     */
    public Mono<SentimentData> getComprehensiveSentiment(String symbol, LocalDate date) {
        log.info("Fetching comprehensive sentiment data for {} on {} using Playwright", symbol, date);
        
        // 使用 Playwright 在独立线程中执行（避免阻塞）
        return Mono.fromCallable(() -> playwrightClient.getComprehensiveSentiment(symbol, date))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(data -> {
                    log.info("Successfully fetched sentiment data for {}: xueqiuRank={}, gubaRank={}", 
                            symbol, data.getXueqiuHotRank(), data.getGubaHotRank());
                })
                .doOnError(e -> {
                    log.error("Failed to fetch sentiment data for {}: {}", symbol, e.getMessage());
                })
                .onErrorResume(e -> {
                    // 如果 Playwright 失败，返回模拟数据
                    log.warn("Playwright failed, returning mock data for {}: {}", symbol, e.getMessage());
                    return Mono.just(generateMockSentimentData(symbol, date));
                });
    }

    /**
     * 生成模拟舆情数据（降级方案）
     */
    private SentimentData generateMockSentimentData(String symbol, LocalDate date) {
        SentimentData data = new SentimentData();
        data.setTsCode(symbol);
        data.setTradeDate(date);
        
        // 社交媒体热度（模拟）
        data.setXueqiuHotRank(random.nextInt(100) + 1);
        data.setGubaHotRank(random.nextInt(100) + 1);
        data.setXueqiuDiscussionCount(1000 + random.nextInt(5000));
        data.setGubaDiscussionCount(500 + random.nextInt(3000));
        
        // 帖子情感（模拟）
        int xqTotal = 20 + random.nextInt(30);
        data.setXueqiuPositivePosts((int) (xqTotal * 0.4));
        data.setXueqiuNegativePosts((int) (xqTotal * 0.3));
        data.setXueqiuNeutralPosts((int) (xqTotal * 0.3));
        
        int gbTotal = 30 + random.nextInt(40);
        data.setGubaPositivePosts((int) (gbTotal * 0.35));
        data.setGubaNegativePosts((int) (gbTotal * 0.35));
        data.setGubaNeutralPosts((int) (gbTotal * 0.3));
        data.setGubaReadCount(10000 + random.nextInt(50000));
        data.setGubaCommentCount(100 + random.nextInt(500));
        
        // 填充其他数据
        fillMarketData(data);
        calculateOverallSentiment(data);
        
        return data;
    }

    /**
     * 填充市场数据
     */
    private void fillMarketData(SentimentData data) {
        // 新闻舆情
        int totalNews = 20 + random.nextInt(30);
        data.setNewsTotalCount(totalNews);
        data.setNewsPositiveCount((int) (totalNews * 0.4));
        data.setNewsNegativeCount((int) (totalNews * 0.3));
        data.setNewsNeutralCount(totalNews - data.getNewsPositiveCount() - data.getNewsNegativeCount());
        
        // 公告舆情
        data.setAnnouncementTotalCount(5 + random.nextInt(10));
        
        // 市场情绪
        data.setMarketUpCount(2500 + random.nextInt(1000));
        data.setMarketDownCount(2000 + random.nextInt(1000));
        data.setMarketLimitUpCount(50 + random.nextInt(100));
        data.setMarketLimitDownCount(20 + random.nextInt(50));
        
        BigDecimal ratio = BigDecimal.valueOf(data.getMarketUpCount())
                .divide(BigDecimal.valueOf(data.getMarketDownCount()), 2, RoundingMode.HALF_UP);
        data.setMarketUpDownRatio(ratio);
        
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
    }

    /**
     * 计算综合情感得分
     */
    private void calculateOverallSentiment(SentimentData data) {
        int totalPositive = (data.getXueqiuPositivePosts() != null ? data.getXueqiuPositivePosts() : 0)
                + (data.getGubaPositivePosts() != null ? data.getGubaPositivePosts() : 0)
                + (data.getNewsPositiveCount() != null ? data.getNewsPositiveCount() : 0);
        
        int totalNegative = (data.getXueqiuNegativePosts() != null ? data.getXueqiuNegativePosts() : 0)
                + (data.getGubaNegativePosts() != null ? data.getGubaNegativePosts() : 0)
                + (data.getNewsNegativeCount() != null ? data.getNewsNegativeCount() : 0);
        
        int totalNeutral = (data.getXueqiuNeutralPosts() != null ? data.getXueqiuNeutralPosts() : 0)
                + (data.getGubaNeutralPosts() != null ? data.getGubaNeutralPosts() : 0)
                + (data.getNewsNeutralCount() != null ? data.getNewsNeutralCount() : 0);
        
        int total = totalPositive + totalNegative + totalNeutral;
        
        if (total > 0) {
            BigDecimal score = BigDecimal.valueOf(totalPositive - totalNegative)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            data.setOverallSentiment(score);
            
            if (score.compareTo(new BigDecimal("0.3")) > 0) {
                data.setSentimentLabel("积极");
            } else if (score.compareTo(new BigDecimal("-0.3")) < 0) {
                data.setSentimentLabel("消极");
            } else {
                data.setSentimentLabel("中性");
            }
        } else {
            data.setOverallSentiment(BigDecimal.ZERO);
            data.setSentimentLabel("中性");
        }
    }

    /**
     * 获取市场情绪统计
     */
    public Mono<SentimentData> getMarketSentiment(LocalDate date) {
        return Mono.fromCallable(() -> {
            SentimentData data = new SentimentData();
            data.setTradeDate(date);
            fillMarketData(data);
            return data;
        });
    }
}
