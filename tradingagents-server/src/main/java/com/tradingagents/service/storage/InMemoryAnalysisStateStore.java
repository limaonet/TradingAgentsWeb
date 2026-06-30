package com.tradingagents.service.storage;

import com.tradingagents.model.AnalysisState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "analysis.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryAnalysisStateStore implements AnalysisStateStore {

    private final Map<String, AnalysisState> cache = new ConcurrentHashMap<>();

    @Override
    public void save(AnalysisState state) {
        if (state != null && state.getAnalysisId() != null) {
            cache.put(state.getAnalysisId(), state);
        }
    }

    @Override
    public AnalysisState get(String analysisId) {
        return cache.get(analysisId);
    }

    @Override
    public void update(String analysisId, Consumer<AnalysisState> updater) {
        AnalysisState state = cache.get(analysisId);
        if (state != null) {
            updater.accept(state);
        }
    }
}
