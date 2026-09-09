package com.tradeagent.research.valuation;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.tradeagent.research.dto.AdapterPayloads.ConsensusForecast;
import com.tradeagent.research.dto.AdapterPayloads.Disclosure;
import com.tradeagent.research.dto.AdapterPayloads.FinancialQuarter;
import com.tradeagent.research.model.ValuationResult;
import com.tradeagent.research.model.ValuationRunRequest;

/** Deterministic profit-quality, annual forecast, PE, and ranking calculations. */
@Component
public class FinancialValuationCalculator {
    private final ValuationPolicyProvider policyProvider;

    /** Create the calculator with immutable industry valuation rules. */
    public FinancialValuationCalculator(ValuationPolicyProvider policyProvider) {
        this.policyProvider = policyProvider;
    }

    /** Calculate a traceable valuation result without relying on model-generated numbers. */
    public ValuationResult calculate(CompanyResearchInput input, ValuationRunRequest request) {
        ValuationResult result = new ValuationResult();
        result.symbol = input.constituent().symbol();
        result.name = input.constituent().name();
        result.industryCode = input.constituent().industryCode();
        result.industryName = input.constituent().industryName();
        result.reportPeriod = request.reportPeriod().toString();

        List<FinancialQuarter> quarters = sortedUniqueQuarters(input.financialHistory());
        FinancialQuarter currentQuarter = quarterAt(quarters, request.reportPeriod());
        Disclosure disclosure = bestDisclosure(input.disclosures());
        ActualFacts facts = actualFacts(quarters, currentQuarter, disclosure, request.reportPeriod());
        result.dataStatus = facts.status();
        result.sourceType = facts.sourceType();
        result.announcementDate = facts.announcementDate();
        result.revenueYtd = rounded(facts.revenueYtd());
        result.parentNetProfitYtd = rounded(facts.parentNetProfitYtd());
        result.deductNetProfitYtd = rounded(facts.deductNetProfitYtd());
        result.singleQuarterNetProfit = rounded(facts.singleQuarterNetProfit());

        FinancialQuarter previousQuarter = quarterAt(quarters, request.reportPeriod().minusYears(1));
        result.lowBase = isLowBase(previousQuarter, quarters);
        result.singleQuarterYoY = rounded(growth(facts.singleQuarterNetProfit(),
                previousQuarter == null ? null : previousQuarter.parentNetProfit(), result.lowBase));
        result.nonRecurringContributionRate = rounded(nonRecurringRate(
                facts.parentNetProfitYtd(), facts.deductNetProfitYtd()));
        result.cyclicalVolatility = isCyclicallyVolatile(quarters);

        Forecast forecast = forecastAnnualProfit(quarters, facts.parentNetProfitYtd(), request.reportPeriod());
        result.forecastLow = rounded(forecast.low());
        result.forecastBase = rounded(forecast.base());
        result.forecastHigh = rounded(forecast.high());
        Double previousFullYear = sumMetric(quarters, request.reportPeriod().getYear() - 1, 12,
                FinancialQuarter::parentNetProfit);
        result.forecastGrowth = rounded(growth(forecast.base(), previousFullYear, previousFullYear == null
                || previousFullYear <= 0));

        Double cashFlowYtd = sumMetric(quarters, request.reportPeriod().getYear(),
                request.reportPeriod().getMonthValue(), FinancialQuarter::operatingCashFlow);
        result.qualityScore = roundedPrimitive(qualityScore(
                facts.parentNetProfitYtd(), facts.deductNetProfitYtd(), cashFlowYtd,
                result.nonRecurringContributionRate, result.lowBase, result.cyclicalVolatility));

        result.confidenceScore = roundedPrimitive(confidenceScore(
                request.reportPeriod(), facts.sourceType(), quarters, input.consensusForecast()));
        result.confidenceLevel = confidenceLevel(result.confidenceScore);

        Double price = input.marketSnapshot() == null ? input.constituent().price() : input.marketSnapshot().price();
        Double marketCap = input.marketSnapshot() == null ? yiToYuan(input.constituent().marketCapYi())
                : input.marketSnapshot().totalMarketCap();
        result.currentPrice = rounded(price);
        result.totalMarketCap = rounded(marketCap);
        result.forecastPe = positiveRatio(marketCap, forecast.base());

        ValuationPolicy policy = policyProvider.select(input.constituent().industryName(), input.industry().parentName());
        result.policyVersion = policy.version();
        result.fairPeMin = rounded(policy.minPe());
        result.fairPeMax = rounded(policy.maxPe());
        result.ruleFairPe = rounded(adjustedFairPe(policy, result.forecastGrowth, result.qualityScore,
                result.lowBase, result.cyclicalVolatility));
        result.fairPe = result.ruleFairPe;
        result.fairPeSource = "RULE";
        refreshValuation(result, policy);

        Double shareCount = positiveRatio(marketCap, price);
        result.consensusNetProfit = rounded(consensusNetProfit(
                input.consensusForecast(), request.reportPeriod().getYear(), shareCount));
        result.expectationGap = rounded(ratioDifference(forecast.base(), result.consensusNetProfit));
        buildReasonsAndRisks(result, input, facts, forecast, request);
        result.opportunityScore = roundedPrimitive(opportunityScore(result));
        result.aiStatus = "PENDING";
        return result;
    }

