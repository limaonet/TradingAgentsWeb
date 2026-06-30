package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CausalGraph {
    @Builder.Default
    private List<CausalNode> nodes = new ArrayList<>();
    @Builder.Default
    private List<CausalEdge> edges = new ArrayList<>();
    private String summary;
}
