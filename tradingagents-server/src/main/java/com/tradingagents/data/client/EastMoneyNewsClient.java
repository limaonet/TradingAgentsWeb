package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 东方财富新闻搜索公开 API（无需 token）
 */
@Slf4j
@Component
public class EastMoneyNewsClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EastMoneyNewsClient() {
        this.webClient = WebClient.builder()
                .baseUrl("https://search-api-web.eastmoney.com")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader("Referer", "https://so.eastmoney.com/")
                .build();
    }

    public Mono<List<NewsItem>> searchNews(String symbol, int limit) {
        String code = normalizeCode(symbol);
        String param = String.format(
                "{\"uid\":\"\",\"keyword\":\"%s\",\"type\":[\"cmsArticleWebOld\"],\"client\":\"web\",\"clientType\":\"web\",\"clientVersion\":\"curr\",\"pageIndex\":1,\"pageSize\":%d}",
                code, limit);

        return webClient.get()
                .uri(uri -> uri
                        .path("/search/jsonp")
                        .queryParam("cb", "cb")
                        .queryParam("param", param)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseNewsResponse)
                .doOnError(e -> log.warn("【东财新闻】搜索失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorReturn(List.of());
    }

    private List<NewsItem> parseNewsResponse(String body) {
        List<NewsItem> items = new ArrayList<>();
        try {
            int start = body.indexOf('(');
            int end = body.lastIndexOf(')');
            if (start < 0 || end <= start) {
                return items;
            }
            JsonNode root = objectMapper.readTree(body.substring(start + 1, end));
            JsonNode articles = root.path("result").path("cmsArticleWebOld");
            if (!articles.isArray()) {
                return items;
            }
            for (JsonNode article : articles) {
                String title = stripHtml(article.path("title").asText(""));
                if (title.length() < 6) {
                    continue;
                }
                items.add(NewsItem.builder()
                        .title(title)
                        .summary(stripHtml(article.path("content").asText("")))
                        .url(article.path("url").asText(""))
                        .source(article.path("mediaName").asText("东方财富"))
                        .publishedAt(article.path("date").asText(""))
                        .sentimentScore(analyzeSentiment(title))
                        .build());
            }
        } catch (Exception e) {
            log.warn("【东财新闻】解析失败：{}", e.getMessage());
        }
        return items;
    }

    private String stripHtml(String text) {
        return text.replaceAll("<[^>]+>", "").trim();
    }

    private int analyzeSentiment(String text) {
        String[] positive = {"利好", "增长", "新高", "增持", "回购", "突破", "盈利", "看好", "上调", "分红", "反弹"};
        String[] negative = {"利空", "下滑", "减持", "亏损", "违约", "调查", "下调", "暴跌", "风险", "跳水"};
        int score = 0;
        for (String w : positive) if (text.contains(w)) score++;
        for (String w : negative) if (text.contains(w)) score--;
        return score;
    }

    private String normalizeCode(String symbol) {
        String normalized = symbol.trim();
        if (normalized.matches("[01]\\.\\d{6}")) {
            return normalized.substring(2);
        }
        return normalized;
    }
}
