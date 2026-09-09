package com.tradeagent.research.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.research.config.ResearchProperties;
import com.tradeagent.research.model.ValuationResult;

/** DeepSeek JSON-output client for bounded fair-PE review and explanations. */
@Component
public class DeepSeekResearchClient {
    private static final String SYSTEM_PROMPT = """
            你是A股财报PE估值复核员。你需要逐一复核输入表格中的每家公司，并给出建议合理PE。
            只能依据输入的公开财务指标、规则PE基线和行业PE上下限判断，不得补造事实或修改其他财务数字。
            reasonablePe必须是数字，并且必须位于fairPeMin与fairPeMax之间；证据不足时使用ruleFairPe。
            不要给出买入、卖出或短期涨跌预测，不得遗漏任何输入股票。
            必须只输出合法 JSON，结构为：
            {"analyses":[{"symbol":"股票代码","reasonablePe":20.0,"summary":"两句内总结","reasons":["原因"],
            "risks":["风险"],"falsificationConditions":["可量化或可观察的证伪条件"]}]}
            每只股票最多3条原因、3条风险和3条证伪条件。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ResearchProperties properties;

    /** Create the client from shared HTTP transport, JSON support, and secret-backed settings. */
    public DeepSeekResearchClient(RestClient restClient, ObjectMapper objectMapper, ResearchProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Review one valuation batch and return fair-PE analyses keyed by stock symbol. */
    public Map<String, AiAnalysis> evaluateFairPe(List<ValuationResult> results) {
        if (!properties.deepseekAvailable() || results.isEmpty()) {
            return Map.of();
        }
        URI uri = UriComponentsBuilder.fromUriString(properties.normalizedDeepSeekBaseUrl())
                .pathSegment("chat", "completions")
                .build().encode().toUri();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.deepseekModelOrDefault());
        request.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt(results))));
        request.put("response_format", Map.of("type", "json_object"));
        request.put("max_tokens", 6000);
        request.put("stream", false);

        JsonNode response = restClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.deepseekApiKey().trim())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                    throw new ResponseStatusException(httpResponse.getStatusCode(), "DeepSeek PE 复核失败");
                })
                .body(JsonNode.class);
        String content = response == null ? null : response.path("choices").path(0).path("message").path("content")
                .asText(null);
        return parseAnalyses(content);
    }

    /** Build a compact public-financial-data prompt without sending secrets or raw internal state. */
    private String userPrompt(List<ValuationResult> results) {
        List<Map<String, Object>> companies = new ArrayList<>();
        for (ValuationResult result : results) {
            Map<String, Object> company = new LinkedHashMap<>();
            company.put("symbol", result.symbol);
            company.put("name", result.name);
            company.put("industry", result.industryName);
            company.put("sourceType", result.sourceType);
            company.put("reportPeriod", result.reportPeriod);
            company.put("parentNetProfitYtd", result.parentNetProfitYtd);
            company.put("deductNetProfitYtd", result.deductNetProfitYtd);
            company.put("singleQuarterYoY", result.singleQuarterYoY);
            company.put("nonRecurringContributionRate", result.nonRecurringContributionRate);
            company.put("lowBase", result.lowBase);
            company.put("cyclicalVolatility", result.cyclicalVolatility);
            company.put("forecastLow", result.forecastLow);
            company.put("forecastBase", result.forecastBase);
            company.put("forecastHigh", result.forecastHigh);
            company.put("forecastGrowth", result.forecastGrowth);
            company.put("forecastPe", result.forecastPe);
            company.put("ruleFairPe", result.ruleFairPe);
            company.put("fairPeMin", result.fairPeMin);
            company.put("fairPeMax", result.fairPeMax);
            company.put("currentPrice", result.currentPrice);
            company.put("totalMarketCap", result.totalMarketCap);
            company.put("valuationBand", result.valuationBand);
            company.put("expectationGap", result.expectationGap);
            company.put("qualityScore", result.qualityScore);
            company.put("confidenceLevel", result.confidenceLevel);
            company.put("policyVersion", result.policyVersion);
            company.put("existingRisks", result.risks);
            companies.add(company);
        }
        try {
            return "请逐一复核以下PE估值表并输出每家公司建议合理PE的JSON："
                    + objectMapper.writeValueAsString(companies);
        } catch (JsonProcessingException exc) {
            throw new IllegalStateException("无法构造 DeepSeek 财报解释输入", exc);
        }
    }

    /** Parse and validate JSON analyses without trusting model-generated stock identifiers blindly. */
    private Map<String, AiAnalysis> parseAnalyses(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            Map<String, AiAnalysis> analyses = new HashMap<>();
            for (JsonNode item : root.path("analyses")) {
                String symbol = normalizeSymbol(item.path("symbol").asText(""));
                if (symbol.isBlank()) {
                    continue;
                }
                analyses.put(symbol, new AiAnalysis(
                        nullableDouble(item.path("reasonablePe")),
                        item.path("summary").asText(""),
                        stringList(item.path("reasons")),
                        stringList(item.path("risks")),
                        stringList(item.path("falsificationConditions"))));
            }
            return analyses;
        } catch (JsonProcessingException exc) {
            throw new IllegalStateException("DeepSeek 返回了无法解析的 JSON", exc);
        }
    }

    /** Parse a finite optional number from a JSON numeric or textual field. */
    private Double nullableDouble(JsonNode node) {
        try {
            double value = node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText(""));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exc) {
            return null;
        }
    }

    /** Convert one JSON string array into a bounded Java list. */
    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
            if (values.size() == 3) {
                break;
            }
        }
        return values;
    }

    /** Normalize a model-returned symbol to six digits. */
    private String normalizeSymbol(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return "";
        }
        return String.format("%06d", Long.parseLong(digits.substring(Math.max(0, digits.length() - 6))));
    }

    /** Structured fair-PE recommendation and bounded narrative returned by DeepSeek. */
    public record AiAnalysis(
            Double reasonablePe,
            String summary,
            List<String> reasons,
            List<String> risks,
            List<String> falsificationConditions) {
    }
}
