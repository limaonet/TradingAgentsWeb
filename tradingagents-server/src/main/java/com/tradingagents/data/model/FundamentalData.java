package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 基本面财务数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalData {
    
    private String tsCode;          // 股票代码
    private String endDate;         // 报告期
    
    // 盈利能力
    private BigDecimal roe;         // 净资产收益率
    private BigDecimal roa;         // 总资产报酬率
    private BigDecimal grossMargin; // 毛利率
    private BigDecimal netMargin;   // 净利率
    
    // 偿债能力
    private BigDecimal currentRatio;    // 流动比率
    private BigDecimal quickRatio;      // 速动比率
    private BigDecimal debtToAsset;     // 资产负债率
    
    // 成长能力
    private BigDecimal revenueGrowth;   // 营收增长率
    private BigDecimal profitGrowth;    // 净利润增长率
    
    // 估值指标
    private BigDecimal peRatio;     // 市盈率
    private BigDecimal pbRatio;     // 市净率
    private BigDecimal psRatio;     // 市销率
    
    // 现金流
    private BigDecimal operatingCashFlow;   // 经营现金流
    private BigDecimal investingCashFlow;   // 投资现金流
    private BigDecimal financingCashFlow;   // 筹资现金流
}
