package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {
    private String title;
    private String summary;
    private String url;
    private String source;
    private String publishedAt;
    private Integer sentimentScore;
}
