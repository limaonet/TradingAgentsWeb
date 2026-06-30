package com.tradingagents.data.service;

import com.tradingagents.data.client.EastMoneyAnnouncementClient;
import com.tradingagents.data.client.EastMoneyNewsClient;
import com.tradingagents.data.client.SinaNewsClient;
import com.tradingagents.data.model.NewsItem;
import com.tradingagents.data.model.StockEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聚合新闻、公告与市场异动，供因果链多阶段分析使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCollectionService {

    private final EastMoneyNewsClient eastMoneyNewsClient;
    private final SinaNewsClient sinaNewsClient;
    private final EastMoneyAnnouncementClient announcementClient;

    /** 兼容旧接口：异步采集新闻 */
    public Mono<List<NewsItem>> collectNews(String symbol, int limitPerSource) {
        return Mono.zip(
                sinaNewsClient.getNewsItems(symbol, limitPerSource).defaultIfEmpty(List.of()),
                eastMoneyNewsClient.searchNews(symbol, limitPerSource).defaultIfEmpty(List.of())
        ).map(tuple -> deduplicateNews(tuple.getT1(), tuple.getT2()))
                .doOnNext(items -> log.info("【事件采集】标的={} 共采集 {} 条新闻", symbol, items.size()));
    }

    public List<StockEvent> collectEvents(String stockCode, String stockName) {
        List<StockEvent> events = new ArrayList<>();

        List<NewsItem> eastMoneyNews = eastMoneyNewsClient.searchNews(stockCode, 12).blockOptional().orElse(List.of());
        for (NewsItem item : eastMoneyNews) {
            events.add(StockEvent.fromNews(item));
        }
        List<NewsItem> sinaNews = sinaNewsClient.getNewsItems(stockCode, 8).blockOptional().orElse(List.of());
        for (NewsItem item : sinaNews) {
            events.add(StockEvent.fromNews(item));
        }
        List<StockEvent> announcements = announcementClient.getAnnouncements(stockCode, 8);
        events.addAll(announcements);

        events = dedupe(events);
        events.sort(Comparator.comparing(StockEvent::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        log.info("【事件采集】标的={} ({}) 结构化事件 {} 条", stockCode, stockName, events.size());
        return events.stream().limit(25).collect(Collectors.toList());
    }

    public String collectEventsAsText(String stockCode, String stockName) {
        List<StockEvent> events = collectEvents(stockCode, stockName);
        if (events.isEmpty()) {
            return "近期未采集到结构化事件，请结合基本面与行情数据推断因果，并降低 confidence。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("股票: ").append(stockName).append(" (").append(stockCode).append(")\n");
        sb.append("事件条数: ").append(events.size()).append("\n\n");
        for (int i = 0; i < events.size(); i++) {
            StockEvent e = events.get(i);
            sb.append(i + 1).append(". [").append(e.getType()).append("] ");
            if (e.getPublishedAt() != null) {
                sb.append(e.getPublishedAt()).append(" ");
            }
            sb.append(e.getTitle());
            if (e.getUrl() != null && !e.getUrl().isBlank()) {
                sb.append(" URL: ").append(e.getUrl());
            }
            if (e.getSummary() != null && !e.getSummary().isBlank()) {
                sb.append(" — ").append(e.getSummary());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<NewsItem> deduplicateNews(List<NewsItem> sina, List<NewsItem> eastMoney) {
        Map<String, NewsItem> merged = new LinkedHashMap<>();
        for (NewsItem item : sina) {
            merged.putIfAbsent(normalize(item.getTitle()), item);
        }
        for (NewsItem item : eastMoney) {
            merged.putIfAbsent(normalize(item.getTitle()), item);
        }
        return new ArrayList<>(merged.values());
    }

    private List<StockEvent> dedupe(List<StockEvent> events) {
        Map<String, StockEvent> map = new LinkedHashMap<>();
        for (StockEvent e : events) {
            map.putIfAbsent(normalize(e.getTitle()), e);
        }
        return new ArrayList<>(map.values());
    }

    private String normalize(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceAll("\\s+", "").toLowerCase();
    }
}
