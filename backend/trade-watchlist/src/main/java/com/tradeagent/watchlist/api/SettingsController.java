package com.tradeagent.watchlist.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.market.dto.StockIdentity;
import com.tradeagent.watchlist.service.WatchlistService;

/** Dashboard settings HTTP routes. */
@RestController
public class SettingsController {
    private final WatchlistService watchlistService;

    /** Create the controller with the watchlist workflow. */
    public SettingsController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /** Return the stock configured as the dashboard default. */
    @GetMapping("/api/default-stock")
    public StockIdentity getDefaultStock() {
        return watchlistService.getDefaultStock();
    }

    /** Resolve and persist the requested stock as the dashboard default. */
    @PutMapping("/api/default-stock")
    public StockIdentity setDefaultStock(@RequestBody Map<String, String> item) {
        String query = firstNonBlank(item.get("query"), item.get("symbol"));
        return watchlistService.setDefaultStock(query);
    }

    /** Return the first non-blank request value. */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
