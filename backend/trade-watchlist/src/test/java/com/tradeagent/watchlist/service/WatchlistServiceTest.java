package com.tradeagent.watchlist.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tradeagent.market.dto.StockIdentity;
import com.tradeagent.market.service.StockService;
import com.tradeagent.watchlist.dto.WatchlistItem;
import com.tradeagent.watchlist.repository.SettingsRepository;
import com.tradeagent.watchlist.repository.WatchlistRepository;

/** Verifies that watchlist workflows coordinate persistence with the market module. */
class WatchlistServiceTest {
    /** Warm every persisted stock into the market module during initialization. */
    @Test
    void initializesRepositoriesAndActiveStocks() {
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        WatchlistRepository watchlistRepository = mock(WatchlistRepository.class);
        StockService stockService = mock(StockService.class);
        StockIdentity defaultStock = new StockIdentity("000001", "平安银行", "");
        WatchlistItem watchlistItem = new WatchlistItem("600519", "贵州茅台", "2026-08-28T10:00:00+08:00");
        when(settingsRepository.getDefaultStock()).thenReturn(defaultStock);
        when(watchlistRepository.list()).thenReturn(List.of(watchlistItem));

        WatchlistService service = new WatchlistService(settingsRepository, watchlistRepository, stockService);
        service.initialize();

        verify(settingsRepository).init();
        verify(watchlistRepository).init();
        verify(stockService).activate("000001", "平安银行");
        verify(stockService).activate("600519", "贵州茅台");
    }

    /** Persist and activate a resolved default stock through one module workflow. */
    @Test
    void updatesDefaultStock() {
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        WatchlistRepository watchlistRepository = mock(WatchlistRepository.class);
        StockService stockService = mock(StockService.class);
        StockIdentity resolved = new StockIdentity("000001", "平安银行", "平安银行");
        StockIdentity saved = new StockIdentity("000001", "平安银行", "");
        when(stockService.resolveStock("平安银行")).thenReturn(resolved);
        when(settingsRepository.setDefaultStock("000001", "平安银行")).thenReturn(saved);

        WatchlistService service = new WatchlistService(settingsRepository, watchlistRepository, stockService);
        StockIdentity result = service.setDefaultStock("平安银行");

        assertSame(saved, result);
        verify(stockService).activate("000001", "平安银行");
    }
}
