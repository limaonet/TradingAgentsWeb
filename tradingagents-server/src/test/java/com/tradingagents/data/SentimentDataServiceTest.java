package com.tradingagents.data;

import com.tradingagents.data.client.PlaywrightSentimentClient;
import com.tradingagents.data.model.SentimentData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Playwright 舆情采集客户端测试
 */
public class SentimentDataServiceTest {

    @Test
    public void testPlaywrightSentimentClient() {
        System.out.println("=== 测试 Playwright 舆情采集客户端 ===");
        
        PlaywrightSentimentClient client = new PlaywrightSentimentClient();
        String symbol = "000001";  // 平安银行
        LocalDate date = LocalDate.now();
        
        try {
            System.out.println("正在获取舆情数据，请稍候...");
            SentimentData data = client.getComprehensiveSentiment(symbol, date);
            
            assertNotNull(data);
            System.out.println("✅ 舆情数据获取成功!");
            System.out.println();
            
            // 基本信息
            System.out.println("--- 基本信息 ---");
            System.out.println("股票代码: " + data.getTsCode());
            System.out.println("日期: " + data.getTradeDate());
            System.out.println();
            
            // 雪球数据
            System.out.println("--- 雪球数据 ---");
            System.out.println("热股排名: " + data.getXueqiuHotRank());
            System.out.println("讨论数: " + data.getXueqiuDiscussionCount());
            System.out.println("正面帖子: " + data.getXueqiuPositivePosts());
            System.out.println("负面帖子: " + data.getXueqiuNegativePosts());
            System.out.println("中性帖子: " + data.getXueqiuNeutralPosts());
            System.out.println();
            
            // 股吧数据
            System.out.println("--- 东方财富股吧数据 ---");
            System.out.println("讨论数: " + data.getGubaDiscussionCount());
            System.out.println("正面帖子: " + data.getGubaPositivePosts());
            System.out.println("负面帖子: " + data.getGubaNegativePosts());
            System.out.println("中性帖子: " + data.getGubaNeutralPosts());
            System.out.println("阅读数: " + data.getGubaReadCount());
            System.out.println("评论数: " + data.getGubaCommentCount());
            System.out.println();
            
            // 市场情绪
            System.out.println("--- 市场情绪 ---");
            System.out.println("上涨家数: " + data.getMarketUpCount());
            System.out.println("下跌家数: " + data.getMarketDownCount());
            System.out.println("涨跌比: " + data.getMarketUpDownRatio());
            System.out.println("市场情绪: " + data.getMarketSentiment());
            System.out.println();
            
            // 新闻舆情
            System.out.println("--- 新闻舆情 ---");
            System.out.println("新闻总数: " + data.getNewsTotalCount());
            System.out.println("正面新闻: " + data.getNewsPositiveCount());
            System.out.println("负面新闻: " + data.getNewsNegativeCount());
            System.out.println("中性新闻: " + data.getNewsNeutralCount());
            System.out.println();
            
            // 综合评分
            System.out.println("--- 综合评分 ---");
            System.out.println("情感得分: " + data.getOverallSentiment());
            System.out.println("情感标签: " + data.getSentimentLabel());
            System.out.println();
            
        } catch (Exception e) {
            System.out.println("❌ 舆情数据获取失败: " + e.getMessage());
            e.printStackTrace();
            fail("测试失败: " + e.getMessage());
        }
        
        System.out.println("=== 测试完成 ===");
    }
}
