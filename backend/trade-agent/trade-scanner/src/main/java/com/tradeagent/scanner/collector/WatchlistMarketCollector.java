package com.tradeagent.scanner.collector;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.tradeagent.market.service.StockService;
import com.tradeagent.watchlist.service.WatchlistService;
import com.tradeagent.scanner.domain.ScannerContracts.RawMessage;

/** 复用已有行情识别自选股收盘异动，不额外调用 AkShare。 */
@Component
public class WatchlistMarketCollector implements SourceCollector {
    private final StockService market;
    private final WatchlistService watchlist;
    /** 注入权威自选股服务和行情缓存访问服务。 */
    public WatchlistMarketCollector(StockService market, WatchlistService watchlist) {
        this.market = market; this.watchlist = watchlist;
    }
    /** 标识规则生成的行情证据来源，不将其视为官方公告来源。 */
    public String sourceId() { return "watchlist-market"; }
    /** 仅在收盘后生成日涨跌幅绝对值至少为 4% 的行情消息。 */
    public List<RawMessage> collect() {
        var result = new ArrayList<RawMessage>();
        var zone = ZoneId.of("Asia/Shanghai");
        var now = java.time.ZonedDateTime.now(zone);
        for (var stock : watchlist.listWatchlist()) {
            var payload = market.cached(stock.symbol);
            if (payload == null || payload.error != null || payload.rows == null) continue;
            for (int i = 1; i < payload.rows.size(); i++) {
                var row = payload.rows.get(i);
                var previous = payload.rows.get(i - 1);
                if (row.close == null || previous.close == null || previous.close <= 0 || row.date == null) continue;
                var date = LocalDate.parse(row.date);
                if (!now.isAfter(date.atTime(15, 0).atZone(zone)) || date.isBefore(now.toLocalDate().minusDays(7))) continue;
                double change = (row.close / previous.close - 1) * 100;
                if (Double.isFinite(change) && Math.abs(change) >= 4) {
                    result.add(new RawMessage(sourceId(), stock.symbol + ":" + row.date,
                        stock.symbol + " " + row.date + " 行情异动", "日收盘涨跌幅=" + change + "%，收盘=" + row.close,
                        null, date.atTime(15, 0).atZone(zone).toInstant(), List.of(stock.symbol)));
                }
            }
        }
        return result;
    }
}
