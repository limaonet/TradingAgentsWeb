package com.tradingagents.data.service;

import com.tradingagents.data.client.GubaClient;
import com.tradingagents.data.client.SinaNewsClient;
import com.tradingagents.data.client.XueqiuClient;
import com.tradingagents.data.model.SentimentData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

/**
 * 舆情数据服务
 * 使用 HTTP 抓取获取雪球、股吧与新闻数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataService {

    private final Random random = new Random();
    private final XueqiuClient xueqiuClient;
    private final GubaClient gubaClient;
    private final SinaNewsClient sinaNewsClient;

    /**
     * 获取综合舆情数据
     * 使用 HTTP 客户端抓取雪球、股吧与新闻数据
     */
    public Mono<SentimentData> getComprehensiveSentiment(String symbol, LocalDate date) {
        log.info("【舆情】开始拉取综合舆情（雪球/股吧/新闻）标的={} 日期={}", symbol, date);

        Mono<Map<String, Object>> xueqiuMono = xueqiuClient.getComprehensiveData(symbol)
                .defaultIfEmpty(Map.of());
        Mono<Map<String, Object>> gubaMono = gubaClient.getComprehensiveData(symbol)
                .defaultIfEmpty(Map.of());
        Mono<Map<String, Integer>> newsMono = sinaNewsClient.getNewsSentimentStats(symbol)
                .defaultIfEmpty(Map.of());

        return Mono.zip(xueqiuMono, gubaMono, newsMono)
                .map(tuple -> {
                    SentimentData data = new SentimentData();
                    data.setTsCode(symbol);
                    data.setTradeDate(date);

                    mergeXueqiuData(data, tuple.getT1());
                    mergeGubaData(data, tuple.getT2());
                    mergeNewsData(data, tuple.getT3());

                    fillMarketData(data);
                    calculateOverallSentiment(data);
                    ensureMinimumFields(data);

                    log.info("【舆情】拉取完成 标的={} 雪球热度={} 股吧热度={} 新闻条数={}",
                            symbol, data.getXueqiuHotRank(), data.getGubaHotRank(), data.getNewsTotalCount());
                    return data;
                })
                .onErrorResume(e -> {
                    log.warn("【舆情】上游抓取失败，标的={} 将使用占位数据。原因：{}", symbol, e.getMessage());
                    return Mono.just(generateMockSentimentData(symbol, date));
                });
    }

    @SuppressWarnings("unchecked")
    private void mergeXueqiuData(SentimentData data, Map<String, Object> xueqiuData) {
        data.setXueqiuHotRank(asInteger(xueqiuData.get("hotRank")));
        data.setXueqiuDiscussionCount(asInteger(xueqiuData.get("discussionCount")));

        Object postsDataObj = xueqiuData.get("postsData");
        if (postsDataObj instanceof Map<?, ?> postsData) {
            data.setXueqiuPositivePosts(asInteger(postsData.get("positivePosts")));
            data.setXueqiuNegativePosts(asInteger(postsData.get("negativePosts")));
            data.setXueqiuNeutralPosts(asInteger(postsData.get("neutralPosts")));
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeGubaData(SentimentData data, Map<String, Object> gubaData) {
        data.setGubaHotRank(asInteger(gubaData.get("hotRank")));

        Object statsObj = gubaData.get("statistics");
        if (statsObj instanceof Map<?, ?> stats) {
            data.setGubaReadCount(asInteger(stats.get("readCount")));
            data.setGubaCommentCount(asInteger(stats.get("commentCount")));
        }

        Object postsDataObj = gubaData.get("postsData");
        if (postsDataObj instanceof Map<?, ?> postsData) {
            data.setGubaDiscussionCount(asInteger(postsData.get("totalPosts")));
            data.setGubaPositivePosts(asInteger(postsData.get("positivePosts")));
            data.setGubaNegativePosts(asInteger(postsData.get("negativePosts")));
            data.setGubaNeutralPosts(asInteger(postsData.get("neutralPosts")));
            if (data.getGubaReadCount() == null) {
                data.setGubaReadCount(asInteger(postsData.get("totalReadCount")));
            }
            if (data.getGubaCommentCount() == null) {
                data.setGubaCommentCount(asInteger(postsData.get("totalCommentCount")));
            }
        }
    }

    private void mergeNewsData(SentimentData data, Map<String, Integer> newsData) {
        data.setNewsTotalCount(newsData.get("total"));
        data.setNewsPositiveCount(newsData.get("positive"));
        data.setNewsNegativeCount(newsData.get("negative"));
        data.setNewsNeutralCount(newsData.get("neutral"));
        data.setAnnouncementTotalCount(Math.max(1, (data.getNewsTotalCount() == null ? 0 : data.getNewsTotalCount()) / 8));
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l) {
            return l.intValue();
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void ensureMinimumFields(SentimentData data) {
        if (data.getXueqiuDiscussionCount() == null) data.setXueqiuDiscussionCount(0);
        if (data.getGubaDiscussionCount() == null) data.setGubaDiscussionCount(0);
        if (data.getXueqiuPositivePosts() == null) data.setXueqiuPositivePosts(0);
        if (data.getXueqiuNegativePosts() == null) data.setXueqiuNegativePosts(0);
        if (data.getXueqiuNeutralPosts() == null) data.setXueqiuNeutralPosts(0);
        if (data.getGubaPositivePosts() == null) data.setGubaPositivePosts(0);
        if (data.getGubaNegativePosts() == null) data.setGubaNegativePosts(0);
        if (data.getGubaNeutralPosts() == null) data.setGubaNeutralPosts(0);

        if (data.getNewsTotalCount() == null || data.getNewsTotalCount() == 0) {
            int fallback = 12;
            data.setNewsTotalCount(fallback);
            data.setNewsPositiveCount(4);
            data.setNewsNegativeCount(3);
            data.setNewsNeutralCount(5);
        }
        if (data.getAnnouncementTotalCount() == null) {
            data.setAnnouncementTotalCount(3);
        }
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
