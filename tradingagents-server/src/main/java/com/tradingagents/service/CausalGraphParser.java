package com.tradingagents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.CausalEdge;
import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.data.model.CausalNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CausalGraphParser {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CausalGraph parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return CausalGraph.builder().summary("因果分析结果为空").build();
        }

        try {
            String json = extractJson(llmOutput);
            JsonNode root = objectMapper.readTree(json);
            CausalGraph graph = CausalGraph.builder()
                    .summary(root.path("summary").asText(""))
                    .nodes(parseNodes(root.path("nodes")))
                    .edges(parseEdges(root.path("edges")))
                    .build();
            if (!graph.getNodes().isEmpty()) {
                return graph;
            }
        } catch (Exception e) {
            log.warn("【因果图】JSON 解析失败，将返回摘要：{}", e.getMessage());
        }

        return CausalGraph.builder()
                .summary(llmOutput.length() > 500 ? llmOutput.substring(0, 500) + "..." : llmOutput)
                .build();
    }

    private String extractJson(String text) {
        Matcher matcher = JSON_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private java.util.List<CausalNode> parseNodes(JsonNode nodes) {
        java.util.List<CausalNode> result = new ArrayList<>();
        if (!nodes.isArray()) {
            return result;
        }
        Iterator<JsonNode> it = nodes.elements();
        while (it.hasNext()) {
            JsonNode n = it.next();
            result.add(CausalNode.builder()
                    .id(n.path("id").asText())
                    .type(n.path("type").asText("factor"))
                    .label(n.path("label").asText(""))
                    .description(n.path("description").asText(""))
                    .eventTime(n.path("eventTime").asText(null))
                    .sourceRef(n.path("sourceRef").asText(null))
                    .confidence(n.path("confidence").isNumber() ? n.path("confidence").asDouble() : null)
                    .build());
        }
        return result;
    }

    private java.util.List<CausalEdge> parseEdges(JsonNode edges) {
        java.util.List<CausalEdge> result = new ArrayList<>();
        if (!edges.isArray()) {
            return result;
        }
        Iterator<JsonNode> it = edges.elements();
        while (it.hasNext()) {
            JsonNode e = it.next();
            result.add(CausalEdge.builder()
                    .id(e.path("id").asText())
                    .source(e.path("source").asText())
                    .target(e.path("target").asText())
                    .relation(e.path("relation").asText("leads_to"))
                    .strength(e.path("strength").isNumber() ? e.path("strength").asDouble() : null)
                    .evidence(e.path("evidence").asText(""))
                    .confidence(e.path("confidence").isNumber() ? e.path("confidence").asDouble() : null)
                    .edgeType(e.path("edgeType").asText("inference"))
                    .build());
        }
        return result;
    }
}
