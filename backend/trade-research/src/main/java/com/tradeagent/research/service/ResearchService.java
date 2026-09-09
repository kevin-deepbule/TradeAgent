package com.tradeagent.research.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tradeagent.research.client.ResearchAkShareClient;
import com.tradeagent.research.dto.AdapterPayloads.Industry;
import com.tradeagent.research.dto.AdapterPayloads.IndustryList;
import com.tradeagent.research.model.ResearchRun;
import com.tradeagent.research.model.ValuationRunRequest;
import com.tradeagent.research.repository.ResearchRunStore;

/** Public workflow for listing industries and starting or reading valuation tasks. */
@Service
public class ResearchService {
    private final ResearchAkShareClient akShareClient;
    private final ResearchRunStore runStore;
    private final ResearchRunExecutor runExecutor;

    /** Create the public workflow with adapter, persistence, and background execution. */
    public ResearchService(
            ResearchAkShareClient akShareClient,
            ResearchRunStore runStore,
            ResearchRunExecutor runExecutor) {
        this.akShareClient = akShareClient;
        this.runStore = runStore;
        this.runExecutor = runExecutor;
    }

    /** Return selectable Shenwan industries in stable parent and name order. */
    public List<Industry> listIndustries() {
        IndustryList payload = akShareClient.fetchIndustries();
        if (payload == null || payload.rows() == null) {
            return List.of();
        }
        return payload.rows().stream()
                .sorted(Comparator.comparing((Industry row) -> value(row.parentName()))
                        .thenComparing(row -> value(row.name())))
                .toList();
    }

    /** Validate, persist, and asynchronously start one financial valuation task. */
    public ResearchRun start(ValuationRunRequest request) {
        ValuationRunRequest validated = validate(request);
        ResearchRun run = ResearchRun.queued(UUID.randomUUID().toString(), validated);
        runStore.save(run);
        runExecutor.execute(run.id());
        return run;
    }

    /** Return a persisted task or a stable 404 response. */
    public ResearchRun get(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException exc) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "财报估值任务不存在");
        }
        return runStore.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "财报估值任务不存在"));
    }

    /** Validate report dates, market scope, and bounded industry selections. */
    private ValuationRunRequest validate(ValuationRunRequest request) {
        if (request == null || request.industryCodes() == null || request.industryCodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少选择一个申万三级行业");
        }
        if (request.industryCodes().size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次最多选择 20 个申万三级行业");
        }
        LocalDate reportPeriod = request.reportPeriod();
        if (reportPeriod == null || !List.of(3, 6, 9, 12).contains(reportPeriod.getMonthValue())
                || reportPeriod.getDayOfMonth() != reportPeriod.lengthOfMonth()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报告期必须是一季报、中报、三季报或年报期末");
        }
        LocalDate asOfDate = request.asOfDate() == null ? LocalDate.now() : request.asOfDate();
        if (asOfDate.isBefore(reportPeriod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分析截止日不能早于报告期");
        }
        String marketScope = request.marketScopeOrDefault();
        if (!"MAIN_BOARD".equals(marketScope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第一版只支持 A 股主板");
        }
        List<String> codes = request.industryCodes().stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> normalizeIndustryCode(code).toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申万行业代码不能为空");
        }
        return new ValuationRunRequest(codes, reportPeriod, asOfDate, marketScope);
    }

    /** Normalize a numeric Shenwan code to the adapter's .SI notation. */
    private String normalizeIndustryCode(String code) {
        String trimmed = code.trim();
        return trimmed.matches("\\d+") ? trimmed + ".SI" : trimmed;
    }

    /** Convert null sort values into stable empty strings. */
    private String value(String text) {
        return text == null ? "" : text;
    }
}
