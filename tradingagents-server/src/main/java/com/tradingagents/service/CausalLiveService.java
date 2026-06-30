package com.tradingagents.service;

import com.tradingagents.data.model.CausalGraph;
import com.tradingagents.data.model.StockEvent;
import com.tradingagents.data.service.EventCollectionService;
import com.tradingagents.model.AnalysisState;
import com.tradingagents.service.storage.AnalysisStateStore;
import com.tradingagents.websocket.AnalysisProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 准实时因果链：轮询新闻/公告，发现新事件后增量更新图谱并 WebSocket 推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CausalLiveService {

    private final AnalysisStateStore analysisStateStore;
    private final EventCollectionService eventCollectionService;
    private final CausalAnalysisService causalAnalysisService;
    private final AnalysisProgressHandler progressHandler;

    @Value("${causal.live.enabled:true}")
    private boolean liveEnabled;

    @Value("${causal.live.min-refresh-interval-ms:120000}")
    private long minRefreshIntervalMs;

    @Value("${causal.live.max-session-hours:2}")
    private long maxSessionHours;

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, LocalDateTime> sessionStartedAt = new ConcurrentHashMap<>();

    public boolean enableLive(String analysisId) {
        AnalysisState state = analysisStateStore.get(analysisId);
        if (state == null) {
            return false;
        }
        if (state.getCausalGraph() == null || state.getCausalGraph().getNodes() == null
                || state.getCausalGraph().getNodes().isEmpty()) {
            throw new IllegalStateException("因果图尚未生成，无法开启实时追踪");
        }

        List<StockEvent> current = eventCollectionService.collectEvents(state.getTicker(), state.getTicker());
        Set<String> seen = CausalEventTracker.mergeKeys(state.getCausalSeenEventKeys(), current);

        analysisStateStore.update(analysisId, s -> {
            s.setCausalLiveEnabled(true);
            s.setCausalSeenEventKeys(seen);
            if (s.getCausalLastRefreshedAt() == null) {
                s.setCausalLastRefreshedAt(LocalDateTime.now());
            }
        });

        activeSessions.add(analysisId);
        sessionStartedAt.put(analysisId, LocalDateTime.now());
        log.info("【因果实时】已开启 analysisId={} 标的={}", analysisId, state.getTicker());
        return true;
    }

    public void disableLive(String analysisId) {
        activeSessions.remove(analysisId);
        sessionStartedAt.remove(analysisId);
        analysisStateStore.update(analysisId, s -> s.setCausalLiveEnabled(false));
        log.info("【因果实时】已关闭 analysisId={}", analysisId);
    }

    public boolean isLiveEnabled(String analysisId) {
        AnalysisState state = analysisStateStore.get(analysisId);
        return state != null && Boolean.TRUE.equals(state.getCausalLiveEnabled());
    }

    @Scheduled(fixedDelayString = "${causal.live.poll-interval-ms:60000}")
    public void pollLiveSessions() {
        if (!liveEnabled || activeSessions.isEmpty()) {
            return;
        }
        for (String analysisId : Set.copyOf(activeSessions)) {
            try {
                refreshIfNeeded(analysisId);
            } catch (Exception e) {
                log.warn("【因果实时】刷新失败 analysisId={} 原因：{}", analysisId, e.getMessage());
            }
        }
    }

    private void refreshIfNeeded(String analysisId) {
        AnalysisState state = analysisStateStore.get(analysisId);
        if (state == null || !Boolean.TRUE.equals(state.getCausalLiveEnabled())) {
            activeSessions.remove(analysisId);
            sessionStartedAt.remove(analysisId);
            return;
        }

        LocalDateTime started = sessionStartedAt.get(analysisId);
        if (started != null && Duration.between(started, LocalDateTime.now()).toHours() >= maxSessionHours) {
            log.info("【因果实时】会话超时自动关闭 analysisId={}", analysisId);
            disableLive(analysisId);
            progressHandler.sendCausalLiveStatus(analysisId, false, "实时追踪已超时自动关闭（最长 " + maxSessionHours + " 小时）");
            return;
        }

        LocalDateTime lastRefresh = state.getCausalLastRefreshedAt();
        if (lastRefresh != null) {
            long sinceMs = Duration.between(lastRefresh, LocalDateTime.now()).toMillis();
            if (sinceMs < minRefreshIntervalMs) {
                return;
            }
        }

        List<StockEvent> allEvents = eventCollectionService.collectEvents(state.getTicker(), state.getTicker());
        Set<String> seen = state.getCausalSeenEventKeys() == null ? new HashSet<>() : new HashSet<>(state.getCausalSeenEventKeys());
        List<StockEvent> newEvents = CausalEventTracker.findNewEvents(allEvents, seen);
        if (newEvents.isEmpty()) {
            return;
        }

        log.info("【因果实时】发现 {} 条新事件 analysisId={} 标的={}", newEvents.size(), analysisId, state.getTicker());
        progressHandler.sendCausalLiveStatus(analysisId, true, "检测到 " + newEvents.size() + " 条新事件，正在增量更新因果图...");

        CausalGraph updated = causalAnalysisService.incrementalUpdate(
                state.getCausalGraph(),
                newEvents,
                state.getTicker(),
                state.getDate(),
                state.getMarketReport(),
                state.getSentimentReport(),
                state.getFundamentalsReport());

        Set<String> mergedSeen = CausalEventTracker.mergeKeys(seen, newEvents);
        LocalDateTime now = LocalDateTime.now();

        analysisStateStore.update(analysisId, s -> {
            s.setCausalGraph(updated);
            s.setCausalReport(updated.getSummary());
            s.setCausalSeenEventKeys(mergedSeen);
            s.setCausalLastRefreshedAt(now);
        });

        progressHandler.sendCausalGraphUpdate(analysisId, updated, newEvents.size(), now);
    }

    /** 因果分析完成时登记已见事件，便于后续增量检测 */
    public void seedSeenEvents(String analysisId, String ticker) {
        AnalysisState state = analysisStateStore.get(analysisId);
        if (state == null) {
            return;
        }
        List<StockEvent> events = eventCollectionService.collectEvents(ticker, ticker);
        Set<String> seen = CausalEventTracker.mergeKeys(state.getCausalSeenEventKeys(), events);
        analysisStateStore.update(analysisId, s -> {
            s.setCausalSeenEventKeys(seen);
            if (s.getCausalLastRefreshedAt() == null) {
                s.setCausalLastRefreshedAt(LocalDateTime.now());
            }
        });
    }
}
