package com.tradingagents.service.storage;

import com.tradingagents.model.AnalysisState;

import java.util.function.Consumer;

public interface AnalysisStateStore {

    void save(AnalysisState state);

    AnalysisState get(String analysisId);

    void update(String analysisId, Consumer<AnalysisState> updater);
}
