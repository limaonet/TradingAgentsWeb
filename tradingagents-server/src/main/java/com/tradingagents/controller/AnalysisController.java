package com.tradingagents.controller;

import com.tradingagents.model.AnalysisRequest;
import com.tradingagents.model.AnalysisState;
import com.tradingagents.model.SymbolSearchItem;
import com.tradingagents.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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
        log.info("【分析】收到启动请求：标的={}，日期={}", request.getTicker(), request.getDate());
        
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

    /**
     * 股票模糊搜索（用于前端下拉联想）
     */
    @GetMapping("/symbols/search")
    public ResponseEntity<List<SymbolSearchItem>> searchSymbols(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(analysisService.searchSymbols(keyword, limit));
    }
}
