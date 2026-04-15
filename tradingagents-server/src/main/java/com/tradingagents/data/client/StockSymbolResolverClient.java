package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingagents.model.SymbolSearchItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 股票名称/代码解析客户端
 * 使用东方财富搜索接口，将股票名称解析为6位A股代码
 */
@Slf4j
@Component
public class StockSymbolResolverClient {

    private final WebClient webClient;

    @Value("${data.symbol-resolver.enabled:true}")
    private boolean enabled;

    @Value("${data.symbol-resolver.token:D43BF722C8E33BDC906FB84D85E326E8}")
    private String token;

    public StockSymbolResolverClient(
            @Value("${data.symbol-resolver.base-url:https://searchapi.eastmoney.com}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .build();
    }

    public Mono<String> resolveToSymbol(String input) {
        if (input == null || input.isBlank()) {
            return Mono.error(new IllegalArgumentException("Ticker is blank"));
        }

        String normalized = input.trim();
        if (normalized.matches("\\d{6}")) {
            return Mono.just(normalized);
        }
        if (!enabled) {
            return Mono.error(new IllegalArgumentException("Ticker must be 6-digit symbol when resolver disabled"));
        }

        return querySuggest(input)
                .flatMap(this::pickAStockCode)
                .doOnSuccess(code -> log.info("【标的解析】输入「{}」已解析为代码 {}", input, code))
                .onErrorResume(e -> Mono.error(new IllegalArgumentException("Unable to resolve ticker: " + input)));
    }

    public Mono<List<SymbolSearchItem>> searchCandidates(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return Mono.just(List.of());
        }
        if (!enabled) {
            return Mono.just(List.of());
        }
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        return querySuggest(keyword)
                .map(root -> mapCandidates(root, boundedLimit))
                .onErrorResume(e -> {
                    log.warn("【标的搜索】联想查询失败 keyword={} 原因：{}", keyword, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<JsonNode> querySuggest(String input) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/suggest/get")
                        .queryParam("input", input)
                        .queryParam("type", "14")
                        .queryParam("token", token)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private Mono<String> pickAStockCode(JsonNode root) {
        JsonNode data = root.path("QuotationCodeTable").path("Data");
        if (!data.isArray() || data.isEmpty()) {
            return Mono.error(new IllegalStateException("No matches"));
        }

        for (JsonNode item : data) {
            String code = item.path("Code").asText("");
            String classify = item.path("Classify").asText("");
            String marketType = item.path("MarketType").asText("");
            if (code.matches("\\d{6}") && "AStock".equalsIgnoreCase(classify) && ("0".equals(marketType) || "1".equals(marketType))) {
                return Mono.just(code);
            }
        }

        for (JsonNode item : data) {
            String code = item.path("Code").asText("");
            if (code.matches("\\d{6}")) {
                return Mono.just(code);
            }
        }

        return Mono.error(new IllegalStateException("No A-share 6-digit symbol found"));
    }

    private List<SymbolSearchItem> mapCandidates(JsonNode root, int limit) {
        JsonNode data = root.path("QuotationCodeTable").path("Data");
        if (!data.isArray() || data.isEmpty()) {
            return List.of();
        }

        List<SymbolSearchItem> result = new ArrayList<>();
        for (JsonNode item : data) {
            String code = item.path("Code").asText("");
            String name = item.path("Name").asText("");
            String classify = item.path("Classify").asText("");
            if (!code.matches("\\d{6}") || !"AStock".equalsIgnoreCase(classify)) {
                continue;
            }
            result.add(SymbolSearchItem.builder()
                    .code(code)
                    .name(name)
                    .marketType(item.path("MarketType").asText(""))
                    .quoteId(item.path("QuoteID").asText(""))
                    .build());
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }
}
