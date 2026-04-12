package com.tradingagents.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 分析进度 WebSocket 处理器
 * 负责向客户端推送实时分析进度
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisProgressHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发送进度更新
     */
    public void sendProgress(String analysisId, String agent, String status, String message) {
        Map<String, Object> payload = createPayload("progress", agent, status, message, null);
        messagingTemplate.convertAndSend("/topic/analysis/" + analysisId, payload);
        log.debug("Sent progress update for analysis {}: agent={}, status={}", analysisId, agent, status);
    }

    /**
     * 发送报告更新
     */
    public void sendReport(String analysisId, String reportType, String content) {
        Map<String, Object> payload = createPayload("report", null, null, null, reportType);
        payload.put("reportType", reportType);
        payload.put("content", content);
        messagingTemplate.convertAndSend("/topic/analysis/" + analysisId, payload);
        log.debug("Sent report update for analysis {}: type={}", analysisId, reportType);
    }

    /**
     * 发送分析完成消息
     */
    public void sendComplete(String analysisId, String finalDecision) {
        Map<String, Object> payload = createPayload("complete", null, "completed", "分析完成", null);
        payload.put("finalDecision", finalDecision);
        messagingTemplate.convertAndSend("/topic/analysis/" + analysisId, payload);
        log.info("Analysis completed: {}", analysisId);
    }

    /**
     * 发送错误消息
     */
    public void sendError(String analysisId, String agent, String errorMessage) {
        Map<String, Object> payload = createPayload("error", agent, "error", errorMessage, null);
        messagingTemplate.convertAndSend("/topic/analysis/" + analysisId, payload);
        log.error("Analysis error for {}: {}", analysisId, errorMessage);
    }

    /**
     * 发送 Agent 状态更新
     */
    public void sendAgentStatus(String analysisId, String agent, String status, String detail) {
        Map<String, Object> payload = createPayload("agent_status", agent, status, detail, null);
        messagingTemplate.convertAndSend("/topic/analysis/" + analysisId, payload);
    }

    /**
     * 发送辩论状态更新
     */
    public void sendDebateUpdate(String analysisId, String debateType, String speaker, String content, int round) {
        Map<String, Object> payload = createPayload("debate", speaker, "running", content, null);
        payload.put("debateType", debateType);
        payload.put("round", round);
        messagingTemplate.convertAndSend("/topic/analysis/" + analysisId, payload);
    }

    /**
     * 创建消息负载
     */
    private Map<String, Object> createPayload(String type, String agent, String status, String content, String reportType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("agent", agent);
        payload.put("status", status);
        payload.put("content", content);
        payload.put("reportType", reportType);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return payload;
    }
}
