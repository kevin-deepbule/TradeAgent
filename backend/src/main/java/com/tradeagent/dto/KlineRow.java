package com.tradeagent.dto;

/** One daily K-line row with moving averages. */
public class KlineRow {
    public String date;
    public Double open;
    public Double close;
    public Double high;
    public Double low;
    public Double volume;
    public Double ma5;
    public Double ma20;
    public Double ma60;

    /** Create an empty row for JSON binding. */
    public KlineRow() {
    }
}

