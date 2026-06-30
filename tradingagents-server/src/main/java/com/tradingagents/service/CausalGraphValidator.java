package com.tradingagents.service;

import com.tradingagents.data.model.CausalEdge;
import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.data.model.CausalNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CausalGraphValidator {

    private static final double MIN_CONFIDENCE = 0.35;

    public CausalGraph validateAndClean(CausalGraph graph, String symbol) {
        if (graph == null) {
            return CausalGraph.builder().summary("因果图为空").build();
        }

        List<CausalNode> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<CausalEdge> edges = graph.getEdges() == null ? List.of() : graph.getEdges();

        List<CausalNode> cleanedNodes = nodes.stream()
                .filter(n -> n.getId() != null && !n.getId().isBlank())
                .filter(n -> n.getLabel() != null && !n.getLabel().isBlank())
                .filter(n -> n.getConfidence() == null || n.getConfidence() >= MIN_CONFIDENCE)
                .map(n -> {
                    if ("event".equals(n.getType()) && (n.getSourceRef() == null || n.getSourceRef().isBlank())) {
                        n.setConfidence(n.getConfidence() == null ? 0.4 : Math.min(n.getConfidence(), 0.55));
                        n.setDescription((n.getDescription() == null ? "" : n.getDescription()) + " [未绑定来源，置信度已下调]");
                    }
                    return n;
                })
                .collect(Collectors.toList());

        Set<String> nodeIds = cleanedNodes.stream().map(CausalNode::getId).collect(Collectors.toSet());

        List<CausalEdge> cleanedEdges = edges.stream()
                .filter(e -> nodeIds.contains(e.getSource()) && nodeIds.contains(e.getTarget()))
                .filter(e -> e.getConfidence() == null || e.getConfidence() >= MIN_CONFIDENCE)
                .map(e -> {
                    if ("fact".equals(e.getEdgeType()) && (e.getEvidence() == null || e.getEvidence().isBlank())) {
                        e.setEdgeType("inference");
                        e.setConfidence(e.getConfidence() == null ? 0.5 : Math.min(e.getConfidence(), 0.6));
                    }
                    return e;
                })
                .collect(Collectors.toList());

        if (!hasContradiction(cleanedEdges) && cleanedEdges.size() >= 2) {
            log.info("【因果图】未检测到矛盾边，图谱可能偏单向 narrative 标的={}", symbol);
        }

        return CausalGraph.builder()
                .summary(graph.getSummary())
                .nodes(cleanedNodes)
                .edges(cleanedEdges)
                .build();
    }

    private boolean hasContradiction(List<CausalEdge> edges) {
        Set<String> relations = new HashSet<>();
        for (CausalEdge e : edges) {
            if (e.getRelation() != null) {
                relations.add(e.getRelation().toLowerCase());
            }
        }
        return relations.contains("contradicts") || relations.contains("dampens");
    }
}
