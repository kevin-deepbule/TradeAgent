package com.tradeagent.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tradeagent.dto.StockIdentity;

/** Public settings API contract for dashboard defaults. */
public interface SettingsApi {
    /** Return the stock configured as the dashboard default. */
    @GetMapping("/api/default-stock")
    StockIdentity getDefaultStock();

    /** Resolve and persist the requested stock as the dashboard default. */
    @PutMapping("/api/default-stock")
    StockIdentity setDefaultStock(@RequestBody Map<String, String> item);
}
