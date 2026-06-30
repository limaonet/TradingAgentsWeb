package com.tradingagents.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CausalEdge {
    private String id;
    private String source;
    private String target;
    /** causes | leads_to | amplifies | dampens | contradicts | supports */
    private String relation;
    private Double strength;
    private String evidence;
    private Double confidence;
    /** fact | inference */
    private String edgeType;
}
