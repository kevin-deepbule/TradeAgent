package com.tradeagent.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** API error shaping compatible with the existing FastAPI frontend contract. */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** Convert response-status exceptions into a JSON payload with detail. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exc) {
        String detail = exc.getReason() == null ? "请求失败" : exc.getReason();
        return ResponseEntity.status(exc.getStatusCode()).body(Map.of("detail", detail));
    }
}

