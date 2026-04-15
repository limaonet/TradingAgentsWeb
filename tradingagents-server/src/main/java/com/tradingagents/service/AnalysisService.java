package com.tradingagents.service;

import com.tradingagents.agents.*;
import com.tradingagents.data.client.StockSymbolResolverClient;
import com.tradingagents.model.AnalysisRequest;
import com.tradingagents.model.AnalysisState;
import com.tradingagents.model.SymbolSearchItem;
import com.tradingagents.websocket.AnalysisProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分析服务
 * 负责编排整个分析流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final MarketAnalystAgent marketAnalystAgent;
    private final SentimentAnalystAgent sentimentAnalystAgent;
    private final FundamentalsAnalystAgent fundamentalsAnalystAgent;
    private final ResearchManagerAgent researchManagerAgent;
    private final TraderAgent traderAgent;
    private final RiskManagementAgents riskManagementAgents;
    private final PortfolioManagerAgent portfolioManagerAgent;
    private final AnalysisProgressHandler progressHandler;
    private final StockSymbolResolverClient stockSymbolResolverClient;

    // 分析状态缓存
    private final Map<String, AnalysisState> analysisCache = new ConcurrentHashMap<>();

    /**
     * 启动分析
     */
    public String startAnalysis(AnalysisRequest request) {
        String analysisId = UUID.randomUUID().toString();
        String resolvedSymbol = stockSymbolResolverClient.resolveToSymbol(request.getTicker()).block();
        request.setTicker(resolvedSymbol);
        
        // 初始化分析状态
        AnalysisState state = AnalysisState.builder()
                .analysisId(analysisId)
                .ticker(resolvedSymbol)
                .date(request.getDate())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status("running")
                .progress(0)
                .startTime(LocalDateTime.now())
                .build();
        
        analysisCache.put(analysisId, state);
        log.info("【分析】已创建任务 analysisId={}，解析后标的={}", analysisId, resolvedSymbol);
        
        // 异步执行分析流程
        executeAnalysisFlow(analysisId, request)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            state.setStatus("completed");
                            state.setEndTime(LocalDateTime.now());
                            state.setProgress(100);
                            log.info("【分析】任务完成 analysisId={}", analysisId);
                        },
                        error -> {
                            state.setStatus("error");
                            state.setEndTime(LocalDateTime.now());
                            state.setErrorMessage(error.getMessage());
                            log.error("【分析】任务失败 analysisId={}，原因：{}", analysisId, error.getMessage());
                            progressHandler.sendError(analysisId, "system", error.getMessage());
                        }
                );
        
        return analysisId;
    }

    /**
     * 执行分析流程
     */
    private Mono<Void> executeAnalysisFlow(String analysisId, AnalysisRequest request) {
        String symbol = request.getTicker();
        String date = request.getDate();
        
        return Mono.fromRunnable(() -> {
                    log.info("【分析】进入编排：并行执行市场/舆情/基本面分析 analysisId={} 标的={}", analysisId, symbol);
                    progressHandler.sendProgress(analysisId, "system", "started", "开始分析流程");
                })
                // Phase 1: 并行执行分析师分析
                .then(Mono.zipDelayError(
                        executeMarketAnalysis(analysisId, symbol, date),
                        executeSentimentAnalysis(analysisId, symbol, date),
                        executeFundamentalsAnalysis(analysisId, symbol, date)
                ))
                .flatMap(tuple -> {
                    String marketReport = tuple.getT1();
                    String sentimentReport = tuple.getT2();
                    String fundamentalsReport = tuple.getT3();
                    
                    // Phase 2: 研究经理生成投资计划
                    return executeResearchManager(analysisId, symbol, date, 
                            marketReport, sentimentReport, fundamentalsReport)
                            .flatMap(investmentPlan -> {
                                // Phase 3: 交易员生成交易计划
                                return executeTrader(analysisId, symbol, date, investmentPlan)
                                        .flatMap(tradePlan -> {
                                            // Phase 4: 风控辩论
                                            return executeRiskDebate(analysisId, symbol, date,
                                                    marketReport, sentimentReport, fundamentalsReport, tradePlan)
                                                    .flatMap(riskViews -> {
                                                        updateState(analysisId, state -> {
                                                            state.setAggressiveAnalysis(riskViews.aggressive());
                                                            state.setConservativeAnalysis(riskViews.conservative());
                                                            state.setNeutralAnalysis(riskViews.neutral());
                                                        });
                                                        // Phase 5: 组合经理生成最终决策
                                                        return executePortfolioManager(analysisId, symbol, date,
                                                                marketReport, sentimentReport, fundamentalsReport,
                                                                investmentPlan, tradePlan, riskViews);
                                                    });
                                        });
                            });
                })
                .then();
    }

    /**
     * 执行市场分析
     */
    private Mono<String> executeMarketAnalysis(String analysisId, String symbol, String date) {
        return Mono.fromCallable(() -> marketAnalystAgent.analyze(analysisId, symbol, date))
                .doOnSuccess(report -> updateState(analysisId, state -> state.setMarketReport(report)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行情绪分析
     */
    private Mono<String> executeSentimentAnalysis(String analysisId, String symbol, String date) {
        return Mono.fromCallable(() -> sentimentAnalystAgent.analyze(analysisId, symbol, date))
                .doOnSuccess(report -> updateState(analysisId, state -> state.setSentimentReport(report)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行基本面分析
     */
    private Mono<String> executeFundamentalsAnalysis(String analysisId, String symbol, String date) {
        return Mono.fromCallable(() -> fundamentalsAnalystAgent.analyze(analysisId, symbol, date))
                .doOnSuccess(report -> updateState(analysisId, state -> state.setFundamentalsReport(report)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行研究经理
     */
    private Mono<String> executeResearchManager(String analysisId, String symbol, String date,
                                                 String marketReport, String sentimentReport,
                                                 String fundamentalsReport) {
        return Mono.fromCallable(() -> 
                        researchManagerAgent.generateInvestmentPlan(analysisId, symbol, date,
                                marketReport, sentimentReport, null, fundamentalsReport))
                .doOnSuccess(plan -> updateState(analysisId, state -> state.setResearchManagerDecision(plan)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行交易员
     */
    private Mono<String> executeTrader(String analysisId, String symbol, String date, String investmentPlan) {
        return Mono.fromCallable(() -> traderAgent.generateTradePlan(analysisId, symbol, date, investmentPlan))
                .doOnSuccess(plan -> updateState(analysisId, state -> state.setTraderInvestmentPlan(plan)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行风控辩论
     */
    private Mono<RiskViews> executeRiskDebate(String analysisId, String symbol, String date,
                                               String marketReport, String sentimentReport,
                                               String fundamentalsReport, String tradePlan) {
        return Mono.zip(
                Mono.fromCallable(() -> riskManagementAgents.aggressiveAnalysis(analysisId, symbol, date,
                        marketReport, sentimentReport, fundamentalsReport, tradePlan))
                        .subscribeOn(Schedulers.boundedElastic()),
                Mono.fromCallable(() -> riskManagementAgents.conservativeAnalysis(analysisId, symbol, date,
                        marketReport, sentimentReport, fundamentalsReport, tradePlan))
                        .subscribeOn(Schedulers.boundedElastic())
        ).flatMap(tuple -> {
            String aggressiveView = tuple.getT1();
            String conservativeView = tuple.getT2();
            
            return Mono.fromCallable(() -> riskManagementAgents.neutralAnalysis(analysisId, symbol, date,
                            marketReport, sentimentReport, fundamentalsReport, tradePlan,
                            aggressiveView, conservativeView))
                    .map(neutralView -> new RiskViews(aggressiveView, conservativeView, neutralView))
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }

    /**
     * 执行组合经理
     */
    private Mono<String> executePortfolioManager(String analysisId, String symbol, String date,
                                                  String marketReport, String sentimentReport,
                                                  String fundamentalsReport, String investmentPlan,
                                                  String tradePlan, RiskViews riskViews) {
        return Mono.fromCallable(() -> 
                        portfolioManagerAgent.generateFinalDecision(analysisId, symbol, date,
                                marketReport, sentimentReport, fundamentalsReport,
                                investmentPlan, tradePlan,
                                riskViews.aggressive, riskViews.conservative, riskViews.neutral))
                .doOnSuccess(decision -> updateState(analysisId, state -> state.setFinalTradeDecision(decision)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 更新分析状态
     */
    private void updateState(String analysisId, java.util.function.Consumer<AnalysisState> updater) {
        AnalysisState state = analysisCache.get(analysisId);
        if (state != null) {
            updater.accept(state);
        }
    }

    /**
     * 获取分析状态
     */
    public AnalysisState getAnalysisState(String analysisId) {
        return analysisCache.get(analysisId);
    }

    public java.util.List<SymbolSearchItem> searchSymbols(String keyword, int limit) {
        return stockSymbolResolverClient.searchCandidates(keyword, limit).block();
    }

    /**
     * 风控观点
     */
    private record RiskViews(String aggressive, String conservative, String neutral) {}
}
