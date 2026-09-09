package com.tradeagent.research.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.tradeagent.research.client.DeepSeekResearchClient;
import com.tradeagent.research.client.DeepSeekResearchClient.AiAnalysis;
import com.tradeagent.research.client.ResearchAkShareClient;
import com.tradeagent.research.config.ResearchProperties;
import com.tradeagent.research.dto.AdapterPayloads.ConsensusForecast;
import com.tradeagent.research.dto.AdapterPayloads.ConsensusForecastList;
import com.tradeagent.research.dto.AdapterPayloads.Constituent;
import com.tradeagent.research.dto.AdapterPayloads.ConstituentList;
import com.tradeagent.research.dto.AdapterPayloads.Disclosure;
import com.tradeagent.research.dto.AdapterPayloads.DisclosureList;
import com.tradeagent.research.dto.AdapterPayloads.FinancialHistory;
import com.tradeagent.research.dto.AdapterPayloads.Industry;
import com.tradeagent.research.dto.AdapterPayloads.MarketSnapshot;
import com.tradeagent.research.dto.AdapterPayloads.MarketSnapshotList;
import com.tradeagent.research.model.ResearchRun;
import com.tradeagent.research.model.ValuationResult;
import com.tradeagent.research.model.ValuationRunRequest;
import com.tradeagent.research.repository.ResearchRunStore;
import com.tradeagent.research.valuation.CompanyResearchInput;
import com.tradeagent.research.valuation.FinancialValuationCalculator;

/** Background orchestration for complete multi-industry financial valuation runs. */
@Component
public class ResearchRunExecutor {
    private final ResearchAkShareClient akShareClient;
    private final DeepSeekResearchClient deepSeekClient;
    private final FinancialValuationCalculator calculator;
    private final ResearchRunStore runStore;
    private final ResearchProperties properties;
    private final Executor fetchExecutor;

    /** Create the executor from deterministic and explanatory research components. */
    public ResearchRunExecutor(
            ResearchAkShareClient akShareClient,
            DeepSeekResearchClient deepSeekClient,
            FinancialValuationCalculator calculator,
            ResearchRunStore runStore,
            ResearchProperties properties,
            @Qualifier("researchFetchExecutor") Executor fetchExecutor) {
        this.akShareClient = akShareClient;
        this.deepSeekClient = deepSeekClient;
        this.calculator = calculator;
        this.runStore = runStore;
        this.properties = properties;
        this.fetchExecutor = fetchExecutor;
    }

    /** Execute one persisted run on the bounded research background executor. */
    @Async("researchTaskExecutor")
    public void execute(String runId) {
        ResearchRun run = runStore.find(runId).orElse(null);
        if (run == null) {
            return;
        }
        try {
            ValuationRunRequest request = run.request();
            update(runId, 5, "同步申万行业和成分股");
            Map<String, Industry> industries = industryMap();
            Map<String, Constituent> constituents = constituentMap(request);
            if (constituents.isEmpty()) {
                throw new IllegalStateException("所选行业没有符合第一版范围的 A 股主板公司");
            }

            update(runId, 15, "获取业绩预告、快报和财报摘要");
            DisclosureList disclosurePayload = akShareClient.fetchDisclosures(request.reportPeriod());
            Map<String, List<Disclosure>> disclosures = disclosureMap(disclosurePayload, request.asOfDate());

            update(runId, 22, "获取市场估值和机构盈利预测");
            Map<String, MarketSnapshot> market = marketMap(new ArrayList<>(constituents.keySet()));
            Map<String, ConsensusForecast> consensus = consensusMap();

            List<CompletableFuture<ValuationResult>> pendingResults = new ArrayList<>();
            for (Constituent constituent : constituents.values()) {
                pendingResults.add(CompletableFuture.supplyAsync(
                        () -> calculateCompany(constituent, request, industries, disclosures, market, consensus),
                        fetchExecutor));
            }

            List<ValuationResult> results = new ArrayList<>();
            int completed = 0;
            for (CompletableFuture<ValuationResult> pendingResult : pendingResults) {
                results.add(pendingResult.join());
                completed++;
                int progress = 22 + (int) Math.floor(completed * 58.0 / constituents.size());
                if (completed == constituents.size() || completed % 5 == 0) {
                    update(runId, progress, "分析公司 " + completed + "/" + constituents.size());
                }
            }

            rank(results);
            update(runId, 82, "DeepSeek 全量复核合理 PE");
            applyAiValuations(runId, results);
            rank(results);
            runStore.save(requireRun(runId).completed(results));
        } catch (Exception exc) {
            ResearchRun current = runStore.find(runId).orElse(run);
            runStore.save(current.failed(message(exc)));
        }
    }

