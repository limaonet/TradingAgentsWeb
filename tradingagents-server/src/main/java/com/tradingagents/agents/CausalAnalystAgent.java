package com.tradingagents.agents;

import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.service.CausalAnalysisService;
import com.tradingagents.websocket.AnalysisProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 因果分析师 Agent
 * 基于真实新闻/公告事件与三份分析师报告，多阶段构建因果链图谱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CausalAnalystAgent {

    private final CausalAnalysisService causalAnalysisService;
    private final AnalysisProgressHandler progressHandler;

    public CausalAnalysisResult analyze(String analysisId, String symbol, String date,
                                        String marketReport, String sentimentReport,
                                        String fundamentalsReport) {
        log.info("【因果分析师】开始分析 标的={} 日期={}", symbol, date);
        progressHandler.sendAgentStatus(analysisId, "causal_analyst", "running", "正在采集新闻与公告事件...");

        try {
            progressHandler.sendAgentStatus(analysisId, "causal_analyst", "running", "正在多阶段构建因果链...");

            CausalAnalysisService.CausalPipelineResult result = causalAnalysisService.run(
                    symbol, date, marketReport, sentimentReport, fundamentalsReport);
            CausalGraph graph = result.graph();

            progressHandler.sendAgentStatus(analysisId, "causal_analyst", "completed", "因果链分析完成");
            progressHandler.sendReport(analysisId, "causal_report", graph.getSummary());
            progressHandler.sendCausalGraph(analysisId, graph);

            log.info("【因果分析师】完成 标的={} 节点数={} 边数={}",
                    symbol, graph.getNodes().size(), graph.getEdges() == null ? 0 : graph.getEdges().size());
            return new CausalAnalysisResult(result.report(), graph);

        } catch (Exception e) {
            log.error("【因果分析师】失败 标的={} 原因：{}", symbol, e.getMessage());
            progressHandler.sendError(analysisId, "causal_analyst", e.getMessage());
            throw new RuntimeException("因果分析失败", e);
        }
    }

    public record CausalAnalysisResult(String summary, CausalGraph graph) {}
}
