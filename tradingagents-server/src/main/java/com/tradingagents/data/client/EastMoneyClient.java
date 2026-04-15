package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 东方财富数据客户端
 * 主要数据源：A股实时行情、K线数据、资金流向
 */
@Slf4j
@Component
public class EastMoneyClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${data.eastmoney.enabled:true}")
    private boolean enabled;

    @Value("${data.eastmoney.cookie:}")
    private String cookie;

    public EastMoneyClient(@Value("${data.eastmoney.base-url:https://push2.eastmoney.com/api}") String baseUrl,
                          @Value("${data.eastmoney.cookie:}") String cookie) {
        this.cookie = cookie;
        
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "*/*")
                .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .defaultHeader("Referer", "https://quote.eastmoney.com/");
        
        // 如果配置了 Cookie，添加到请求头
        if (cookie != null && !cookie.isEmpty()) {
            builder.defaultHeader("Cookie", cookie);
        }
        
        this.webClient = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取股票代码映射（东方财富格式）
     */
    private String convertToEastMoneyCode(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Invalid symbol: symbol is blank");
        }

        String normalized = symbol.trim();

        // 已经是东方财富格式，例如 1.600000 或 0.000001
        if (normalized.matches("[01]\\.\\d{6}")) {
            return normalized;
        }

        // A股代码转换
        if (normalized.matches("\\d{6}")) {
            if (normalized.startsWith("6")) {
                return "1." + normalized;  // 上海
            } else {
                return "0." + normalized;  // 深圳
            }
        }

        throw new IllegalArgumentException("Invalid symbol format: " + symbol + ", expected 6-digit A-share code");
    }

    /**
     * 获取实时行情
     */
    public Mono<Map<String, Object>> getRealtimeQuote(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        String emCode = convertToEastMoneyCode(symbol);
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/qt/stock/get")
                        .queryParam("secid", emCode)
                        .queryParam("fields", "f43,f44,f45,f46,f47,f48,f57,f58,f60,f170")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseRealtimeData)
                .doOnError(e -> log.error("【东财】获取实时行情失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取K线数据
     */
    public Mono<JsonNode> getKlineData(String symbol, int period, int limit) {
        if (!enabled) {
            return Mono.empty();
        }

        String emCode = convertToEastMoneyCode(symbol);
        
        // period: 101=日, 102=周, 103=月
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/qt/stock/kline/get")
                        .queryParam("secid", emCode)
                        .queryParam("fields1", "f1,f2,f3,f4,f5,f6")
                        .queryParam("fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61")
                        .queryParam("klt", period)
                        .queryParam("fqt", "0")
                        .queryParam("end", "20500101")
                        .queryParam("lmt", limit)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnError(e -> log.error("【东财】获取 K 线失败 标的={} 原因：{}", symbol, e.getMessage()))
                .flatMap(data -> {
                    JsonNode klineNode = data.path("data").path("klines");
                    if (!klineNode.isArray() || klineNode.isEmpty()) {
                        String message = "【东财】K 线数据为空 标的=" + symbol;
                        log.error(message);
                        return Mono.error(new IllegalStateException(message));
                    }
                    return Mono.just(data);
                });
    }

    /**
     * 获取资金流向
     */
    public Mono<Map<String, Object>> getCapitalFlow(String symbol) {
        if (!enabled) {
            return Mono.empty();
        }

        String emCode = convertToEastMoneyCode(symbol);
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/qt/stock/capital/get")
                        .queryParam("secid", emCode)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseCapitalFlow)
                .doOnError(e -> log.error("【东财】获取资金流向失败 标的={} 原因：{}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 获取板块资金流向
     */
    public Mono<JsonNode> getSectorCapitalFlow(String sectorType) {
        if (!enabled) {
            return Mono.empty();
        }

        // sectorType: industry=行业, concept=概念, area=地域
        String plateType = switch (sectorType) {
            case "industry" -> "2";
            case "concept" -> "3";
            case "area" -> "1";
            default -> "2";
        };

        return webClient.get()
                .uri("http://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=20&po=1&np=1&fltt=2&invt=2&fid=f62&fs=m:90+t:" + plateType + "&fields=f12,f13,f14,f20,f21,f22,f23,f24,f25,f26,f27,f28,f29,f30,f31,f32,f33,f34,f35,f36,f37,f38,f39,f40,f41,f42,f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f65,f66,f67,f68,f69,f70,f71,f72,f73,f74,f75,f76,f77,f78,f79,f80,f81,f82,f83,f84,f85,f86,f87,f88,f89,f90,f91")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnError(e -> log.error("【东财】获取板块资金流失败：{}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * 解析实时行情数据
     */
    private Map<String, Object> parseRealtimeData(String response) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 东方财富返回的是 JavaScript 格式，需要解析
            if (response.contains("data")) {
                int start = response.indexOf("{");
                int end = response.lastIndexOf("}") + 1;
                String json = response.substring(start, end);
                JsonNode node = objectMapper.readTree(json);
                JsonNode data = node.get("data");
                
                if (data != null) {
                    result.put("price", data.get("f43"));      // 最新价
                    result.put("open", data.get("f44"));       // 开盘价
                    result.put("high", data.get("f45"));       // 最高价
                    result.put("low", data.get("f46"));        // 最低价
                    result.put("volume", data.get("f47"));     // 成交量
                    result.put("amount", data.get("f48"));     // 成交额
                    result.put("code", data.get("f57"));       // 股票代码
                    result.put("name", data.get("f58"));       // 股票名称
                    result.put("preClose", data.get("f60"));   // 昨收
                    result.put("changePct", data.get("f170")); // 涨跌幅
                }
            }
        } catch (Exception e) {
            log.error("【东财】解析实时行情失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 解析资金流向数据
     */
    private Map<String, Object> parseCapitalFlow(String response) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (response.contains("data")) {
                int start = response.indexOf("{");
                int end = response.lastIndexOf("}") + 1;
                String json = response.substring(start, end);
                JsonNode node = objectMapper.readTree(json);
                JsonNode data = node.get("data");
                
                if (data != null) {
                    result.put("mainInflow", data.get("f44"));     // 主力流入
                    result.put("mainOutflow", data.get("f45"));    // 主力流出
                    result.put("mainNet", data.get("f46"));        // 主力净流入
                    result.put("retailInflow", data.get("f47"));   // 散户流入
                    result.put("retailOutflow", data.get("f48"));  // 散户流出
                    result.put("retailNet", data.get("f49"));      // 散户净流入
                }
            }
        } catch (Exception e) {
            log.error("【东财】解析资金流向失败：{}", e.getMessage());
        }
        return result;
    }
}
