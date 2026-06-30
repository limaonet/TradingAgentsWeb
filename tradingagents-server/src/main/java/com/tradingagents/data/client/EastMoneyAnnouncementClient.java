package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.StockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class EastMoneyAnnouncementClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Mono<List<StockEvent>> fetchAnnouncements(String symbol, int limit) {
        return Mono.fromCallable(() -> loadAnnouncements(symbol, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("【东财公告】拉取失败 标的={} 原因：{}", symbol, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    public List<StockEvent> getAnnouncements(String symbol, int limit) {
        try {
            return loadAnnouncements(symbol, limit);
        } catch (Exception e) {
            log.warn("【东财公告】拉取失败 标的={} 原因：{}", symbol, e.getMessage());
            return List.of();
        }
    }

    private List<StockEvent> loadAnnouncements(String symbol, int limit) throws Exception {
        String code = normalizeCode(symbol);
        String url = "https://np-anotice-stock.eastmoney.com/api/security/ann?page_size=" + limit
                + "&page_index=1&ann_type=A&client_source=web&stock_list=" + code;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://data.eastmoney.com/")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        List<StockEvent> events = new ArrayList<>();
        JsonNode list = objectMapper.readTree(response.body()).path("data").path("list");
        if (!list.isArray()) {
            return events;
        }
        for (JsonNode item : list) {
            String title = item.path("title_ch").asText(item.path("title").asText(""));
            if (title.isBlank()) continue;
            String artCode = item.path("art_code").asText("");
            events.add(StockEvent.builder()
                    .eventType("announcement")
                    .title(title)
                    .publishedAt(item.path("notice_date").asText(""))
                    .url(artCode.isBlank() ? "" : "https://data.eastmoney.com/notices/detail/" + code + "/" + artCode + ".html")
                    .source("东方财富公告")
                    .build());
        }
        return events;
    }

    private String normalizeCode(String symbol) {
        String n = symbol.trim();
        if (n.matches("[01]\\.\\d{6}")) return n.substring(2);
        return n;
    }
}