    /** Fetch formal facts and calculate one company on a bounded worker thread. */
    private ValuationResult calculateCompany(
            Constituent constituent,
            ValuationRunRequest request,
            Map<String, Industry> industries,
            Map<String, List<Disclosure>> disclosures,
            Map<String, MarketSnapshot> market,
            Map<String, ConsensusForecast> consensus) {
        FinancialHistory history = financialHistory(constituent.symbol(), request);
        Industry industry = industries.getOrDefault(constituent.industryCode(),
                new Industry(constituent.industryCode(), constituent.industryName(), "", 3, 0,
                        null, null, null, null));
        CompanyResearchInput input = new CompanyResearchInput(
                industry,
                constituent,
                disclosures.getOrDefault(constituent.symbol(), List.of()),
                history,
                market.get(constituent.symbol()),
                consensus.get(constituent.symbol()));
        return calculator.calculate(input, request);
    }

    /** Fetch industries into a code-keyed lookup. */
    private Map<String, Industry> industryMap() {
        Map<String, Industry> values = new HashMap<>();
        var payload = akShareClient.fetchIndustries();
        if (payload != null && payload.rows() != null) {
            for (Industry industry : payload.rows()) {
                values.put(industry.code(), industry);
            }
        }
        return values;
    }

    /** Fetch and de-duplicate selected main-board constituents. */
    private Map<String, Constituent> constituentMap(ValuationRunRequest request) {
        Map<String, Constituent> values = new LinkedHashMap<>();
        for (String industryCode : request.industryCodes()) {
            ConstituentList payload = akShareClient.fetchConstituents(industryCode);
            if (payload == null || payload.rows() == null) {
                continue;
            }
            for (Constituent constituent : payload.rows()) {
                if ("MAIN_BOARD".equals(request.marketScopeOrDefault())
                        && !"MAIN_BOARD".equals(constituent.marketBoard())) {
                    continue;
                }
                values.putIfAbsent(constituent.symbol(), constituent);
            }
        }
        return values;
    }

    /** Group disclosures by symbol while enforcing the run's point-in-time cutoff. */
    private Map<String, List<Disclosure>> disclosureMap(DisclosureList payload, LocalDate asOfDate) {
        Map<String, List<Disclosure>> values = new HashMap<>();
        if (payload == null || payload.rows() == null) {
            return values;
        }
        for (Disclosure disclosure : payload.rows()) {
            LocalDate announcedAt = parseDate(disclosure.announcementDate());
            if (announcedAt != null && announcedAt.isAfter(asOfDate)) {
                continue;
            }
            values.computeIfAbsent(disclosure.symbol(), ignored -> new ArrayList<>()).add(disclosure);
        }
        return values;
    }

    /** Fetch current market values and gracefully fall back to constituent snapshots. */
    private Map<String, MarketSnapshot> marketMap(List<String> symbols) {
        Map<String, MarketSnapshot> values = new HashMap<>();
        try {
            MarketSnapshotList payload = akShareClient.fetchMarketSnapshot(symbols);
            if (payload != null && payload.rows() != null) {
                for (MarketSnapshot row : payload.rows()) {
                    values.put(row.symbol(), row);
                }
            }
        } catch (Exception ignored) {
            // Constituent price and market-cap values remain available as a degraded source.
        }
        return values;
    }

    /** Fetch optional analyst forecasts without failing the deterministic valuation run. */
    private Map<String, ConsensusForecast> consensusMap() {
        Map<String, ConsensusForecast> values = new HashMap<>();
        try {
            ConsensusForecastList payload = akShareClient.fetchConsensusForecasts();
            if (payload != null && payload.rows() != null) {
                for (ConsensusForecast row : payload.rows()) {
                    values.put(row.symbol(), row);
                }
            }
        } catch (Exception ignored) {
            // Missing consensus is explicitly reflected as lower confidence per company.
        }
        return values;
    }

    /** Fetch formal financial history and convert upstream failures into visible warnings. */
    private FinancialHistory financialHistory(String symbol, ValuationRunRequest request) {
        try {
            return akShareClient.fetchFinancialHistory(symbol, request.reportPeriod(), request.asOfDate());
        } catch (Exception exc) {
            return new FinancialHistory(
                    symbol,
                    request.reportPeriod().toString(),
                    request.asOfDate().toString(),
                    List.of(),
                    List.of("FORMAL_REPORT: " + message(exc)));
        }
    }

