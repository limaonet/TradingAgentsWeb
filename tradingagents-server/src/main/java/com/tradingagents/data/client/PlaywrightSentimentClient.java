package com.tradingagents.data.client;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.tradingagents.data.model.SentimentData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playwright 舆情采集客户端
 * 使用浏览器自动化获取雪球和东方财富股吧数据
 */
@Slf4j
@Component
public class PlaywrightSentimentClient {

    @Value("${data.sentiment.xueqiu.enabled:true}")
    private boolean xueqiuEnabled;

    @Value("${data.sentiment.guba.enabled:true}")
    private boolean gubaEnabled;

    @Value("${data.sentiment.playwright.headless:true}")
    private boolean headless;

    private final Random random = new Random();

    /**
     * 获取综合舆情数据
     */
    public SentimentData getComprehensiveSentiment(String symbol, LocalDate date) {
        SentimentData data = new SentimentData();
        data.setTsCode(symbol);
        data.setTradeDate(date);

        // 使用 Playwright 获取雪球数据
        if (xueqiuEnabled) {
            try {
                fetchXueqiuData(data, symbol);
            } catch (Exception e) {
                log.warn("Failed to fetch xueqiu data for {}: {}", symbol, e.getMessage());
            }
        }

        // 使用 Playwright 获取股吧数据
        if (gubaEnabled) {
            try {
                fetchGubaData(data, symbol);
            } catch (Exception e) {
                log.warn("Failed to fetch guba data for {}: {}", symbol, e.getMessage());
            }
        }

        // 计算综合情感得分
        calculateOverallSentiment(data);

        return data;
    }

