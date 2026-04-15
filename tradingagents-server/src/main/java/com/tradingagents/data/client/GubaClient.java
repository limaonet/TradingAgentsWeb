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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东方财富股吧客户端
 * 获取股吧热度、帖子数、投资者情绪等数据
 */
@Slf4j
@Component
public class GubaClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${data.sentiment.guba.enabled:true}")
    private boolean enabled;

    @Value("${data.sentiment.guba.cookie:}")
    private String cookie;

    public GubaClient(@Value("${data.sentiment.guba.base-url:https://guba.eastmoney.com}") String baseUrl,
                      @Value("${data.sentiment.guba.cookie:}") String cookie) {
        this.cookie = cookie;

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .defaultHeader("Referer", "https://guba.eastmoney.com/");

        // 如果配置了 Cookie，添加到请求头
        if (cookie != null && !cookie.isEmpty()) {
            builder.defaultHeader("Cookie", cookie);
        }

        this.webClient = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取股吧热度排名
     */
    public Mono<Integer> getHotRank(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        // 东方财富股吧代码格式
        String gubaCode = convertToGubaCode(symbol);

        return webClient.get()
                .uri("/list,{code}.html", gubaCode)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseHotRankFromHtml)
                .doOnError(e -> log.error("【股吧】获取热度失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取股吧帖子统计
     */
    public Mono<Map<String, Object>> getPostStatistics(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        String gubaCode = convertToGubaCode(symbol);

        return webClient.get()
                .uri("/api/taobaolst?type=1&code={code}&page=1", gubaCode)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parsePostStatistics)
                .doOnError(e -> log.error("【股吧】获取帖子统计失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取股吧最新帖子列表
     */
    public Mono<Map<String, Object>> getLatestPosts(String symbol, int count) {
        if (!enabled) {
            return Mono.empty();
        }

        String gubaCode = convertToGubaCode(symbol);

        return webClient.get()
                .uri("/list,{code}_1.html", gubaCode)
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parsePostsFromHtml(html, count))
                .doOnError(e -> log.error("【股吧】获取帖子列表失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取综合股吧数据
     */
    public Mono<Map<String, Object>> getComprehensiveData(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        return Mono.zip(
                getHotRank(symbol).defaultIfEmpty(0),
                Mono.just(new HashMap<String, Object>()),
                getLatestPosts(symbol, 20).defaultIfEmpty(new HashMap<>())
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("hotRank", tuple.getT1());
            result.put("statistics", tuple.getT2());
            result.put("postsData", tuple.getT3());
            return result;
        });
    }

    /**
     * 转换为股吧代码格式
     * 东方财富股吧代码格式：个股为股票代码，如 000001
     */
    private String convertToGubaCode(String symbol) {
        // 去除可能的前缀
        if (symbol.contains(".")) {
            return symbol.substring(0, symbol.indexOf("."));
        }
        return symbol;
    }

    /**
     * 从 HTML 解析热度排名
     */
    private Integer parseHotRankFromHtml(String html) {
        try {
            Document doc = Jsoup.parse(html);
            
            // 尝试从页面中查找热度信息
            Elements hotElements = doc.select(".hot-rank, .heat-num, .stock-rank");
            for (Element elem : hotElements) {
                String text = elem.text();
                // 提取数字
                Pattern pattern = Pattern.compile("\\d+");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group());
                }
            }
            
            // 备选方案：从页面标题或其他元素推断
            Elements titleElements = doc.select("title");
            if (!titleElements.isEmpty()) {
                String title = titleElements.first().text();
                log.debug("Guba page title: {}", title);
            }
            
        } catch (Exception e) {
            log.error("【股吧】解析热度 HTML 失败：{}", e.getMessage());
        }
        return 0;
    }

    /**
     * 解析帖子统计数据
     */
    private Map<String, Object> parsePostStatistics(String response) {
        Map<String, Object> result = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            
            if (root.has("total")) {
                result.put("totalPosts", root.get("total").asInt());
            }
            if (root.has("read_count")) {
                result.put("readCount", root.get("read_count").asInt());
            }
            if (root.has("comment_count")) {
                result.put("commentCount", root.get("comment_count").asInt());
            }
            
        } catch (Exception e) {
            log.error("【股吧】解析帖子统计失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 从 HTML 解析帖子列表
     */
    private Map<String, Object> parsePostsFromHtml(String html, int count) {
        Map<String, Object> result = new HashMap<>();
        try {
            Document doc = Jsoup.parse(html);
            
            // 股吧帖子选择器
            Elements postElements = doc.select(".articleh");
            
            int positiveCount = 0;
            int negativeCount = 0;
            int neutralCount = 0;
            int totalReadCount = 0;
            int totalCommentCount = 0;
            
            int limit = Math.min(count, postElements.size());
            for (int i = 0; i < limit; i++) {
                Element post = postElements.get(i);
                
                // 阅读数
                Element readElem = post.selectFirst(".l1");
                if (readElem != null) {
                    String readText = readElem.text().replaceAll(",", "");
                    try {
                        totalReadCount += Integer.parseInt(readText);
                    } catch (NumberFormatException ignored) {}
                }
                
                // 评论数
                Element commentElem = post.selectFirst(".l2");
                if (commentElem != null) {
                    String commentText = commentElem.text().replaceAll(",", "");
                    try {
                        totalCommentCount += Integer.parseInt(commentText);
                    } catch (NumberFormatException ignored) {}
                }
                
                // 帖子标题（用于简单情感分析）
                Element titleElem = post.selectFirst(".l3 a");
                if (titleElem != null) {
                    String title = titleElem.text();
                    int sentiment = analyzeSentiment(title);
                    if (sentiment > 0) {
                        positiveCount++;
                    } else if (sentiment < 0) {
                        negativeCount++;
                    } else {
                        neutralCount++;
                    }
                }
            }
            
            result.put("totalPosts", limit);
            result.put("positivePosts", positiveCount);
            result.put("negativePosts", negativeCount);
            result.put("neutralPosts", neutralCount);
            result.put("totalReadCount", totalReadCount);
            result.put("totalCommentCount", totalCommentCount);
            
        } catch (Exception e) {
            log.error("【股吧】解析帖子 HTML 失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 简单的情感分析
     * 基于关键词匹配
     */
    private int analyzeSentiment(String text) {
        String lowerText = text.toLowerCase();
        
        // 正面关键词
        String[] positiveWords = {"涨", "升", "好", "牛", "利好", "买入", "推荐", "看好", "强势", "突破", "涨停"};
        // 负面关键词
        String[] negativeWords = {"跌", "降", "差", "熊", "利空", "卖出", "回避", "看空", "弱势", "跌破", "跌停", "跌"};
        
        int score = 0;
        for (String word : positiveWords) {
            if (lowerText.contains(word)) {
                score++;
            }
        }
        for (String word : negativeWords) {
            if (lowerText.contains(word)) {
                score--;
            }
        }
        
        return score;
    }
}
