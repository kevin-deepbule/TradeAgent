package com.tradeagent.scanner.collector.news;

import java.util.List;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.client.NewsAdapterClient;
import com.tradeagent.scanner.client.NewsAdapterClient.NewsItem;

/** 定时采集同花顺全球财经直播并交给 Scanner 原始消息存储。 */
@Component
public class ThsNewsCollector extends AbstractNewsCollector {
    private final NewsAdapterClient client;

    /** 注入内部资讯 Adapter 客户端。 */
    public ThsNewsCollector(NewsAdapterClient client) { this.client = client; }

    /** 返回同花顺来源的稳定标识。 */
    @Override
    public String sourceId() { return "ths-news"; }

    /** 每轮读取 Adapter 当前提供的同花顺消息。 */
    @Override
    protected List<NewsItem> fetchNews() { return client.fetchThs(100); }
}
