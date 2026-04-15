package com.tradingagents.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票搜索候选项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolSearchItem {
    private String code;
    private String name;
    private String marketType;
    private String quoteId;
}
