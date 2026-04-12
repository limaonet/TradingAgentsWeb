package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 舆情数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentData {
    
    private String tsCode;          // 股票代码
    private LocalDate tradeDate;    // 日期
    
    // 市场情绪统计
    private Integer marketUpCount;      // 上涨家数
    private Integer marketDownCount;    // 下跌家数
    private Integer marketLimitUpCount; // 涨停家数
    private Integer marketLimitDownCount;   // 跌停家数
    private BigDecimal marketUpDownRatio;   // 涨跌比
    private String marketSentiment;     // 整体情绪
    
    // 新闻舆情
    private Integer newsTotalCount;     // 新闻总数
    private Integer newsPositiveCount;  // 正面新闻
    private Integer newsNegativeCount;  // 负面新闻
    private Integer newsNeutralCount;   // 中性新闻
    
    // 公告舆情
    private Integer announcementTotalCount; // 公告总数
    
    // 社交媒体热度
    private Integer xueqiuHotRank;      // 雪球热股排名
    private Integer gubaHotRank;        // 东方财富股吧排名
    private Integer xueqiuDiscussionCount;  // 雪球讨论数
    private Integer gubaDiscussionCount;    // 股吧讨论数
    
    // 综合情感得分 (-1 到 1)
    private BigDecimal overallSentiment;
    private String sentimentLabel;      // 情感标签: 积极/中性/消极
}
