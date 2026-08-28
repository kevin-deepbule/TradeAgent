package com.tradeagent.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.tradeagent.market.client.AkShareAdapterClient;
import com.tradeagent.market.dto.KlinePayload;
import com.tradeagent.market.dto.StockIdentity;

/** Verifies the market module's stock lookup and cache workflow. */
class StockServiceTest {
    /** Reuse a fetched K-line payload after the first adapter request. */
    @Test
    void reusesCachedKlinePayload() {
        AkShareAdapterClient client = mock(AkShareAdapterClient.class);
        StockIdentity identity = new StockIdentity("000001", "平安银行", "平安银行");
        KlinePayload adapterPayload = new KlinePayload();
        adapterPayload.symbol = "000001";
        adapterPayload.name = "平安银行";
        when(client.resolveStock("平安银行")).thenReturn(identity);
        when(client.fetchKline("000001", "平安银行")).thenReturn(adapterPayload);

        StockService service = new StockService(client, new AdviceService(), new StockCache());
        KlinePayload first = service.getKline("平安银行");
        KlinePayload second = service.getKline("平安银行");

        assertEquals("000001", first.symbol);
        assertEquals("平安银行", second.name);
        assertSame(adapterPayload.rows, second.rows);
        verify(client, times(1)).fetchKline("000001", "平安银行");
    }
}
