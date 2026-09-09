package com.tradeagent.research.model;

import java.util.ArrayList;
import java.util.List;

/** Traceable financial forecast, valuation, score, and explanation for one stock. */
public class ValuationResult {
    public int rank;
    public String symbol;
    public String name;
    public String industryCode;
    public String industryName;
    public String dataStatus;
    public String sourceType;
    public String reportPeriod;
    public String announcementDate;
    public Double revenueYtd;
    public Double parentNetProfitYtd;
    public Double deductNetProfitYtd;
    public Double singleQuarterNetProfit;
    public Double singleQuarterYoY;
    public Double nonRecurringContributionRate;
    public boolean lowBase;
    public boolean cyclicalVolatility;
    public Double forecastLow;
    public Double forecastBase;
    public Double forecastHigh;
    public Double forecastGrowth;
    public String confidenceLevel;
    public double confidenceScore;
    public Double currentPrice;
    public Double totalMarketCap;
    public Double forecastPe;
    public Double ruleFairPe;
    public Double fairPeMin;
    public Double fairPeMax;
    public Double deepSeekFairPe;
    public Double fairPe;
    public String fairPeSource;
    public Double fairPrice;
    public Double valuationUpside;
    public String valuationBand;
    public Double consensusNetProfit;
    public Double expectationGap;
    public double qualityScore;
    public double opportunityScore;
    public String policyVersion;
    public String aiStatus;
    public String aiSummary;
    public List<String> reasons = new ArrayList<>();
    public List<String> risks = new ArrayList<>();
    public List<String> falsificationConditions = new ArrayList<>();
    public List<String> evidence = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
}
