package com.tradingagents.controller;

import com.tradingagents.model.AnalysisRequest;
import com.tradingagents.model.AnalysisState;
import com.tradingagents.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 启动分析
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startAnalysis(@RequestBody AnalysisRequest request) {
        log.info("Starting analysis for ticker: {} on date: {}", request.getTicker(), request.getDate());
        
        String analysisId = analysisService.startAnalysis(request);
        
        Map<String, String> response = new HashMap<>();
        response.put("analysisId", analysisId);
        response.put("status", "started");
        response.put("message", "分析已启动，请通过 WebSocket 监听进度");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取分析状态
     */
    @GetMapping("/{analysisId}")
    public ResponseEntity<?> getAnalysisState(@PathVariable String analysisId) {
        AnalysisState state = analysisService.getAnalysisState(analysisId);
        
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(state);
    }

    /**
     * 获取分析报告
     */
    @GetMapping("/{analysisId}/reports")
    public ResponseEntity<?> getAnalysisReports(@PathVariable String analysisId) {
        AnalysisState state = analysisService.getAnalysisState(analysisId);
        
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(state.getAllReports());
    }
}
