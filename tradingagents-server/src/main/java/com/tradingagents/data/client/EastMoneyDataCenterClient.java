package com.tradingagents.data.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingagents.data.model.FundamentalData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 东方财富数据中心公开 API（无需 token）
 * 文档来源：datacenter-web.eastmoney.com 开放接口
 */
@Slf4j
@Component
public class EastMoneyDataCenterClient {

    private final WebClient webClient;
    private final WebClient quoteClient;

    public EastMoneyDataCenterClient() {
        this.webClient = WebClient.builder()
                .baseUrl("https://datacenter-web.eastmoney.com/api/data/v1")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader("Referer", "https://data.eastmoney.com/")
                .build();
        this.quoteClient = WebClient.builder()
                .baseUrl("https://push2delay.eastmoney.com/api")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader("Referer", "https://quote.eastmoney.com/")
                .build();
    }

    public Mono<FundamentalData> getFundamentalData(String symbol, String period) {
        String code = normalizeCode(symbol);
        String filter = "(SECURITY_CODE=\"" + code + "\")";

        Mono<JsonNode> mainFin = fetchReport("RPT_F10_FINANCE_MAINFINADATA",
                "SECURITY_CODE,REPORT_DATE,ROEJQ,ZZCJLL,XSMLL,XSJLL,LD,SD,ZCFZL", filter);
        Mono<JsonNode> income = fetchReport("RPT_DMSK_FN_INCOME",
                "SECURITY_CODE,REPORT_DATE,TOTAL_OPERATE_INCOME,PARENT_NETPROFIT", filter, 8);
        Mono<JsonNode> cashflow = fetchReport("RPT_DMSK_FN_CASHFLOW",
                "SECURITY_CODE,REPORT_DATE,NETCASH_OPERATE,NETCASH_INVEST,NETCASH_FINANCE", filter);
        Mono<JsonNode> quote = fetchQuote(code);

        return Mono.zip(mainFin, income, cashflow, quote)
                .map(tuple -> mergeFundamentalData(code, period, tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()))
                .doOnError(e -> log.error("【东财数据中心】获取基本面失败 标的={} 原因：{}", symbol, e.getMessage()));
    }

    private Mono<JsonNode> fetchReport(String reportName, String columns, String filter) {
        return fetchReport(reportName, columns, filter, 1);
    }

