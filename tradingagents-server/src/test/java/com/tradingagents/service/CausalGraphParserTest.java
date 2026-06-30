package com.tradingagents.service;

import com.tradingagents.data.model.CausalGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CausalGraphParserTest {

    private final CausalGraphParser parser = new CausalGraphParser();

    @Test
    void shouldParseJsonBlock() {
        String input = """
                ```json
                {
                  "summary": "分红利好传导至估值修复",
                  "nodes": [
                    {"id":"evt_1","type":"event","label":"年报分红","confidence":0.9}
                  ],
                  "edges": [
                    {"id":"e1","source":"evt_1","target":"out_1","relation":"causes","edgeType":"fact"}
                  ]
                }
                ```
                """;
        CausalGraph graph = parser.parse(input);
        assertEquals("分红利好传导至估值修复", graph.getSummary());
        assertEquals(1, graph.getNodes().size());
        assertEquals("evt_1", graph.getNodes().get(0).getId());
        assertEquals(1, graph.getEdges().size());
    }
}
