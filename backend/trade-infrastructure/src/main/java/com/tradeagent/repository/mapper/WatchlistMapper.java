package com.tradeagent.repository.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tradeagent.dto.WatchlistItem;

/** MyBatis mapper for the persisted stock watchlist. */
@Mapper
public interface WatchlistMapper {
    /** Create the watchlist table when local PostgreSQL has not initialized it yet. */
    void createWatchlistTable();

    /** Keep legacy watchlist tables aligned with timestamptz defaults. */
    void ensureTimestampDefaults();

    /** Insert the configured default stock only when it is absent. */
    void insertWatchlistIfAbsent(@Param("symbol") String symbol, @Param("name") String name);

    /** Return all persisted watchlist rows in display order. */
    List<WatchlistItem> listWatchlist();

    /** Insert or update a watchlist row and return the saved value. */
    WatchlistItem upsertWatchlist(@Param("symbol") String symbol, @Param("name") String name);

    /** Delete a normalized stock symbol from the watchlist. */
    void deleteWatchlist(@Param("symbol") String symbol);
}
