package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 股票行情数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockData {
    
    private String tsCode;          // 股票代码
    private LocalDate tradeDate;    // 交易日期
    private BigDecimal open;        // 开盘价
    private BigDecimal high;        // 最高价
    private BigDecimal low;         // 最低价
    private BigDecimal close;       // 收盘价
    private BigDecimal preClose;    // 昨收价
    private BigDecimal change;      // 涨跌额
    private BigDecimal pctChange;   // 涨跌幅
    private BigDecimal vol;         // 成交量（手）
    private BigDecimal amount;      // 成交额（千元）
}
