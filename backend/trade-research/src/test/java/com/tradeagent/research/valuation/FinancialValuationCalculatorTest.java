package com.tradeagent.research.valuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.research.dto.AdapterPayloads.ConsensusForecast;
import com.tradeagent.research.dto.AdapterPayloads.Constituent;
import com.tradeagent.research.dto.AdapterPayloads.FinancialHistory;
import com.tradeagent.research.dto.AdapterPayloads.FinancialQuarter;
import com.tradeagent.research.dto.AdapterPayloads.Industry;
import com.tradeagent.research.dto.AdapterPayloads.MarketSnapshot;
import com.tradeagent.research.model.ValuationResult;
import com.tradeagent.research.model.ValuationRunRequest;

/** Verifies deterministic annual-profit and valuation behavior with synthetic reports. */
class FinancialValuationCalculatorTest {
    /** Produce positive PE, fair price, and evidence from a complete half-year history. */
    @Test
    void calculatesTraceableHalfYearValuation() {
        ValuationPolicyProvider policies = new ValuationPolicyProvider(new ObjectMapper());
        FinancialValuationCalculator calculator = new FinancialValuationCalculator(policies);
        List<FinancialQuarter> quarters = new ArrayList<>();
        addYear(quarters, 2023, 70, 75, 65, 70);
        addYear(quarters, 2024, 75, 80, 70, 75);
        addYear(quarters, 2025, 80, 90, 70, 80);
        quarters.add(quarter("2026-03-31", 90, 86));
        quarters.add(quarter("2026-06-30", 110, 104));

        Industry industry = new Industry("857321.SI", "轮胎轮毂", "汽车零部件", 3, 10,
                15.0, 14.0, 2.0, 1.0);
        Constituent constituent = new Constituent("600000", "示例轮胎", "857321.SI", "轮胎轮毂",
                "2020-01-01", 10.0, 15.0, 14.0, 2.0, 12.0, 50.0, 20.0, 15.0, "MAIN_BOARD");
        FinancialHistory history = new FinancialHistory("600000", "20260630", "2026-08-28", quarters,
                List.of());
        MarketSnapshot market = new MarketSnapshot("600000", "示例轮胎", 10.0, 14.0,
                5_000.0, 4_000.0);
        ConsensusForecast consensus = new ConsensusForecast("600000", "示例轮胎", 5,
                Map.of("2026", 0.72));
        CompanyResearchInput input = new CompanyResearchInput(
                industry, constituent, List.of(), history, market, consensus);
        ValuationRunRequest request = new ValuationRunRequest(
                List.of("857321.SI"), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 8, 28), "MAIN_BOARD");

        ValuationResult result = calculator.calculate(input, request);

        assertEquals("FORMAL_REPORT", result.sourceType);
        assertEquals(200.0, result.parentNetProfitYtd);
        assertEquals(190.0, result.deductNetProfitYtd);
        assertNotNull(result.forecastBase);
        assertTrue(result.forecastBase > result.parentNetProfitYtd);
        assertNotNull(result.forecastPe);
        assertNotNull(result.fairPrice);
        assertEquals(result.ruleFairPe, result.fairPe);
        assertEquals("RULE", result.fairPeSource);
        assertEquals("v1.0.0", result.policyVersion);
        assertTrue(result.evidence.stream().anyMatch(value -> value.contains("分析截止日")));

        Double ruleFairPrice = result.fairPrice;
        assertTrue(calculator.applyDeepSeekFairPe(result, 22.0));
        assertEquals(22.0, result.deepSeekFairPe);
        assertEquals(22.0, result.fairPe);
        assertEquals("DEEPSEEK", result.fairPeSource);
        assertNotEquals(ruleFairPrice, result.fairPrice);
    }

    /** Add four synthetic quarterly facts for one completed fiscal year. */
    private void addYear(List<FinancialQuarter> quarters, int year, double q1, double q2, double q3, double q4) {
        quarters.add(quarter(year + "-03-31", q1, q1 * 0.95));
        quarters.add(quarter(year + "-06-30", q2, q2 * 0.95));
        quarters.add(quarter(year + "-09-30", q3, q3 * 0.95));
        quarters.add(quarter(year + "-12-31", q4, q4 * 0.95));
    }

    /** Create one single-quarter financial row with profit values in matching units. */
    private FinancialQuarter quarter(String reportDate, double parentProfit, double deductedProfit) {
        return new FinancialQuarter(reportDate, reportDate, reportDate, parentProfit * 5,
                parentProfit, deductedProfit, parentProfit * 0.9, null, "FORMAL_REPORT");
    }
}
