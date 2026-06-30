package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CausalNode {
    private String id;
    /** event | factor | indicator | outcome */
    private String type;
    private String label;
    private String description;
    private String eventTime;
    private String sourceRef;
    private Double confidence;
}
