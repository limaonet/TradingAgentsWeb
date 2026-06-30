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

/**
 * 舆情数据服务
 * 使用公开 HTTP 接口抓取，失败时返回空数据而非随机 mock
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataService {

    private final XueqiuClient xueqiuClient;
    private final GubaClient gubaClient;
    private final SinaNewsClient sinaNewsClient;

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
                    calculateOverallSentiment(data);

                    log.info("【舆情】拉取完成 标的={} 新闻条数={} 情感标签={}",
                            symbol, data.getNewsTotalCount(), data.getSentimentLabel());
                    return data;
                })
                .onErrorResume(e -> {
                    log.warn("【舆情】上游抓取失败，标的={} 返回空数据。原因：{}", symbol, e.getMessage());
                    return Mono.just(createEmptySentiment(symbol, date));
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
        }
    }

    private void mergeNewsData(SentimentData data, Map<String, Integer> newsStats) {
        if (newsStats == null || newsStats.isEmpty()) {
            return;
        }
        data.setNewsTotalCount(newsStats.getOrDefault("total", 0));
        data.setNewsPositiveCount(newsStats.getOrDefault("positive", 0));
        data.setNewsNegativeCount(newsStats.getOrDefault("negative", 0));
        data.setNewsNeutralCount(newsStats.getOrDefault("neutral", 0));
    }

    private SentimentData createEmptySentiment(String symbol, LocalDate date) {
        SentimentData data = new SentimentData();
        data.setTsCode(symbol);
        data.setTradeDate(date);
        data.setOverallSentiment(BigDecimal.ZERO);
        data.setSentimentLabel("数据不可用");
        return data;
    }

    private void calculateOverallSentiment(SentimentData data) {
        int totalPositive = nullSafe(data.getXueqiuPositivePosts())
                + nullSafe(data.getGubaPositivePosts())
                + nullSafe(data.getNewsPositiveCount());

        int totalNegative = nullSafe(data.getXueqiuNegativePosts())
                + nullSafe(data.getGubaNegativePosts())
                + nullSafe(data.getNewsNegativeCount());

        int totalNeutral = nullSafe(data.getXueqiuNeutralPosts())
                + nullSafe(data.getGubaNeutralPosts())
                + nullSafe(data.getNewsNeutralCount());

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
            data.setSentimentLabel("数据不足");
        }
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
