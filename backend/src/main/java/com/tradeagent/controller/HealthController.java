package com.tradeagent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.config.AppProperties;

/** Health-check route for service readiness and refresh settings. */
@RestController
public class HealthController {
    private final AppProperties properties;

    /** Create the controller with refresh configuration. */
    public HealthController(AppProperties properties) {
        this.properties = properties;
    }

    /** Return a lightweight status payload for frontend and smoke checks. */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "refreshSeconds", properties.refreshSecondsOrDefault());
    }
}

