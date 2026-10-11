package com.tradeagent.scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.tradeagent.scanner.client.NewsAdapterClient;
import com.tradeagent.scanner.client.NewsAdapterClient.NewsItem;
import com.tradeagent.scanner.collector.news.ClsNewsCollector;
import com.tradeagent.scanner.collector.news.EastmoneyNewsCollector;
import com.tradeagent.scanner.collector.news.ThsNewsCollector;

/** 验证三个资讯 Collector 的来源标识、字段映射和仅采集边界。 */
class NewsCollectorTest {
    /** 构造一条不包含 raw 的 Adapter 统一资讯。 */
    private NewsItem item() {
        return new NewsItem("external-1", "标题", "正文", "https://example.com/news",
            Instant.parse("2026-10-11T07:00:00Z"));
    }

    /** 财联社 Collector 保留统一字段并声明仅采集。 */
    @Test
    void collectsClsNews() {
        var client = mock(NewsAdapterClient.class);
        when(client.fetchCls(100)).thenReturn(List.of(item()));
        var collector = new ClsNewsCollector(client);
        var raw = collector.collect().get(0);
        assertTrue(collector.ingestionOnly());
        assertEquals("cls-news", raw.sourceId());
        assertEquals("external-1", raw.externalId());
        assertEquals(List.of(), raw.symbols());
    }

    /** 东方财富 Collector 使用独立来源标识并请求完整当前批次。 */
    @Test
    void collectsEastmoneyNews() {
        var client = mock(NewsAdapterClient.class);
        when(client.fetchEastmoney(200)).thenReturn(List.of(item()));
        var collector = new EastmoneyNewsCollector(client);
        assertEquals("eastmoney-news", collector.collect().get(0).sourceId());
    }

    /** 同花顺 Collector 使用独立来源标识并保留原文链接。 */
    @Test
    void collectsThsNews() {
        var client = mock(NewsAdapterClient.class);
        when(client.fetchThs(100)).thenReturn(List.of(item()));
        var collector = new ThsNewsCollector(client);
        assertEquals("https://example.com/news", collector.collect().get(0).url());
        assertEquals("ths-news", collector.sourceId());
    }
}
