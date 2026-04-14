package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 雪球数据客户端
 * 获取股票热度、讨论数、投资者情绪等数据
 */
@Slf4j
@Component
public class XueqiuClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${data.sentiment.xueqiu.enabled:true}")
    private boolean enabled;

    @Value("${data.sentiment.xueqiu.cookie:}")
    private String cookie;

    public XueqiuClient(@Value("${data.sentiment.xueqiu.base-url:https://xueqiu.com}") String baseUrl,
                        @Value("${data.sentiment.xueqiu.cookie:}") String cookie) {
        this.cookie = cookie;

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/javascript, */*")
                .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .defaultHeader("Referer", "https://xueqiu.com/")
                .defaultHeader("X-Requested-With", "XMLHttpRequest");

        // 如果配置了 Cookie，添加到请求头
        if (cookie != null && !cookie.isEmpty()) {
            builder.defaultHeader("Cookie", cookie);
        }

        this.webClient = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取股票热度排名
     */
    public Mono<Integer> getHotRank(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        // 雪球代码格式：SH/深圳前缀
        String xueqiuSymbol = convertToXueqiuCode(symbol);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v5/stock/quotepage.json")
                        .queryParam("symbol", xueqiuSymbol)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseHotRank)
                .doOnError(e -> log.error("Failed to get xueqiu hot rank for {}: {}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取股票讨论数
     */
    public Mono<Integer> getDiscussionCount(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        String xueqiuSymbol = convertToXueqiuCode(symbol);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query/v1/symbol/search/status.json")
                        .queryParam("symbol", xueqiuSymbol)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseDiscussionCount)
                .doOnError(e -> log.error("Failed to get xueqiu discussion count for {}: {}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取股票相关帖子列表（用于情感分析）
     */
    public Mono<Map<String, Object>> getStockPosts(String symbol, int count) {
        if (!enabled) {
            return Mono.empty();
        }

        String xueqiuSymbol = convertToXueqiuCode(symbol);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query/v1/symbol/search/status.json")
                        .queryParam("symbol", xueqiuSymbol)
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parsePostsData)
                .doOnError(e -> log.error("Failed to get xueqiu posts for {}: {}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取综合热度数据
     */
    public Mono<Map<String, Object>> getComprehensiveData(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        return Mono.zip(
                getHotRank(symbol).defaultIfEmpty(0),
                getDiscussionCount(symbol).defaultIfEmpty(0),
                getStockPosts(symbol, 20).defaultIfEmpty(new HashMap<>())
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("hotRank", tuple.getT1());
            result.put("discussionCount", tuple.getT2());
            result.put("postsData", tuple.getT3());
            return result;
        });
    }

    /**
     * 转换为雪球代码格式
     */
    private String convertToXueqiuCode(String symbol) {
        // A股代码转换
        if (symbol.matches("\\d{6}")) {
            if (symbol.startsWith("6")) {
                return "SH" + symbol;  // 上海
            } else {
                return "SZ" + symbol;  // 深圳
            }
        }
        return symbol;
    }

    /**
     * 解析热度排名
     */
    private Integer parseHotRank(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data != null && data.has("hot_rank")) {
                return data.get("hot_rank").asInt();
            }
        } catch (Exception e) {
            log.error("Failed to parse hot rank: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 解析讨论数
     */
    private Integer parseDiscussionCount(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("count")) {
                return root.get("count").asInt();
            }
        } catch (Exception e) {
            log.error("Failed to parse discussion count: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 解析帖子数据
     */
    private Map<String, Object> parsePostsData(String response) {
        Map<String, Object> result = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            
            // 帖子列表
            if (root.has("list")) {
                JsonNode list = root.get("list");
                int positiveCount = 0;
                int negativeCount = 0;
                int neutralCount = 0;
                
                for (JsonNode post : list) {
                    // 简单情感判断：根据点赞数和评论数粗略估计
                    int likeCount = post.has("like_count") ? post.get("like_count").asInt() : 0;
                    int commentCount = post.has("reply_count") ? post.get("reply_count").asInt() : 0;
                    
                    // 热度高的帖子倾向于正面
                    if (likeCount > 10 || commentCount > 5) {
                        positiveCount++;
                    } else if (likeCount < 2 && commentCount < 2) {
                        negativeCount++;
                    } else {
                        neutralCount++;
                    }
                }
                
                result.put("totalPosts", list.size());
                result.put("positivePosts", positiveCount);
                result.put("negativePosts", negativeCount);
                result.put("neutralPosts", neutralCount);
            }
        } catch (Exception e) {
            log.error("Failed to parse posts data: {}", e.getMessage());
        }
        return result;
    }
}
