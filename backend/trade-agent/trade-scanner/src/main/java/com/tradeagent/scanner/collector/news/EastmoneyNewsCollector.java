package com.tradeagent.scanner.collector.news;

import java.util.List;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.client.NewsAdapterClient;
import com.tradeagent.scanner.client.NewsAdapterClient.NewsItem;

/** 定时采集东方财富全球财经快讯并交给 Scanner 原始消息存储。 */
@Component
public class EastmoneyNewsCollector extends AbstractNewsCollector {
    private final NewsAdapterClient client;

    /** 注入内部资讯 Adapter 客户端。 */
    public EastmoneyNewsCollector(NewsAdapterClient client) { this.client = client; }

    /** 返回东方财富来源的稳定标识。 */
    @Override
    public String sourceId() { return "eastmoney-news"; }

    /** 每轮读取 Adapter 当前提供的东方财富消息。 */
    @Override
    protected List<NewsItem> fetchNews() { return client.fetchEastmoney(200); }
}
