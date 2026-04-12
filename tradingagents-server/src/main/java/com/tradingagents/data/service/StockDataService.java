package com.tradingagents.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradingagents.data.client.EastMoneyClient;
import com.tradingagents.data.model.FundamentalData;
import com.tradingagents.data.model.StockData;
import com.tradingagents.data.model.TechnicalIndicators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 股票数据服务
 * 以东方财富为主要数据源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {

    private final EastMoneyClient eastMoneyClient;

    /**
     * 获取股票历史数据
     */
    public Mono<List<StockData>> getStockHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        int days = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 10;
        
        return eastMoneyClient.getKlineData(symbol, 101, days)
                .map(klineData -> parseKlineData(symbol, klineData))
                .defaultIfEmpty(new ArrayList<>());
    }

    /**
     * 获取技术指标
     */
    public Mono<TechnicalIndicators> getTechnicalIndicators(String symbol, LocalDate date) {
        return getStockHistory(symbol, date.minusDays(100), date)
                .map(history -> calculateIndicators(symbol, history));
    }

    /**
     * 获取基本面数据
     */
    public Mono<FundamentalData> getFundamentalData(String symbol, String period) {
        // 东方财富不直接提供财务数据，返回模拟数据
        // 实际项目中可以接入其他财务数据源
        return Mono.just(createMockFundamentalData(symbol, period));
    }

    /**
     * 获取实时行情
     */
    public Mono<Map<String, Object>> getRealtimeQuote(String symbol) {
        return eastMoneyClient.getRealtimeQuote(symbol);
    }

    /**
     * 解析K线数据
     */
    private List<StockData> parseKlineData(String symbol, JsonNode klineData) {
        List<StockData> result = new ArrayList<>();
        
        try {
            JsonNode klines = klineData.get("data").get("klines");
            if (klines != null && klines.isArray()) {
                for (JsonNode kline : klines) {
                    String[] parts = kline.asText().split(",");
                    if (parts.length >= 6) {
                        StockData data = StockData.builder()
                                .tsCode(symbol)
                                .tradeDate(LocalDate.parse(parts[0]))
                                .open(new BigDecimal(parts[1]))
                                .close(new BigDecimal(parts[2]))
                                .high(new BigDecimal(parts[3]))
                                .low(new BigDecimal(parts[4]))
                                .vol(new BigDecimal(parts[5]))
                                .amount(parts.length > 6 ? new BigDecimal(parts[6]) : BigDecimal.ZERO)
                                .build();
                        result.add(data);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse kline data: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 计算技术指标
     */
    private TechnicalIndicators calculateIndicators(String symbol, List<StockData> history) {
        if (history == null || history.size() < 60) {
            return TechnicalIndicators.builder().tsCode(symbol).build();
        }

        TechnicalIndicators indicators = new TechnicalIndicators();
        indicators.setTsCode(symbol);

        // 计算均线
        indicators.setMa5(calculateMA(history, 5));
        indicators.setMa10(calculateMA(history, 10));
        indicators.setMa20(calculateMA(history, 20));
        indicators.setMa60(calculateMA(history, 60));

        // 计算RSI
        indicators.setRsi6(calculateRSI(history, 6));
        indicators.setRsi12(calculateRSI(history, 12));
        indicators.setRsi24(calculateRSI(history, 24));

        // 计算MACD
        calculateMACD(history, indicators);

        // 计算KDJ
        calculateKDJ(history, indicators);

        return indicators;
    }

    /**
     * 计算简单移动平均
     */
    private BigDecimal calculateMA(List<StockData> data, int period) {
        if (data.size() < period) return null;
        
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = data.size() - period; i < data.size(); i++) {
            sum = sum.add(data.get(i).getClose());
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算RSI
     */
    private BigDecimal calculateRSI(List<StockData> data, int period) {
        if (data.size() < period + 1) return null;
        
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        
        for (int i = data.size() - period; i < data.size(); i++) {
            BigDecimal change = data.get(i).getClose().subtract(data.get(i - 1).getClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }
        
        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100);
        
        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP)
        );
    }

    /**
     * 计算MACD
     */
    private void calculateMACD(List<StockData> data, TechnicalIndicators indicators) {
        if (data.size() < 26) return;
        
        // 简化版MACD计算
        BigDecimal ema12 = calculateEMA(data, 12);
        BigDecimal ema26 = calculateEMA(data, 26);
        
        if (ema12 != null && ema26 != null) {
            BigDecimal dif = ema12.subtract(ema26);
            indicators.setMacd(dif);
            indicators.setMacdSignal(dif.multiply(BigDecimal.valueOf(0.9))); // 简化
            indicators.setMacdHist(dif.multiply(BigDecimal.valueOf(0.1)));
        }
    }

    /**
     * 计算EMA
     */
    private BigDecimal calculateEMA(List<StockData> data, int period) {
        if (data.size() < period) return null;
        
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = data.get(data.size() - period).getClose();
        
        for (int i = data.size() - period + 1; i < data.size(); i++) {
            BigDecimal close = data.get(i).getClose();
            ema = close.multiply(multiplier).add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }
        
        return ema;
    }

    /**
     * 计算KDJ
     */
    private void calculateKDJ(List<StockData> data, TechnicalIndicators indicators) {
        if (data.size() < 9) return;
        
        int period = 9;
        int startIdx = data.size() - period;
        
        BigDecimal highestHigh = data.get(startIdx).getHigh();
        BigDecimal lowestLow = data.get(startIdx).getLow();
        
        for (int i = startIdx + 1; i < data.size(); i++) {
            if (data.get(i).getHigh().compareTo(highestHigh) > 0) {
                highestHigh = data.get(i).getHigh();
            }
            if (data.get(i).getLow().compareTo(lowestLow) < 0) {
                lowestLow = data.get(i).getLow();
            }
        }
        
        BigDecimal close = data.get(data.size() - 1).getClose();
        BigDecimal rsvRange = highestHigh.subtract(lowestLow);
        
        if (rsvRange.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rsv = close.subtract(lowestLow)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(rsvRange, 4, RoundingMode.HALF_UP);
            
            indicators.setK(rsv);
            indicators.setD(rsv.multiply(BigDecimal.valueOf(0.67)));
            indicators.setJ(rsv.multiply(BigDecimal.valueOf(3)).subtract(indicators.getD().multiply(BigDecimal.valueOf(2))));
        }
    }

    /**
     * 创建模拟基本面数据
     */
    private FundamentalData createMockFundamentalData(String symbol, String period) {
        return FundamentalData.builder()
                .tsCode(symbol)
                .endDate(period)
                .roe(new BigDecimal("15.5"))
                .roa(new BigDecimal("8.2"))
                .grossMargin(new BigDecimal("35.0"))
                .netMargin(new BigDecimal("12.5"))
                .currentRatio(new BigDecimal("1.8"))
                .quickRatio(new BigDecimal("1.5"))
                .debtToAsset(new BigDecimal("45.0"))
                .peRatio(new BigDecimal("20.5"))
                .pbRatio(new BigDecimal("2.8"))
                .operatingCashFlow(new BigDecimal("100000"))
                .investingCashFlow(new BigDecimal("-50000"))
                .financingCashFlow(new BigDecimal("-20000"))
                .build();
    }
}
