package com.tradeagent.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;

/** Public health API contract for service readiness checks. */
public interface HealthApi {
    /** Return a lightweight status payload for frontend and smoke checks. */
    @GetMapping("/api/health")
    Map<String, Object> health();
}
