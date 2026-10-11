package com.tradeagent.scanner.collector.news;

import java.util.List;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.client.NewsAdapterClient;
import com.tradeagent.scanner.client.NewsAdapterClient.NewsItem;

/** 定时采集财联社电报并交给 Scanner 原始消息存储。 */
@Component
public class ClsNewsCollector extends AbstractNewsCollector {
    private final NewsAdapterClient client;

    /** 注入内部资讯 Adapter 客户端。 */
    public ClsNewsCollector(NewsAdapterClient client) { this.client = client; }

    /** 返回财联社来源的稳定标识。 */
    @Override
    public String sourceId() { return "cls-news"; }

    /** 每轮读取 Adapter 当前提供的财联社消息。 */
    @Override
    protected List<NewsItem> fetchNews() { return client.fetchCls(100); }
}
