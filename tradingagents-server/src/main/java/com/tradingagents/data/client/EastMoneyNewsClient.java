package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 东方财富新闻搜索公开 API（无需 token）
 */
@Slf4j
@Component
public class EastMoneyNewsClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Mono<List<NewsItem>> searchNews(String symbol, int limit) {
        return Mono.fromCallable(() -> fetchNews(symbol, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("【东财新闻】搜索失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorReturn(List.of());
    }

    private List<NewsItem> fetchNews(String symbol, int limit) throws Exception {
        String code = normalizeCode(symbol);
        String param = String.format(
                "{\"uid\":\"\",\"keyword\":\"%s\",\"type\":[\"cmsArticleWebOld\"],\"client\":\"web\",\"clientType\":\"web\",\"clientVersion\":\"curr\",\"pageIndex\":1,\"pageSize\":%d}",
                code, limit);
        String url = "https://search-api-web.eastmoney.com/search/jsonp?cb=cb&param="
                + URLEncoder.encode(param, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://so.eastmoney.com/")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return parseNewsResponse(response.body());
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