    private Mono<JsonNode> fetchReport(String reportName, String columns, String filter, int pageSize) {
        return webClient.get()
                .uri(uri -> uri
                        .path("/get")
                        .queryParam("reportName", reportName)
                        .queryParam("columns", columns)
                        .queryParam("pageNumber", 1)
                        .queryParam("pageSize", pageSize)
                        .queryParam("sortColumns", "REPORT_DATE")
                        .queryParam("sortTypes", -1)
                        .queryParam("filter", filter)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode node = objectMapper.readTree(body);
                        if (!node.path("success").asBoolean(false)) {
                            throw new IllegalStateException(node.path("message").asText("datacenter error"));
                        }
                        return node.path("result").path("data");
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse datacenter response", e);
                    }
                });
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Mono<JsonNode> fetchQuote(String code) {
        String secid = code.startsWith("6") ? "1." + code : "0." + code;
        return quoteClient.get()
                .uri(uri -> uri
                        .path("/qt/stock/get")
                        .queryParam("secid", secid)
                        .queryParam("fields", "f162,f167,f168")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        return objectMapper.readTree(body).path("data");
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse quote response", e);
                    }
                });
    }

    private FundamentalData mergeFundamentalData(String code, String period,
                                                    JsonNode mainFinArr, JsonNode incomeArr,
                                                    JsonNode cashflowArr, JsonNode quote) {
        JsonNode mainFin = mainFinArr.isArray() && !mainFinArr.isEmpty() ? mainFinArr.get(0) : null;
        JsonNode income = incomeArr.isArray() && !incomeArr.isEmpty() ? incomeArr.get(0) : null;
        JsonNode incomeYoY = findYearAgoIncome(incomeArr, income);
        JsonNode cashflow = cashflowArr.isArray() && !cashflowArr.isEmpty() ? cashflowArr.get(0) : null;

        String endDate = Optional.ofNullable(mainFin)
                .map(n -> n.path("REPORT_DATE").asText(""))
                .filter(s -> !s.isBlank())
                .orElse(period);

        FundamentalData.FundamentalDataBuilder builder = FundamentalData.builder()
                .tsCode(code)
                .endDate(endDate);

        if (mainFin != null) {
            builder.roe(decimal(mainFin, "ROEJQ"))
                    .roa(decimal(mainFin, "ZZCJLL"))
                    .grossMargin(decimal(mainFin, "XSMLL"))
                    .netMargin(decimal(mainFin, "XSJLL"))
                    .currentRatio(decimal(mainFin, "LD"))
                    .quickRatio(decimal(mainFin, "SD"))
                    .debtToAsset(decimal(mainFin, "ZCFZL"));
        }

        if (income != null) {
            BigDecimal revenue = decimal(income, "TOTAL_OPERATE_INCOME");
            BigDecimal profit = decimal(income, "PARENT_NETPROFIT");
            if (incomeYoY != null) {
                builder.revenueGrowth(growthRate(revenue, decimal(incomeYoY, "TOTAL_OPERATE_INCOME")))
                        .profitGrowth(growthRate(profit, decimal(incomeYoY, "PARENT_NETPROFIT")));
            }
        }

        if (cashflow != null) {
            builder.operatingCashFlow(toWanYuan(decimal(cashflow, "NETCASH_OPERATE")))
                    .investingCashFlow(toWanYuan(decimal(cashflow, "NETCASH_INVEST")))
                    .financingCashFlow(toWanYuan(decimal(cashflow, "NETCASH_FINANCE")));
        }

        if (quote != null && !quote.isMissingNode()) {
            // 东财行情字段放大了 100 倍
            builder.peRatio(scaleQuote(quote, "f162"))
                    .pbRatio(scaleQuote(quote, "f167"))
                    .psRatio(scaleQuote(quote, "f168"));
        }

        return builder.build();
    }

    private BigDecimal scaleQuote(JsonNode quote, String field) {
        if (!quote.has(field) || quote.get(field).isNull()) {
            return null;
        }
        return BigDecimal.valueOf(quote.get(field).asDouble())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toWanYuan(BigDecimal yuan) {
        if (yuan == null) return null;
        return yuan.divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
    }

    /**
     * 匹配去年同期报告（同比），避免相邻季度被误算为环比。
     */
    private JsonNode findYearAgoIncome(JsonNode incomeArr, JsonNode current) {
        if (incomeArr == null || !incomeArr.isArray() || current == null) {
            return null;
        }
        String currentDate = current.path("REPORT_DATE").asText("");
        if (currentDate.isBlank() || currentDate.length() < 10) {
            return incomeArr.size() > 1 ? incomeArr.get(1) : null;
        }
        String targetDate = shiftReportDateOneYear(currentDate);
        for (JsonNode row : incomeArr) {
            if (targetDate.equals(row.path("REPORT_DATE").asText(""))) {
                return row;
            }
        }
        String targetMonthDay = currentDate.substring(5);
        for (JsonNode row : incomeArr) {
            String rowDate = row.path("REPORT_DATE").asText("");
            if (rowDate.length() >= 10 && rowDate.substring(5).equals(targetMonthDay)
                    && rowDate.compareTo(currentDate) < 0) {
                return row;
            }
        }
        return incomeArr.size() > 1 ? incomeArr.get(1) : null;
    }

    private String shiftReportDateOneYear(String reportDate) {
        try {
            int year = Integer.parseInt(reportDate.substring(0, 4));
            return (year - 1) + reportDate.substring(4);
        } catch (Exception e) {
            return reportDate;
        }
    }

    private BigDecimal growthRate(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return BigDecimal.valueOf(node.get(field).asDouble()).setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeCode(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is blank");
        }
        String normalized = symbol.trim();
        if (normalized.matches("[01]\\.\\d{6}")) {
            return normalized.substring(2);
        }
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("Invalid symbol: " + symbol);
        }
        return normalized;
    }
}
