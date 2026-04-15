package com.tradingagents.data.client;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 新浪财经新闻客户端
 * 抓取个股新闻并做简单情感统计
 */
@Slf4j
@Component
public class SinaNewsClient {

    private final WebClient webClient;

    @Value("${data.sentiment.news.enabled:true}")
    private boolean enabled;

    public SinaNewsClient(@Value("${data.sentiment.news.base-url:https://finance.sina.com.cn}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .build();
    }

    public Mono<Map<String, Integer>> getNewsSentimentStats(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        String sinaCode = convertToSinaCode(symbol);
        String path = "/realstock/company/" + sinaCode + "/nc.shtml";

        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseNewsStats)
                .doOnError(e -> log.error("【新浪新闻】请求页面失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private String convertToSinaCode(String symbol) {
        if (symbol != null && symbol.matches("\\d{6}")) {
            return symbol.startsWith("6") ? "sh" + symbol : "sz" + symbol;
        }
        return symbol == null ? "" : symbol.toLowerCase();
    }

    private Map<String, Integer> parseNewsStats(String html) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("positive", 0);
        stats.put("negative", 0);
        stats.put("neutral", 0);

        try {
            Document doc = Jsoup.parse(html);
            Elements links = doc.select("a");

            int total = 0;
            int positive = 0;
            int negative = 0;
            int neutral = 0;

            for (Element link : links) {
                String title = link.text();
                if (title == null || title.length() < 8 || title.length() > 80) {
                    continue;
                }
                total++;
                int sentiment = analyzeSentiment(title);
                if (sentiment > 0) {
                    positive++;
                } else if (sentiment < 0) {
                    negative++;
                } else {
                    neutral++;
                }
                if (total >= 60) {
                    break;
                }
            }

            stats.put("total", total);
            stats.put("positive", positive);
            stats.put("negative", negative);
            stats.put("neutral", neutral);
        } catch (Exception e) {
            log.error("【新浪新闻】解析 HTML 失败：{}", e.getMessage());
        }

        return stats;
    }

    private int analyzeSentiment(String text) {
        String[] positiveWords = {"利好", "增长", "新高", "增持", "回购", "突破", "盈利", "看好", "上调"};
        String[] negativeWords = {"利空", "下滑", "减持", "亏损", "违约", "调查", "下调", "暴跌", "风险"};

        int score = 0;
        for (String word : positiveWords) {
            if (text.contains(word)) {
                score++;
            }
        }
        for (String word : negativeWords) {
            if (text.contains(word)) {
                score--;
            }
        }
        return score;
    }
}
