package com.tradingagents.data.client;

import com.tradingagents.data.model.FundamentalData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EastMoneyDataCenterClientTest {

    @Autowired
    private EastMoneyDataCenterClient client;

    @Test
    void shouldFetchRealFundamentalsForMaotai() {
        FundamentalData data = client.getFundamentalData("600519", "20250930").block();
        assertNotNull(data);
        assertEquals("600519", data.getTsCode());
        assertNotNull(data.getRoe(), "ROE should come from East Money datacenter");
        assertNotNull(data.getPeRatio(), "PE should come from quote API");
        System.out.printf("600519 ROE=%s PE=%s RevenueGrowth=%s%n",
                data.getRoe(), data.getPeRatio(), data.getRevenueGrowth());
    }
}
