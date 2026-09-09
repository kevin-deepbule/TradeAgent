package com.tradeagent.research.client;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.tradeagent.research.config.ResearchProperties;
import com.tradeagent.research.dto.AdapterPayloads.ConsensusForecastList;
import com.tradeagent.research.dto.AdapterPayloads.ConstituentList;
import com.tradeagent.research.dto.AdapterPayloads.DisclosureList;
import com.tradeagent.research.dto.AdapterPayloads.FinancialHistory;
import com.tradeagent.research.dto.AdapterPayloads.IndustryList;
import com.tradeagent.research.dto.AdapterPayloads.MarketSnapshotList;

/** HTTP client for research endpoints exposed by the internal AkShare adapter. */
@Component
public class ResearchAkShareClient {
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final String baseUrl;

    /** Create the client from the shared HTTP transport and research settings. */
    public ResearchAkShareClient(RestClient restClient, ResearchProperties properties) {
        this.restClient = restClient;
        this.baseUrl = properties.normalizedAkshareBaseUrl();
    }

    /** Fetch the selectable Shenwan level-three industry list. */
    public IndustryList fetchIndustries() {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "research", "industries")
                .queryParam("level", 3)
                .build().encode().toUri();
        return get(uri, IndustryList.class, "申万行业获取失败");
    }

    /** Fetch current constituents for one Shenwan industry code. */
    public ConstituentList fetchConstituents(String industryCode) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "research", "industries", industryCode, "constituents")
                .build().encode().toUri();
        return get(uri, ConstituentList.class, "行业成分股获取失败");
    }

    /** Fetch bulk report-period disclosures. */
    public DisclosureList fetchDisclosures(LocalDate reportPeriod) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "research", "disclosures")
                .queryParam("report_period", COMPACT_DATE.format(reportPeriod))
                .build().encode().toUri();
        return get(uri, DisclosureList.class, "财报披露数据获取失败");
    }

    /** Fetch point-filtered formal quarterly facts for one company. */
    public FinancialHistory fetchFinancialHistory(String symbol, LocalDate reportPeriod, LocalDate asOfDate) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "research", "stocks", symbol, "financials")
                .queryParam("report_period", COMPACT_DATE.format(reportPeriod))
                .queryParam("as_of", asOfDate)
                .build().encode().toUri();
        return get(uri, FinancialHistory.class, "公司财务数据获取失败");
    }

    /** Fetch current price and market-cap values for selected companies. */
    public MarketSnapshotList fetchMarketSnapshot(List<String> symbols) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "research", "market-snapshot")
                .build().encode().toUri();
        return restClient.post()
                .uri(uri)
                .body(Map.of("symbols", symbols))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "市场估值数据获取失败");
                })
                .body(MarketSnapshotList.class);
    }

    /** Fetch current analyst EPS forecasts for expectation-gap calculations. */
    public ConsensusForecastList fetchConsensusForecasts() {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "research", "profit-forecasts")
                .build().encode().toUri();
        return get(uri, ConsensusForecastList.class, "盈利预测数据获取失败");
    }

    /** Execute one typed adapter GET request with consistent error shaping. */
    private <T> T get(URI uri, Class<T> responseType, String errorMessage) {
        return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), errorMessage);
                })
                .body(responseType);
    }
}
