package com.tradingagents.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分析请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {
    
    /**
     * 股票代码
     */
    private String ticker;
    
    /**
     * 分析日期
     */
    private String date;
    
    /**
     * 研究深度 (1-3)
     */
    private Integer researchDepth;
    
    /**
     * 选择的分析师
     */
    private List<String> selectedAnalysts;
    
    /**
     * LLM 提供商
     */
    private String llmProvider;
    
    /**
     * 深度思考模型
     */
    private String deepThinkModel;
    
    /**
     * 快速思考模型
     */
    private String quickThinkModel;
}
