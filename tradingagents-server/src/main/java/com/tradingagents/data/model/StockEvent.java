package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockEvent {
    /** news | announcement | market */
    private String eventType;
    private String title;
    private String summary;
    private String url;
    private String source;
    private String publishedAt;
    private Integer sentimentScore;

    public String getType() {
        return eventType;
    }

    public static StockEvent fromNews(NewsItem item) {
        return StockEvent.builder()
                .eventType("news")
                .title(item.getTitle())
                .summary(item.getSummary())
                .url(item.getUrl())
                .source(item.getSource())
                .publishedAt(item.getPublishedAt())
                .sentimentScore(item.getSentimentScore())
                .build();
    }
}
