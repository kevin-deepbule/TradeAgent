package com.tradeagent.research.dto;

import java.util.List;
import java.util.Map;

/** Typed payloads returned by the internal Python financial-data adapter. */
public final class AdapterPayloads {
    private AdapterPayloads() {
    }

    /** Selectable Shenwan industry metadata. */
    public record Industry(
            String code,
            String name,
            String parentName,
            int level,
            int memberCount,
            Double staticPe,
            Double ttmPe,
            Double pb,
            Double dividendYield) {
    }

    /** Industry-list adapter response. */
    public record IndustryList(String updatedAt, List<Industry> rows) {
    }

    /** Current membership and reference valuation for one stock. */
    public record Constituent(
            String symbol,
            String name,
            String industryCode,
            String industryName,
            String includedAt,
            Double price,
            Double pe,
            Double ttmPe,
            Double pb,
            Double roe,
            Double marketCapYi,
            Double profitGrowth,
            Double revenueGrowth,
            String marketBoard) {
    }

    /** Constituents returned for one Shenwan industry. */
    public record ConstituentList(String industryCode, String updatedAt, List<Constituent> rows) {
    }

    /** One normalized report, quick-report, or earnings-forecast row. */
    public record Disclosure(
            String sourceType,
            String symbol,
            String name,
            String reportPeriod,
            String announcementDate,
            Double revenue,
            Double revenueYoY,
            Double parentNetProfit,
            Double parentNetProfitYoY,
            String forecastMetric,
            Double forecastValue,
            Double forecastChange,
            String changeReason,
            String forecastType) {
    }

    /** Bulk report-period disclosures. */
    public record DisclosureList(String reportPeriod, String updatedAt, List<Disclosure> rows, List<String> warnings) {
    }

    /** One single-quarter formal financial fact. */
    public record FinancialQuarter(
            String reportDate,
            String noticeDate,
            String updateDate,
            Double revenue,
            Double parentNetProfit,
            Double deductParentNetProfit,
            Double operatingCashFlow,
            Double grossProfit,
            String sourceType) {
    }

    /** Historical single-quarter financial facts for one company. */
    public record FinancialHistory(
            String symbol,
            String reportPeriod,
            String asOf,
            List<FinancialQuarter> rows,
            List<String> warnings) {
    }

    /** Current price and market-cap values for one company. */
    public record MarketSnapshot(
            String symbol,
            String name,
            Double price,
            Double dynamicPe,
            Double totalMarketCap,
            Double floatMarketCap) {
    }

    /** Current market values for requested companies. */
    public record MarketSnapshotList(String asOf, List<MarketSnapshot> rows) {
    }

    /** Current analyst coverage and annual EPS forecasts. */
    public record ConsensusForecast(
            String symbol,
            String name,
            int reportCount,
            Map<String, Double> epsForecasts) {
    }

    /** Current analyst EPS forecasts for the market. */
    public record ConsensusForecastList(String asOf, List<ConsensusForecast> rows) {
    }
}
