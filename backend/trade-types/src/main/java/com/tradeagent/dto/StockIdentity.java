package com.tradeagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Resolved stock code and display name metadata. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StockIdentity {
    public String symbol = "";
    public String name = "";
    public String query = "";

    /** Create an empty identity for JSON binding. */
    public StockIdentity() {
    }

    /** Create a resolved identity from explicit values. */
    public StockIdentity(String symbol, String name, String query) {
        this.symbol = symbol;
        this.name = name;
        this.query = query;
    }
}
