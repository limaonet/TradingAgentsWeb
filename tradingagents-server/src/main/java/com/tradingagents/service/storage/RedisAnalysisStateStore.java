package com.tradingagents.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.model.AnalysisState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "analysis.storage", havingValue = "redis")
public class RedisAnalysisStateStore implements AnalysisStateStore {

    private static final String KEY_PREFIX = "analysis:state:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${analysis.state-ttl-days:7}")
    private int ttlDays;

    @Override
    public void save(AnalysisState state) {
        if (state == null || state.getAnalysisId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(state);
            String key = KEY_PREFIX + state.getAnalysisId();
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(ttlDays));
        } catch (Exception e) {
            log.error("【Redis】保存分析状态失败 analysisId={} 原因：{}", state.getAnalysisId(), e.getMessage());
            throw new IllegalStateException("Failed to save analysis state", e);
        }
    }

    @Override
    public AnalysisState get(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + analysisId);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, AnalysisState.class);
        } catch (Exception e) {
            log.error("【Redis】读取分析状态失败 analysisId={} 原因：{}", analysisId, e.getMessage());
            return null;
        }
    }

    @Override
    public void update(String analysisId, Consumer<AnalysisState> updater) {
        AnalysisState state = get(analysisId);
        if (state != null) {
            updater.accept(state);
            save(state);
        }
    }
}
