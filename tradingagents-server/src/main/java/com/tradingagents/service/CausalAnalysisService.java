package com.tradingagents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.data.model.StockEvent;
import com.tradingagents.data.service.EventCollectionService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CausalAnalysisService {

    private final EventCollectionService eventCollectionService;
    private final CausalGraphParser causalGraphParser;
    private final CausalGraphValidator causalGraphValidator;
    private final ChatLanguageModel quickThinkingModel;
    private final ObjectMapper objectMapper;

    public record CausalPipelineResult(String report, CausalGraph graph) {}

    public CausalPipelineResult run(String symbol, String date,
                                    String marketReport, String sentimentReport,
                                    String fundamentalsReport) {
        CausalGraph graph = buildCausalGraph(symbol, date, marketReport, sentimentReport, fundamentalsReport);
        String report = graph.getSummary() != null ? graph.getSummary() : "因果链分析完成";
        return new CausalPipelineResult(report, graph);
    }

    public CausalGraph buildCausalGraph(String symbol, String date,
                                        String marketReport, String sentimentReport,
                                        String fundamentalsReport) {
        List<StockEvent> events = eventCollectionService.collectEvents(symbol, symbol);
        log.info("【因果分析】事件采集完成 标的={} 事件数={}", symbol, events.size());

        String eventJson = serializeEvents(events);
        String graphPrompt = buildGraphPrompt(symbol, date, eventJson, marketReport, sentimentReport, fundamentalsReport);

        GraphBuilder builder = AiServices.create(GraphBuilder.class, quickThinkingModel);
        String rawGraph = builder.buildGraph(graphPrompt);
        CausalGraph graph = causalGraphParser.parse(rawGraph);

        Critic critic = AiServices.create(Critic.class, quickThinkingModel);
        String criticPrompt = "标的=" + symbol + "\n因果图摘要=" + graph.getSummary()
                + "\n节点数=" + graph.getNodes().size()
                + "\n边数=" + (graph.getEdges() == null ? 0 : graph.getEdges().size())
                + "\n请检查是否遗漏关键公告/矛盾链，输出修订建议（简短）";
        String criticNotes = critic.review(criticPrompt);
        if (criticNotes != null && !criticNotes.isBlank()) {
            graph.setSummary(graph.getSummary() + " | 校验备注: " + criticNotes);
        }

        return causalGraphValidator.validateAndClean(graph, symbol);
    }

    private String serializeEvents(List<StockEvent> events) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(events);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildGraphPrompt(String symbol, String date, String eventsJson,
                                    String marketReport, String sentimentReport, String fundamentalsReport) {
        return """
                请为股票 %s 在 %s 构建多层因果链 JSON。
                
                【结构化事件列表（新闻/公告/行情异动）】
                %s
                
                【市场分析报告】
                %s
                
                【情绪分析报告】
                %s
                
                【基本面分析报告】
                %s
                
                输出 JSON（可包在 ```json 中），结构：
                {
                  "summary": "摘要",
                  "nodes": [
                    {"id":"evt_1","type":"event","label":"","description":"","eventTime":"","sourceRef":"URL","confidence":0.9},
                    {"id":"fac_1","type":"factor","label":"","description":"","confidence":0.8},
                    {"id":"ind_1","type":"indicator","label":"","description":"","confidence":0.75},
                    {"id":"out_1","type":"outcome","label":"","description":"","confidence":0.7}
                  ],
                  "edges": [
                    {"id":"e1","source":"evt_1","target":"fac_1","relation":"causes","strength":0.8,"evidence":"","confidence":0.85,"edgeType":"fact"},
                    {"id":"e2","source":"fac_2","target":"out_1","relation":"contradicts","strength":0.6,"evidence":"","confidence":0.6,"edgeType":"inference"}
                  ]
                }
                
                要求：
                1. 至少 8 个节点、6 条边，覆盖 macro/industry/company 至少两个层级
                2. 事件节点必须来自上方事件列表，sourceRef 填 URL
                3. 指标节点必须引用报告中的真实数字
                4. 至少 1 条 contradicts 或 dampens 边，表达多空对冲
                5. 禁止编造不在事件列表中的公司公告
                """.formatted(symbol, date, eventsJson,
                marketReport != null ? marketReport : "无",
                sentimentReport != null ? sentimentReport : "无",
                fundamentalsReport != null ? fundamentalsReport : "无");
    }

    interface GraphBuilder {
        @SystemMessage("你是因果图构建专家。只输出 JSON，不要其他文字。")
        @UserMessage("{{it}}")
        String buildGraph(String prompt);
    }

    interface Critic {
        @SystemMessage("你是因果图质检员，简要指出遗漏或逻辑漏洞，不超过80字。")
        @UserMessage("{{it}}")
        String review(String prompt);
    }
}
