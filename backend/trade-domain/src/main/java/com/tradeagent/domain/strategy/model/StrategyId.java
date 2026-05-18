package com.tradeagent.domain.strategy.model;

/** Stable identifiers for built-in trading strategies owned by the strategy domain. */
public final class StrategyId {
    public static final String MA20_CROSS = "ma20-cross";
    public static final String LONG_PROTECT = "long-protect";
    public static final String VOLUME_DROP = "volume-drop";
    public static final String MA20_BREAKOUT = "ma20-breakout";
    public static final String BOLL_BREAK_BUY = "boll-break-buy";

    private StrategyId() {
    }
}
