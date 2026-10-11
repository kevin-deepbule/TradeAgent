package com.tradeagent.scanner.client;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 调用内部资讯 Adapter，并只保留 Scanner 入库所需的统一字段。 */
@Component
public class NewsAdapterClient {
    private final RestClient restClient;
    private final String baseUrl;

    /** 注入共享 HTTP 客户端和资讯 Adapter 地址。 */
    public NewsAdapterClient(RestClient restClient,
                             @Value("${tradeagent.scanner.news-base-url:http://localhost:8002}") String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 获取财联社全部电报；输入条数上限，输出不含 raw 的统一资讯列表。 */
    public List<NewsItem> fetchCls(int limit) {
        return fetch("/internal/news/cls?symbol=%E5%85%A8%E9%83%A8&limit=" + limit);
    }

    /** 获取东方财富快讯；输入条数上限，输出不含 raw 的统一资讯列表。 */
    public List<NewsItem> fetchEastmoney(int limit) {
        return fetch("/internal/news/eastmoney?limit=" + limit);
    }

    /** 获取同花顺直播；输入条数上限，输出不含 raw 的统一资讯列表。 */
    public List<NewsItem> fetchThs(int limit) {
        return fetch("/internal/news/ths?limit=" + limit);
    }

    /** 调用一个内部资讯路由并容忍响应中额外的 raw 等字段。 */
    private List<NewsItem> fetch(String pathAndQuery) {
        NewsResponse response = restClient.get().uri(URI.create(baseUrl + pathAndQuery))
            .retrieve().body(NewsResponse.class);
        return response == null || response.rows() == null ? List.of() : response.rows();
    }

    /** 表示 Adapter 的资讯列表响应，未声明的来源元数据不会进入数据库。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewsResponse(List<NewsItem> rows) {}

    /** 表示一条待采集资讯，故意不声明 raw 字段以丢弃上游原始对象。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewsItem(String externalId, String title, String content, String url,
                           Instant publishedAt) {}
}
