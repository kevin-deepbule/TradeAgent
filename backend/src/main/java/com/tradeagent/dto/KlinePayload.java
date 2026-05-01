package com.tradeagent.dto;

import java.util.ArrayList;
import java.util.List;

/** K-line response payload consumed by the Vue dashboard. */
public class KlinePayload {
    public String symbol = "";
    public String name = "";
    public String updatedAt = "";
    public String source;
    public List<KlineRow> rows = new ArrayList<>();
    public AdviceDto advice;
    public String error;
    public List<String> warnings = new ArrayList<>();

    /** Create an empty payload for JSON binding. */
    public KlinePayload() {
    }

    /** Return a shallow copy with a replacement display name. */
    public KlinePayload withName(String nextName) {
        KlinePayload copy = new KlinePayload();
        copy.symbol = symbol;
        copy.name = nextName == null || nextName.isBlank() ? name : nextName;
        copy.updatedAt = updatedAt;
        copy.source = source;
        copy.rows = rows;
        copy.advice = advice;
        copy.error = error;
        copy.warnings = warnings;
        return copy;
    }
}

