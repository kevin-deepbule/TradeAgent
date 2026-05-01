package com.tradeagent.util;

/** Stock symbol and query text helpers shared by services. */
public final class StockText {
    /** Prevent construction of this utility class. */
    private StockText() {
    }

    /** Return the last six digits of a stock symbol with zero padding. */
    public static String normalizeSymbol(String symbol) {
        String digits = symbol == null ? "" : symbol.replaceAll("\\D", "");
        String padded = "000000" + digits;
        return padded.substring(Math.max(0, padded.length() - 6));
    }

    /** Check whether a user query is a numeric stock code. */
    public static boolean isSymbolQuery(String query) {
        if (query == null) {
            return false;
        }
        String text = query.trim();
        return !text.isEmpty() && text.length() <= 6 && text.chars().allMatch(Character::isDigit);
    }
}

