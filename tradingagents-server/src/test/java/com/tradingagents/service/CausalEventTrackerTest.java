package com.tradingagents.service;

import com.tradingagents.data.model.StockEvent;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CausalEventTrackerTest {

    @Test
    void findNewEvents_returnsOnlyUnseen() {
        StockEvent a = StockEvent.builder().eventType("news").title("茅台提价").publishedAt("2026-06-30").build();
        StockEvent b = StockEvent.builder().eventType("announcement").title("半年报预告").publishedAt("2026-06-29").build();

        Set<String> seen = new HashSet<>(List.of(CausalEventTracker.eventKey(a)));
        List<StockEvent> fresh = CausalEventTracker.findNewEvents(List.of(a, b), seen);

        assertEquals(1, fresh.size());
        assertEquals("半年报预告", fresh.get(0).getTitle());
    }
}