    /** Apply a bounded DeepSeek PE and recompute every dependent valuation field. */
    public boolean applyDeepSeekFairPe(ValuationResult result, Double proposedFairPe) {
        result.deepSeekFairPe = rounded(proposedFairPe);
        if (result.deepSeekFairPe == null || result.deepSeekFairPe <= 0
                || result.fairPeMin == null || result.fairPeMax == null
                || result.deepSeekFairPe < result.fairPeMin || result.deepSeekFairPe > result.fairPeMax) {
            return false;
        }
        result.fairPe = result.deepSeekFairPe;
        result.fairPeSource = "DEEPSEEK";
        ValuationPolicy policy = policyProvider.select(result.industryName, null);
        refreshValuation(result, policy);
        refreshOpportunityScore(result);
        result.evidence.add("最终合理 PE：DeepSeek 全量复核值 " + result.fairPe
                + "，规则基线 " + result.ruleFairPe);
        return true;
    }

    /** Recalculate the opportunity score after AI valuation evidence or risks change. */
    public void refreshOpportunityScore(ValuationResult result) {
        result.opportunityScore = roundedPrimitive(opportunityScore(result));
    }

    /** Recalculate fair price, upside, and valuation band from the currently adopted PE. */
    private void refreshValuation(ValuationResult result, ValuationPolicy policy) {
        Double shareCount = positiveRatio(result.totalMarketCap, result.currentPrice);
        result.fairPrice = positiveRatio(product(result.forecastBase, result.fairPe), shareCount);
        result.valuationUpside = ratioDifference(result.fairPrice, result.currentPrice);
        result.valuationBand = valuationBand(result.currentPrice, result.fairPrice, policy);
    }

