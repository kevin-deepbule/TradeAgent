package com.tradeagent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.dto.StockIdentity;
import com.tradeagent.repository.SettingsRepository;
import com.tradeagent.service.StockService;

/** Dashboard settings HTTP routes. */
@RestController
public class SettingsController {
    private final SettingsRepository settingsRepository;
    private final StockService stockService;

    /** Create the controller with settings persistence and stock lookup services. */
    public SettingsController(SettingsRepository settingsRepository, StockService stockService) {
        this.settingsRepository = settingsRepository;
        this.stockService = stockService;
    }

    /** Return the stock configured as the dashboard default. */
    @GetMapping("/api/default-stock")
    public StockIdentity getDefaultStock() {
        return settingsRepository.getDefaultStock();
    }

    /** Resolve and persist the requested stock as the dashboard default. */
    @PutMapping("/api/default-stock")
    public StockIdentity setDefaultStock(@RequestBody Map<String, String> item) {
        String query = firstNonBlank(item.get("query"), item.get("symbol"));
        StockIdentity resolved = stockService.resolveStock(query);
        StockIdentity saved = settingsRepository.setDefaultStock(resolved.symbol, resolved.name);
        stockService.cache().addSymbol(saved.symbol, saved.name);
        return saved;
    }

    /** Return the first non-blank request value. */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}

