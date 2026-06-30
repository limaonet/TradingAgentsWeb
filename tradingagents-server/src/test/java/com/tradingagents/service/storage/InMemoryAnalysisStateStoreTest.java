package com.tradingagents.service.storage;

import com.tradingagents.model.AnalysisState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAnalysisStateStoreTest {

  private final InMemoryAnalysisStateStore store = new InMemoryAnalysisStateStore();

    @Test
    void shouldSaveAndRetrieveState() {
        AnalysisState state = AnalysisState.builder()
                .analysisId("test-1")
                .ticker("600519")
                .status("running")
                .startTime(LocalDateTime.now())
                .build();
        store.save(state);

        AnalysisState loaded = store.get("test-1");
        assertNotNull(loaded);
        assertEquals("600519", loaded.getTicker());
    }

    @Test
    void shouldUpdateStateInPlace() {
        store.save(AnalysisState.builder().analysisId("test-2").status("running").build());
        store.update("test-2", s -> s.setStatus("completed"));
        assertEquals("completed", store.get("test-2").getStatus());
    }
}
