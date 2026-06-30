package com.tradingagents.data.service;

import com.tradingagents.data.client.EastMoneyNewsClient;
import com.tradingagents.data.client.SinaNewsClient;
import com.tradingagents.data.model.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件采集服务：聚合公开新闻源，无需 token
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCollectionService {

    private final SinaNewsClient sinaNewsClient;
    private final EastMoneyNewsClient eastMoneyNewsClient;

    public Mono<List<NewsItem>> collectNews(String symbol, int limitPerSource) {
        return Mono.zip(
                sinaNewsClient.getNewsItems(symbol, limitPerSource).defaultIfEmpty(List.of()),
                eastMoneyNewsClient.searchNews(symbol, limitPerSource).defaultIfEmpty(List.of())
        ).map(tuple -> deduplicate(tuple.getT1(), tuple.getT2()))
                .doOnNext(items -> log.info("【事件采集】标的={} 共采集 {} 条新闻", symbol, items.size()));
    }

    private List<NewsItem> deduplicate(List<NewsItem> sina, List<NewsItem> eastMoney) {
        Map<String, NewsItem> merged = new LinkedHashMap<>();
        for (NewsItem item : sina) {
            merged.putIfAbsent(normalizeTitle(item.getTitle()), item);
        }
        for (NewsItem item : eastMoney) {
            merged.putIfAbsent(normalizeTitle(item.getTitle()), item);
        }
        return new ArrayList<>(merged.values());
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.replaceAll("\\s+", "").toLowerCase();
    }
}