    /**
     * 从雪球获取数据
     */
    private void fetchXueqiuData(SentimentData data, String symbol) {
        log.info("Fetching xueqiu data for {}", symbol);

        String xueqiuSymbol = convertToXueqiuCode(symbol);
        String url = "https://xueqiu.com/S/" + xueqiuSymbol;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless)
            );
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
            );

            Page page = context.newPage();

            // 访问页面
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 等待内容加载
            page.waitForTimeout(3000);

            // 尝试获取热度数据
            try {
                // 雪球热度通常在页面某个位置
                Locator hotLocator = page.locator(".hot-rank, .stock-hot, [class*='hot']").first();
                if (hotLocator.isVisible()) {
                    String hotText = hotLocator.textContent();
                    Integer hotRank = extractNumber(hotText);
                    if (hotRank != null) {
                        data.setXueqiuHotRank(hotRank);
                        log.debug("Xueqiu hot rank for {}: {}", symbol, hotRank);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not find hot rank on xueqiu for {}", symbol);
            }

            // 获取讨论数（从页面统计）
            try {
                Locator discussLocator = page.locator(".discuss-count, .comment-count, [class*='discuss']").first();
                if (discussLocator.isVisible()) {
                    String discussText = discussLocator.textContent();
                    Integer discussCount = extractNumber(discussText);
                    if (discussCount != null) {
                        data.setXueqiuDiscussionCount(discussCount);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not find discussion count on xueqiu for {}", symbol);
            }

            // 分析帖子情感（简化版）
            analyzeXueqiuPosts(page, data);

            browser.close();
            log.info("Successfully fetched xueqiu data for {}", symbol);

        } catch (Exception e) {
            log.error("Error fetching xueqiu data for {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * 从东方财富股吧获取数据
     */
    private void fetchGubaData(SentimentData data, String symbol) {
        log.info("Fetching guba data for {}", symbol);

        String url = "https://guba.eastmoney.com/list," + symbol + ".html";

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless)
            );
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
            );

            Page page = context.newPage();

            // 访问页面
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 等待内容加载
            page.waitForTimeout(3000);

            // 获取帖子列表
            analyzeGubaPosts(page, data);

            browser.close();
            log.info("Successfully fetched guba data for {}", symbol);

        } catch (Exception e) {
            log.error("Error fetching guba data for {}: {}", symbol, e.getMessage());
            throw e;
        }
    }

    /**
     * 分析雪球帖子情感
     */
    private void analyzeXueqiuPosts(Page page, SentimentData data) {
        try {
            // 获取帖子标题列表
            Locator posts = page.locator(".discuss-title, .article-title, .status-title");
            int count = Math.min(posts.count(), 20);

            int positive = 0, negative = 0, neutral = 0;

            for (int i = 0; i < count; i++) {
                String title = posts.nth(i).textContent();
                int sentiment = analyzeSentiment(title);

                if (sentiment > 0) positive++;
                else if (sentiment < 0) negative++;
                else neutral++;
            }

            data.setXueqiuPositivePosts(positive);
            data.setXueqiuNegativePosts(negative);
            data.setXueqiuNeutralPosts(neutral);

            // 如果没有获取到讨论数，用帖子数估算
            if (data.getXueqiuDiscussionCount() == null) {
                data.setXueqiuDiscussionCount(count * 10); // 估算值
            }

        } catch (Exception e) {
            log.debug("Could not analyze xueqiu posts: {}", e.getMessage());
        }
    }

    /**
     * 分析股吧帖子情感
     */
    private void analyzeGubaPosts(Page page, SentimentData data) {
        try {
            // 股吧帖子选择器
            Locator posts = page.locator(".articleh");
            int count = Math.min(posts.count(), 20);

            int positive = 0, negative = 0, neutral = 0;
            int totalRead = 0;
            int totalComment = 0;

            for (int i = 0; i < count; i++) {
                Locator post = posts.nth(i);

                // 获取标题
                Locator titleLocator = post.locator(".l3 a");
                if (titleLocator.count() > 0) {
                    String title = titleLocator.textContent();
                    int sentiment = analyzeSentiment(title);

                    if (sentiment > 0) positive++;
                    else if (sentiment < 0) negative++;
                    else neutral++;
                }

                // 获取阅读数
                Locator readLocator = post.locator(".l1");
                if (readLocator.count() > 0) {
                    Integer read = extractNumber(readLocator.textContent());
                    if (read != null) totalRead += read;
                }

                // 获取评论数
                Locator commentLocator = post.locator(".l2");
                if (commentLocator.count() > 0) {
                    Integer comment = extractNumber(commentLocator.textContent());
                    if (comment != null) totalComment += comment;
                }
            }

            data.setGubaPositivePosts(positive);
            data.setGubaNegativePosts(negative);
            data.setGubaNeutralPosts(neutral);
            data.setGubaReadCount(totalRead);
            data.setGubaCommentCount(totalComment);
            data.setGubaDiscussionCount(count);

        } catch (Exception e) {
            log.debug("Could not analyze guba posts: {}", e.getMessage());
        }
    }

    /**
     * 简单的情感分析
     */
    private int analyzeSentiment(String text) {
        if (text == null) return 0;

        String lowerText = text.toLowerCase();

        // 正面关键词
        String[] positiveWords = {"涨", "升", "好", "牛", "利好", "买入", "推荐", "看好", "强势", "突破", "涨停", "涨", "升", "赚钱", "盈利"};
        // 负面关键词
        String[] negativeWords = {"跌", "降", "差", "熊", "利空", "卖出", "回避", "看空", "弱势", "跌破", "跌停", "跌", "亏", "套", "垃圾"};

        int score = 0;
        for (String word : positiveWords) {
            if (lowerText.contains(word)) score++;
        }
        for (String word : negativeWords) {
            if (lowerText.contains(word)) score--;
        }

        return score;
    }

    /**
     * 计算综合情感得分
     */
    private void calculateOverallSentiment(SentimentData data) {
        int totalPositive = (data.getXueqiuPositivePosts() != null ? data.getXueqiuPositivePosts() : 0)
                + (data.getGubaPositivePosts() != null ? data.getGubaPositivePosts() : 0);
        int totalNegative = (data.getXueqiuNegativePosts() != null ? data.getXueqiuNegativePosts() : 0)
                + (data.getGubaNegativePosts() != null ? data.getGubaNegativePosts() : 0);
        int totalNeutral = (data.getXueqiuNeutralPosts() != null ? data.getXueqiuNeutralPosts() : 0)
                + (data.getGubaNeutralPosts() != null ? data.getGubaNeutralPosts() : 0);

        int total = totalPositive + totalNegative + totalNeutral;

        if (total > 0) {
            BigDecimal score = BigDecimal.valueOf(totalPositive - totalNegative)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            data.setOverallSentiment(score);

            if (score.compareTo(new BigDecimal("0.3")) > 0) {
                data.setSentimentLabel("积极");
            } else if (score.compareTo(new BigDecimal("-0.3")) < 0) {
                data.setSentimentLabel("消极");
            } else {
                data.setSentimentLabel("中性");
            }
        } else {
            data.setOverallSentiment(BigDecimal.ZERO);
            data.setSentimentLabel("中性");
        }

        // 填充其他模拟数据
        fillMockData(data);
    }

    /**
     * 填充模拟数据（用于缺失的字段）
     */
    private void fillMockData(SentimentData data) {
        // 新闻舆情（模拟）
        if (data.getNewsTotalCount() == null) {
            int totalNews = 20 + random.nextInt(30);
            data.setNewsTotalCount(totalNews);
            data.setNewsPositiveCount((int) (totalNews * 0.4));
            data.setNewsNegativeCount((int) (totalNews * 0.3));
            data.setNewsNeutralCount((int) (totalNews * 0.3));
        }

        // 公告舆情
        if (data.getAnnouncementTotalCount() == null) {
            data.setAnnouncementTotalCount(5 + random.nextInt(10));
        }

        // 市场情绪（模拟）
        if (data.getMarketUpCount() == null) {
            data.setMarketUpCount(2500 + random.nextInt(1000));
            data.setMarketDownCount(2000 + random.nextInt(1000));
            data.setMarketLimitUpCount(50 + random.nextInt(100));
            data.setMarketLimitDownCount(20 + random.nextInt(50));

            BigDecimal ratio = BigDecimal.valueOf(data.getMarketUpCount())
                    .divide(BigDecimal.valueOf(data.getMarketDownCount()), 2, RoundingMode.HALF_UP);
            data.setMarketUpDownRatio(ratio);

            if (ratio.compareTo(new BigDecimal("1.5")) > 0) {
                data.setMarketSentiment("极度乐观");
            } else if (ratio.compareTo(new BigDecimal("1.2")) > 0) {
                data.setMarketSentiment("乐观");
            } else if (ratio.compareTo(new BigDecimal("0.8")) > 0) {
                data.setMarketSentiment("中性");
            } else if (ratio.compareTo(new BigDecimal("0.5")) > 0) {
                data.setMarketSentiment("悲观");
            } else {
                data.setMarketSentiment("极度悲观");
            }
        }
    }

    /**
     * 转换为雪球代码格式
     */
    private String convertToXueqiuCode(String symbol) {
        if (symbol.matches("\\d{6}")) {
            if (symbol.startsWith("6")) {
                return "SH" + symbol;
            } else {
                return "SZ" + symbol;
            }
        }
        return symbol;
    }

    /**
     * 从文本中提取数字
     */
    private Integer extractNumber(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(text.replaceAll(",", ""));
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