    /** Remove amended duplicates and order formal facts from oldest to newest. */
    private List<FinancialQuarter> sortedUniqueQuarters(
            com.tradeagent.research.dto.AdapterPayloads.FinancialHistory history) {
        if (history == null || history.rows() == null) {
            return List.of();
        }
        Map<LocalDate, FinancialQuarter> selected = new HashMap<>();
        for (FinancialQuarter row : history.rows()) {
            LocalDate reportDate = parseDate(row.reportDate());
            if (reportDate == null) {
                continue;
            }
            selected.merge(reportDate, row, this::newerQuarter);
        }
        return selected.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    /** Select the later update when an upstream statement contains amendments. */
    private FinancialQuarter newerQuarter(FinancialQuarter left, FinancialQuarter right) {
        String leftDate = left.updateDate() == null ? "" : left.updateDate();
        String rightDate = right.updateDate() == null ? "" : right.updateDate();
        return rightDate.compareTo(leftDate) > 0 ? right : left;
    }

    /** Locate one exact single-quarter fact. */
    private FinancialQuarter quarterAt(List<FinancialQuarter> quarters, LocalDate reportDate) {
        return quarters.stream()
                .filter(row -> reportDate.equals(parseDate(row.reportDate())))
                .findFirst()
                .orElse(null);
    }

    /** Select the most authoritative and latest period disclosure. */
    private Disclosure bestDisclosure(List<Disclosure> disclosures) {
        if (disclosures == null || disclosures.isEmpty()) {
            return null;
        }
        Disclosure reported = disclosures.stream()
                .filter(row -> !"FORECAST".equals(row.sourceType()))
                .max(Comparator.comparingInt(this::sourcePriority)
                        .thenComparing(row -> row.announcementDate() == null ? "" : row.announcementDate()))
                .orElse(null);
        if (reported != null) {
            return reported;
        }
        List<Disclosure> parentProfitForecasts = disclosures.stream()
                .filter(this::isParentProfitForecast)
                .toList();
        Disclosure template = parentProfitForecasts.stream()
                .max(Comparator.comparing(row -> row.announcementDate() == null ? "" : row.announcementDate()))
                .orElse(null);
        if (template == null) {
            return null;
        }
        Double midpoint = parentProfitForecasts.stream()
                .map(Disclosure::forecastValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
        return new Disclosure(
                template.sourceType(), template.symbol(), template.name(), template.reportPeriod(),
                template.announcementDate(), null, null, null, null, template.forecastMetric(),
                midpoint, template.forecastChange(), template.changeReason(), template.forecastType());
    }

    /** Rank formal summaries above quick reports and forecasts. */
    private int sourcePriority(Disclosure disclosure) {
        return switch (disclosure.sourceType()) {
            case "REPORT" -> 3;
            case "QUICK_REPORT" -> 2;
            case "FORECAST" -> 1;
            default -> 0;
        };
    }

    /** Resolve current cumulative facts from formal quarters or the best bulk disclosure. */
    private ActualFacts actualFacts(
            List<FinancialQuarter> quarters,
            FinancialQuarter currentQuarter,
            Disclosure disclosure,
            LocalDate reportPeriod) {
        if (currentQuarter != null && currentQuarter.parentNetProfit() != null) {
            return new ActualFacts(
                    "DISCLOSED",
                    "FORMAL_REPORT",
                    currentQuarter.noticeDate(),
                    sumMetric(quarters, reportPeriod.getYear(), reportPeriod.getMonthValue(), FinancialQuarter::revenue),
                    sumMetric(quarters, reportPeriod.getYear(), reportPeriod.getMonthValue(),
                            FinancialQuarter::parentNetProfit),
                    sumMetric(quarters, reportPeriod.getYear(), reportPeriod.getMonthValue(),
                            FinancialQuarter::deductParentNetProfit),
                    currentQuarter.parentNetProfit());
        }
        if (disclosure == null) {
            return new ActualFacts("MISSING", "NONE", null, null, null, null, null);
        }
        Double forecastValue = isParentProfitForecast(disclosure) ? disclosure.forecastValue() : null;
        String status = "FORECAST".equals(disclosure.sourceType()) ? "FORECAST_ONLY" : "DISCLOSED";
        return new ActualFacts(
                status,
                disclosure.sourceType(),
                disclosure.announcementDate(),
                disclosure.revenue(),
                disclosure.parentNetProfit() == null ? forecastValue : disclosure.parentNetProfit(),
                null,
                reportPeriod.getMonthValue() == 3 ? forecastValue : null);
    }

    /** Check that a forecast metric represents parent net profit rather than deducted profit. */
    private boolean isParentProfitForecast(Disclosure disclosure) {
        String metric = disclosure.forecastMetric();
        return metric != null && metric.contains("净利润") && !metric.contains("扣非") && !metric.contains("扣除");
    }

    /** Build low, base, and high full-year profit scenarios. */
    private Forecast forecastAnnualProfit(List<FinancialQuarter> quarters, Double currentYtd, LocalDate reportPeriod) {
        if (currentYtd == null) {
            return new Forecast(null, null, null);
        }
        int month = reportPeriod.getMonthValue();
        if (month == 12) {
            return new Forecast(currentYtd, currentYtd, currentYtd);
        }

        List<Double> seasonalForecasts = new ArrayList<>();
        for (int year = reportPeriod.getYear() - 1; year >= reportPeriod.getYear() - 3; year--) {
            Double historicalYtd = sumMetric(quarters, year, month, FinancialQuarter::parentNetProfit);
            Double historicalFull = sumMetric(quarters, year, 12, FinancialQuarter::parentNetProfit);
            if (historicalYtd != null && historicalFull != null && historicalYtd != 0
                    && Math.signum(historicalYtd) == Math.signum(historicalFull)) {
                double share = historicalYtd / historicalFull;
                if (share >= 0.10 && share <= 1.50) {
                    seasonalForecasts.add(currentYtd / share);
                }
            }
        }

        Double seasonal = median(seasonalForecasts);
        Double previousYtd = sumMetric(quarters, reportPeriod.getYear() - 1, month,
                FinancialQuarter::parentNetProfit);
        Double previousFull = sumMetric(quarters, reportPeriod.getYear() - 1, 12,
                FinancialQuarter::parentNetProfit);
        Double trend = null;
        if (previousYtd != null && previousFull != null && previousYtd > 0) {
            double trendFactor = clamp(currentYtd / previousYtd, 0.50, 1.80);
            trend = currentYtd + (previousFull - previousYtd) * trendFactor;
        }

        Double base = averageNonNull(seasonal, trend);
        if (base == null) {
            base = currentYtd * 12.0 / month;
        }
        double spread = switch (month) {
            case 3 -> 0.25;
            case 6 -> 0.15;
            case 9 -> 0.10;
            default -> 0.20;
        };
        return new Forecast(base * (1 - spread), base, base * (1 + spread));
    }

    /** Sum available single-quarter values through a requested month. */
    private Double sumMetric(
            List<FinancialQuarter> quarters,
            int year,
            int throughMonth,
            Function<FinancialQuarter, Double> extractor) {
        double total = 0;
        int count = 0;
        for (FinancialQuarter quarter : quarters) {
            LocalDate reportDate = parseDate(quarter.reportDate());
            Double value = extractor.apply(quarter);
            if (reportDate != null && reportDate.getYear() == year
                    && reportDate.getMonthValue() <= throughMonth && value != null) {
                total += value;
                count++;
            }
        }
        return count == 0 ? null : total;
    }

    /** Flag a non-comparable prior quarter using sign and company-relative size. */
    private boolean isLowBase(FinancialQuarter previousQuarter, List<FinancialQuarter> quarters) {
        if (previousQuarter == null || previousQuarter.parentNetProfit() == null
                || previousQuarter.parentNetProfit() <= 0) {
            return true;
        }
        List<Double> absoluteProfits = quarters.stream()
                .map(FinancialQuarter::parentNetProfit)
                .filter(Objects::nonNull)
                .map(Math::abs)
                .sorted()
                .toList();
        Double typical = median(absoluteProfits.size() > 8
                ? absoluteProfits.subList(absoluteProfits.size() - 8, absoluteProfits.size())
                : absoluteProfits);
        return typical != null && previousQuarter.parentNetProfit() < typical * 0.20;
    }

    /** Calculate standard growth only when the comparison base is meaningful. */
    private Double growth(Double current, Double previous, boolean lowBase) {
        if (current == null || previous == null || previous == 0 || lowBase) {
            return null;
        }
        return (current - previous) / Math.abs(previous);
    }

    /** Estimate the share of parent profit not supported by deducted profit. */
    private Double nonRecurringRate(Double parentProfit, Double deductedProfit) {
        if (parentProfit == null || deductedProfit == null || parentProfit == 0) {
            return null;
        }
        return Math.abs(parentProfit - deductedProfit) / Math.abs(parentProfit);
    }

    /** Detect repeated sign changes or unusually large normalized quarterly swings. */
    private boolean isCyclicallyVolatile(List<FinancialQuarter> quarters) {
        List<Double> availableProfits = quarters.stream()
                .map(FinancialQuarter::parentNetProfit)
                .filter(Objects::nonNull)
                .toList();
        List<Double> profits = availableProfits.size() > 12
                ? availableProfits.subList(availableProfits.size() - 12, availableProfits.size())
                : availableProfits;
        if (profits.size() < 6) {
            return false;
        }
        int signChanges = 0;
        for (int index = 1; index < profits.size(); index++) {
            if (Math.signum(profits.get(index - 1)) != Math.signum(profits.get(index))) {
                signChanges++;
            }
        }
        double meanAbsolute = profits.stream().mapToDouble(Math::abs).average().orElse(0);
        double mean = profits.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = profits.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
        double normalizedVolatility = meanAbsolute == 0 ? 0 : Math.sqrt(variance) / meanAbsolute;
        return signChanges >= 4 || normalizedVolatility > 1.10;
    }

    /** Score deducted-profit support, cash conversion, and earnings stability. */
    private double qualityScore(
            Double parentProfit,
            Double deductedProfit,
            Double operatingCashFlow,
            Double nonRecurringRate,
            boolean lowBase,
            boolean cyclical) {
        double score = 60;
        if (parentProfit != null && parentProfit > 0 && deductedProfit != null) {
            double deductedRatio = deductedProfit / parentProfit;
            score += deductedRatio >= 0.90 ? 20 : deductedRatio >= 0.70 ? 10 : deductedRatio < 0.50 ? -15 : 0;
        }
        if (parentProfit != null && parentProfit > 0 && operatingCashFlow != null) {
            double cashConversion = operatingCashFlow / parentProfit;
            score += cashConversion >= 0.80 ? 10 : cashConversion < 0 ? -15 : 0;
        }
        if (nonRecurringRate != null) {
            score += nonRecurringRate > 0.50 ? -25 : nonRecurringRate > 0.20 ? -10 : 5;
        }
        score -= lowBase ? 10 : 0;
        score -= cyclical ? 10 : 0;
        return clamp(score, 0, 100);
    }

    /** Score forecast reliability from report stage, period coverage, and history. */
    private double confidenceScore(
            LocalDate reportPeriod,
            String sourceType,
            List<FinancialQuarter> quarters,
            ConsensusForecast consensus) {
        double score = switch (reportPeriod.getMonthValue()) {
            case 3 -> 35;
            case 6 -> 55;
            case 9 -> 75;
            case 12 -> 90;
            default -> 30;
        };
        score += switch (sourceType) {
            case "FORMAL_REPORT" -> 10;
            case "REPORT", "QUICK_REPORT" -> 5;
            case "FORECAST" -> -5;
            default -> -15;
        };
        score += Math.min(quarters.size(), 12) / 3.0;
        score += consensus != null && consensus.reportCount() >= 3 ? 5 : 0;
        return clamp(score, 0, 100);
    }

    /** Convert the numeric confidence score into a compact display level. */
    private String confidenceLevel(double score) {
        if (score >= 80) {
            return "高";
        }
        if (score >= 55) {
            return "中";
        }
        return "低";
    }

    /** Adjust the manually configured PE anchor with bounded quality and growth factors. */
    private double adjustedFairPe(
            ValuationPolicy policy,
            Double forecastGrowth,
            double qualityScore,
            boolean lowBase,
            boolean cyclical) {
        double growthFactor = forecastGrowth == null ? 1.0
                : forecastGrowth >= 0.30 ? 1.20
                        : forecastGrowth >= 0.15 ? 1.10 : forecastGrowth < -0.30 ? 0.70
                                : forecastGrowth < 0 ? 0.85 : 1.0;
        double qualityFactor = qualityScore >= 80 ? 1.10 : qualityScore < 50 ? 0.85 : 1.0;
        double stabilityFactor = (lowBase ? 0.90 : 1.0) * (cyclical ? 0.85 : 1.0);
        return clamp(policy.basePe() * growthFactor * qualityFactor * stabilityFactor,
                policy.minPe(), policy.maxPe());
    }

    /** Classify current price against the configured five fair-price bands. */
    private String valuationBand(Double price, Double fairPrice, ValuationPolicy policy) {
        Double ratio = positiveRatio(price, fairPrice);
        if (ratio == null) {
            return "不可用";
        }
        if (ratio <= policy.deepValueRatio()) {
            return "显著低估";
        }
        if (ratio <= policy.valueRatio()) {
            return "低估";
        }
        if (ratio <= policy.fairRatio()) {
            return "合理";
        }
        if (ratio <= policy.expensiveRatio()) {
            return "偏贵";
        }
        return "高估";
    }

    /** Convert analyst EPS into comparable annual parent net profit. */
    private Double consensusNetProfit(ConsensusForecast consensus, int year, Double shareCount) {
        if (consensus == null || consensus.reportCount() < 3 || consensus.epsForecasts() == null
                || shareCount == null) {
            return null;
        }
        return product(consensus.epsForecasts().get(String.valueOf(year)), shareCount);
    }

    /** Build deterministic explanation evidence, risks, and falsification conditions. */
    private void buildReasonsAndRisks(
            ValuationResult result,
            CompanyResearchInput input,
            ActualFacts facts,
            Forecast forecast,
            ValuationRunRequest request) {
        result.evidence.add("分析截止日：" + request.asOfDate());
        result.evidence.add("报告期：" + request.reportPeriod() + "，数据层级：" + facts.sourceType());
        result.evidence.add("估值规则：" + result.policyVersion);
        if (result.forecastGrowth != null) {
            result.reasons.add("全年基准利润预测同比 " + percent(result.forecastGrowth));
        }
        if (result.valuationUpside != null) {
            result.reasons.add("合理价格相对当前价格空间 " + percent(result.valuationUpside));
        }
        if (result.deductNetProfitYtd != null && result.parentNetProfitYtd != null) {
            result.reasons.add("扣非利润已纳入盈利质量评分");
        }
        if (result.lowBase) {
            result.risks.add("上年同期为亏损或低基数，普通同比不可直接比较");
        }
        if (result.cyclicalVolatility) {
            result.risks.add("最近季度利润波动较大，全年外推稳定性偏低");
        }
        if (result.nonRecurringContributionRate != null && result.nonRecurringContributionRate > 0.20) {
            result.risks.add("归母与扣非差异较大，可能包含显著非经常性贡献");
        }
        if (result.deductNetProfitYtd == null) {
            result.risks.add("当前来源缺少扣非净利润，盈利质量判断降级");
        }
        if (result.consensusNetProfit == null) {
            result.risks.add("机构一致预期覆盖不足，预期差分项按中性处理");
        }
        if ("MISSING".equals(result.dataStatus)) {
            result.risks.add("报告期核心利润数据缺失，仅保留公司覆盖状态");
        }
        if (input.financialHistory() != null && input.financialHistory().warnings() != null) {
            result.warnings.addAll(input.financialHistory().warnings());
        }
        result.falsificationConditions.add(forecast.low() == null
                ? "下一份有效披露仍无法取得归母净利润"
                : "下一期累计归母净利润对应的全年推演低于当前悲观情景");
        result.falsificationConditions.add("后续扣非利润增速显著落后于归母利润增速");
        result.falsificationConditions.add("股价上涨后预测 PE 超过合理 PE 上限");
    }

    /** Combine industry-relative concepts into a bounded first-version opportunity score. */
    private double opportunityScore(ValuationResult result) {
        double surprise = result.expectationGap == null ? 50 : clamp(50 + result.expectationGap * 100, 0, 100);
        double valuation = result.valuationUpside == null ? 30 : clamp(50 + result.valuationUpside * 100, 0, 100);
        double growth = result.forecastGrowth == null ? 35 : clamp(50 + result.forecastGrowth * 100, 0, 100);
        double trend = result.singleQuarterYoY == null ? growth : clamp(50 + result.singleQuarterYoY * 100, 0, 100);
        double penalty = Math.min(result.risks.size() * 4.0, 24.0);
        double score = surprise * 0.30 + valuation * 0.25 + result.qualityScore * 0.20
                + trend * 0.15 + result.confidenceScore * 0.10 - penalty;
        if (result.forecastBase == null || result.forecastBase <= 0 || result.forecastPe == null) {
            score = Math.min(score, 35);
        }
        return clamp(score, 0, 100);
    }

    /** Parse an ISO date without propagating malformed upstream values. */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(value.length(), 10)));
        } catch (DateTimeParseException exc) {
            return null;
        }
    }

    /** Return the median of finite values or null for an empty collection. */
    private Double median(List<Double> values) {
        List<Double> sorted = values.stream().filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    /** Average any available non-null values. */
    private Double averageNonNull(Double... values) {
        double total = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                total += value;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    /** Calculate a ratio only when the denominator and result are positive and finite. */
    private Double positiveRatio(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || numerator <= 0 || denominator <= 0) {
            return null;
        }
        double value = numerator / denominator;
        return Double.isFinite(value) ? rounded(value) : null;
    }

    /** Calculate relative difference from a non-zero baseline. */
    private Double ratioDifference(Double value, Double baseline) {
        if (value == null || baseline == null || baseline == 0) {
            return null;
        }
        double result = (value - baseline) / Math.abs(baseline);
        return Double.isFinite(result) ? result : null;
    }

    /** Multiply two nullable finite values. */
    private Double product(Double left, Double right) {
        if (left == null || right == null) {
            return null;
        }
        double value = left * right;
        return Double.isFinite(value) ? value : null;
    }

    /** Convert an upstream hundred-million-yuan market cap into yuan. */
    private Double yiToYuan(Double value) {
        return value == null ? null : value * 100_000_000.0;
    }

    /** Bound one numeric value to a stable range. */
    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Round nullable output to four decimal places for stable JSON and persistence. */
    private Double rounded(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    /** Round primitive output to four decimal places. */
    private double roundedPrimitive(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    /** Format one ratio as a concise Chinese percentage string. */
    private String percent(Double value) {
        return value == null ? "--" : String.format("%.1f%%", value * 100);
    }

    /** Resolved report-period actual or forecast facts. */
    private record ActualFacts(
            String status,
            String sourceType,
            String announcementDate,
            Double revenueYtd,
            Double parentNetProfitYtd,
            Double deductNetProfitYtd,
            Double singleQuarterNetProfit) {
    }

    /** Low, base, and high full-year parent-profit scenarios. */
    private record Forecast(Double low, Double base, Double high) {
    }
}
