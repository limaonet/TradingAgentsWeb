package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

        String xueqiuSymbol = convertToXueqiuCode(symbol);

        return webClient.get()
                .uri("/S/{symbol}", xueqiuSymbol)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseHotRankFromHtml)
                .doOnError(e -> log.error("【雪球】获取热度失败 标的={} 原因：{}", symbol, e.getMessage()))
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
                .uri("/S/{symbol}", xueqiuSymbol)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseDiscussionCountFromHtml)
                .doOnError(e -> log.error("【雪球】获取讨论数失败 标的={} 原因：{}", symbol, e.getMessage()))
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
                .uri("/S/{symbol}", xueqiuSymbol)
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parsePostsFromHtml(html, count))
                .doOnError(e -> log.error("【雪球】获取帖子失败 标的={} 原因：{}", symbol, e.getMessage()))
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
    private Integer parseHotRankFromHtml(String response) {
        try {
            Document doc = Jsoup.parse(response);
            Elements candidates = doc.select("body *");
            for (Element element : candidates) {
                String text = element.text();
                if (text != null && text.contains("热度")) {
                    String digits = text.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        return Integer.parseInt(digits);
                    }
                }
            }
        } catch (Exception e) {
            log.error("【雪球】解析热度失败：{}", e.getMessage());
        }
        return 0;
    }

    /**
     * 解析讨论数
     */
    private Integer parseDiscussionCountFromHtml(String response) {
        try {
            Document doc = Jsoup.parse(response);
            Elements links = doc.select("a");
            int estimated = 0;
            for (Element link : links) {
                String text = link.text();
                if (text != null && (text.contains("讨论") || text.contains("评论"))) {
                    String digits = text.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        estimated = Math.max(estimated, Integer.parseInt(digits));
                    }
                }
            }
            return estimated;
        } catch (Exception e) {
            log.error("【雪球】解析讨论数失败：{}", e.getMessage());
        }
        return 0;
    }

    /**
     * 解析帖子数据
     */
    private Map<String, Object> parsePostsFromHtml(String response, int count) {
        Map<String, Object> result = new HashMap<>();
        try {
            Document doc = Jsoup.parse(response);
            Elements titles = doc.select("a");
            int positiveCount = 0;
            int negativeCount = 0;
            int neutralCount = 0;
            int total = 0;

            for (Element title : titles) {
                String text = title.text();
                if (text == null || text.length() < 8 || text.length() > 80) {
                    continue;
                }
                int sentiment = analyzeSentiment(text);
                if (sentiment > 0) {
                    positiveCount++;
                } else if (sentiment < 0) {
                    negativeCount++;
                } else {
                    neutralCount++;
                }
                total++;
                if (total >= count) {
                    break;
                }
            }
            result.put("totalPosts", total);
            result.put("positivePosts", positiveCount);
            result.put("negativePosts", negativeCount);
            result.put("neutralPosts", neutralCount);
        } catch (Exception e) {
            log.error("【雪球】解析帖子数据失败：{}", e.getMessage());
        }
        return result;
    }

    private int analyzeSentiment(String text) {
        String[] positiveWords = {"涨", "突破", "利好", "看好", "盈利", "增持", "回购"};
        String[] negativeWords = {"跌", "利空", "风险", "亏损", "减持", "暴跌", "看空"};

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
