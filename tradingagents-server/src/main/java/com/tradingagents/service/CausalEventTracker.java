package com.tradingagents.service;

import com.tradingagents.data.model.StockEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 事件去重与增量检测（用于准实时因果链刷新）。
 */
public final class CausalEventTracker {

    private CausalEventTracker() {}

    public static String eventKey(StockEvent event) {
        String title = event.getTitle() == null ? "" : event.getTitle().replaceAll("\\s+", "").toLowerCase();
        String time = event.getPublishedAt() == null ? "" : event.getPublishedAt();
        String type = event.getEventType() == null ? "" : event.getEventType();
        return type + "|" + time + "|" + title;
    }

    public static List<String> keys(List<StockEvent> events) {
        List<String> keys = new ArrayList<>();
        if (events == null) {
            return keys;
        }
        for (StockEvent e : events) {
            keys.add(eventKey(e));
        }
        return keys;
    }

    public static List<StockEvent> findNewEvents(List<StockEvent> events, Set<String> seenKeys) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Set<String> seen = seenKeys == null ? Set.of() : seenKeys;
        List<StockEvent> fresh = new ArrayList<>();
        for (StockEvent e : events) {
            if (!seen.contains(eventKey(e))) {
                fresh.add(e);
            }
        }
        return fresh;
    }

    public static Set<String> mergeKeys(Set<String> existing, List<StockEvent> events) {
        Set<String> merged = existing == null ? new HashSet<>() : new HashSet<>(existing);
        merged.addAll(keys(events));
        return merged;
    }
}