    /** Sort by score, then quality and forecast PE, and assign stable ranks. */
    private void rank(List<ValuationResult> results) {
        results.sort(Comparator
                .comparingDouble((ValuationResult result) -> result.opportunityScore).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (ValuationResult result) -> result.qualityScore).reversed())
                .thenComparing(result -> result.forecastPe == null ? Double.MAX_VALUE : result.forecastPe)
                .thenComparing(result -> result.symbol));
        for (int index = 0; index < results.size(); index++) {
            results.get(index).rank = index + 1;
        }
    }

    /** Review every valuation row in bounded DeepSeek batches with rule-based fallback. */
    private void applyAiValuations(String runId, List<ValuationResult> results) {
        for (ValuationResult result : results) {
            result.aiSummary = defaultSummary(result);
            result.aiStatus = properties.deepseekAvailable() ? "PENDING" : "DISABLED";
        }
        if (!properties.deepseekAvailable()) {
            return;
        }
        int batchSize = properties.aiBatchSizeOrDefault();
        int totalBatches = (results.size() + batchSize - 1) / batchSize;
        for (int offset = 0; offset < results.size(); offset += batchSize) {
            int batchNumber = offset / batchSize + 1;
            int end = Math.min(offset + batchSize, results.size());
            List<ValuationResult> batch = results.subList(offset, end);
            update(runId, 82 + (int) Math.floor((batchNumber - 1) * 16.0 / totalBatches),
                    "DeepSeek 复核 PE " + batchNumber + "/" + totalBatches);
            try {
                Map<String, AiAnalysis> analyses = deepSeekClient.evaluateFairPe(batch);
                for (ValuationResult result : batch) {
                    applyAiAnalysis(result, analyses.get(result.symbol));
                }
            } catch (Exception exc) {
                for (ValuationResult result : batch) {
                    result.aiStatus = "FAILED";
                    result.fairPeSource = "RULE_FALLBACK";
                    result.warnings.add("DEEPSEEK: " + message(exc));
                }
            }
        }
    }

    /** Validate one AI response, adopt its PE when safe, and merge its narrative. */
    private void applyAiAnalysis(ValuationResult result, AiAnalysis analysis) {
        if (analysis == null) {
            result.aiStatus = "NO_OUTPUT";
            result.fairPeSource = "RULE_FALLBACK";
            result.warnings.add("DEEPSEEK: 未返回该股票的合理 PE");
            return;
        }
        boolean adopted = calculator.applyDeepSeekFairPe(result, analysis.reasonablePe());
        result.aiStatus = adopted ? "COMPLETED" : "REJECTED";
        if (!adopted) {
            result.fairPeSource = "RULE_FALLBACK";
            result.warnings.add("DEEPSEEK: 建议 PE 缺失或超出行业允许区间，已回退规则 PE");
        }
        if (analysis.summary() != null && !analysis.summary().isBlank()) {
            result.aiSummary = analysis.summary();
        } else {
            result.aiSummary = defaultSummary(result);
        }
        addDistinct(result.reasons, analysis.reasons());
        addDistinct(result.risks, analysis.risks());
        addDistinct(result.falsificationConditions, analysis.falsificationConditions());
        calculator.refreshOpportunityScore(result);
    }

    /** Build a deterministic summary when DeepSeek is disabled or omits narrative text. */
    private String defaultSummary(ValuationResult result) {
        return result.name + "当前为“" + result.valuationBand + "”，最终合理 PE "
                + (result.fairPe == null ? "--" : String.format("%.2f", result.fairPe)) + "。";
    }

    /** Append model explanations without duplicating deterministic evidence. */
    private void addDistinct(List<String> target, List<String> additions) {
        if (additions == null) {
            return;
        }
        for (String addition : additions) {
            if (addition != null && !addition.isBlank() && !target.contains(addition)) {
                target.add(addition);
            }
        }
    }

    /** Persist one progress update against the latest task snapshot. */
    private void update(String runId, int progress, String message) {
        runStore.save(requireRun(runId).running(Math.min(progress, 99), message));
    }

    /** Resolve a run that must exist during background execution. */
    private ResearchRun requireRun(String runId) {
        return runStore.find(runId).orElseThrow(() -> new IllegalStateException("财报估值任务已不存在"));
    }

    /** Parse optional ISO announcement dates. */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (DateTimeParseException exc) {
            return null;
        }
    }

    /** Extract a concise error message without exposing stack traces or secrets. */
    private String message(Exception exc) {
        return exc.getMessage() == null || exc.getMessage().isBlank()
                ? exc.getClass().getSimpleName()
                : exc.getMessage();
    }
}
