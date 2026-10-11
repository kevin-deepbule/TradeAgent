package com.tradeagent.scanner.collector.news;

import java.util.List;
import com.tradeagent.scanner.client.NewsAdapterClient.NewsItem;
import com.tradeagent.scanner.collector.SourceCollector;
import com.tradeagent.scanner.domain.ScannerContracts.RawMessage;

/** 复用资讯到 RawMessage 的转换，并明确资讯当前只采集、不生成事件。 */
abstract class AbstractNewsCollector implements SourceCollector {
    /** 返回 Adapter 中该来源当前可用的最新资讯。 */
    protected abstract List<NewsItem> fetchNews();

    /** 资讯来源当前只保存原文，不进入分类、评分或 Outbox。 */
    @Override
    public boolean ingestionOnly() { return true; }

    /** 将统一资讯字段转换为 Scanner RawMessage，丢弃 Adapter 的 raw 字段。 */
    @Override
    public List<RawMessage> collect() {
        return fetchNews().stream().map(item -> new RawMessage(
            sourceId(), item.externalId(), item.title(), item.content(), item.url(),
            item.publishedAt(), List.of())).toList();
    }
}
