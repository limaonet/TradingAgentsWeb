package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.StockData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 新浪行情客户端
 * 通过新浪 K 线接口获取日线数据
 */
@Slf4j
@Component
public class SinaMarketClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${data.sina.enabled:true}")
    private boolean enabled;

    public SinaMarketClient(@Value("${data.sina.kline-base-url:https://quotes.sina.cn}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "*/*")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public Mono<List<StockData>> getDailyKline(String symbol, int limit) {
        if (!enabled) {
            return Mono.empty();
        }

        String sinaSymbol = toSinaSymbol(symbol);
        if (sinaSymbol == null) {
            return Mono.error(new IllegalArgumentException("Invalid symbol format for sina: " + symbol));
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cn/api/jsonp_v2.php/var%20_data=/CN_MarketDataService.getKLineData")
                        .queryParam("symbol", sinaSymbol)
                        .queryParam("scale", 240)
                        .queryParam("ma", "no")
                        .queryParam("datalen", Math.max(60, limit))
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> parseKline(body, symbol))
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        return Mono.error(new IllegalStateException("Empty kline data from Sina for symbol " + symbol));
                    }
                    return Mono.just(list);
                })
                .doOnError(e -> log.error("【新浪行情】请求 K 线失败 标的={} 原因：{}", symbol, e.getMessage()));
    }

    private String toSinaSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        String normalized = symbol.trim();
        if (normalized.matches("\\d{6}")) {
            return normalized.startsWith("6") ? "sh" + normalized : "sz" + normalized;
        }
        return null;
    }

    private List<StockData> parseKline(String body, String symbol) {
        List<StockData> result = new ArrayList<>();
        try {
            int start = body.indexOf('(');
            int end = body.lastIndexOf(')');
            if (start < 0 || end <= start) {
                return result;
            }
            String jsonArray = body.substring(start + 1, end);
            JsonNode array = objectMapper.readTree(jsonArray);
            if (!array.isArray()) {
                return result;
            }

            for (JsonNode n : array) {
                String day = n.path("day").asText(null);
                if (day == null || day.isBlank()) {
                    continue;
                }
                StockData data = StockData.builder()
                        .tsCode(symbol)
                        .tradeDate(LocalDate.parse(day))
                        .open(new BigDecimal(n.path("open").asText("0")))
                        .close(new BigDecimal(n.path("close").asText("0")))
                        .high(new BigDecimal(n.path("high").asText("0")))
                        .low(new BigDecimal(n.path("low").asText("0")))
                        .vol(new BigDecimal(n.path("volume").asText("0")))
                        .amount(BigDecimal.ZERO)
                        .build();
                result.add(data);
            }
        } catch (Exception e) {
            log.error("【新浪行情】解析 K 线失败 标的={} 原因：{}", symbol, e.getMessage());
        }
        return result;
    }
}
