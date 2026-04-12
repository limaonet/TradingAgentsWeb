package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 技术指标数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalIndicators {
    
    private String tsCode;          // 股票代码
    
    // MACD
    private BigDecimal macd;        // MACD值
    private BigDecimal macdSignal;  // 信号线
    private BigDecimal macdHist;    // 柱状图
    
    // RSI
    private BigDecimal rsi6;        // 6日RSI
    private BigDecimal rsi12;       // 12日RSI
    private BigDecimal rsi24;       // 24日RSI
    
    // KDJ
    private BigDecimal k;           // K值
    private BigDecimal d;           // D值
    private BigDecimal j;           // J值
    
    // 均线
    private BigDecimal ma5;         // 5日均线
    private BigDecimal ma10;        // 10日均线
    private BigDecimal ma20;        // 20日均线
    private BigDecimal ma60;        // 60日均线
}
