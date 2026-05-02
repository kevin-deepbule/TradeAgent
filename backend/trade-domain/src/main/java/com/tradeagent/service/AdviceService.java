package com.tradeagent.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tradeagent.dto.AdviceDto;
import com.tradeagent.dto.KlineRow;
import com.tradeagent.util.TimeUtil;

/** Generate lightweight trade advice from existing K-line rows. */
@Service
public class AdviceService {
    /** Build buy/sell/hold advice from moving averages, price, and volume. */
    public AdviceDto buildTradeAdvice(List<KlineRow> rows) {
        if (rows == null || rows.size() < 60) {
            return null;
        }

        KlineRow latest = rows.get(rows.size() - 1);
        Double close = latest.close;
        Double ma5 = latest.ma5;
        Double ma20 = latest.ma20;
        Double ma60 = latest.ma60;
        if (close == null || ma5 == null || ma20 == null || ma60 == null) {
            return null;
        }

        int score = 50;
        List<String> reasons = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        risks.add("建议仅基于历史K线、均线和量能生成，未纳入基本面、消息面和大盘环境。");

        if (close > ma20) {
            score += 10;
            reasons.add("收盘价位于MA20上方，短中期价格结构偏强。");
        } else {
            score -= 10;
            reasons.add("收盘价位于MA20下方，短中期价格结构偏弱。");
        }

        if (ma5 > ma20) {
            score += 8;
            reasons.add("MA5高于MA20，短线趋势强于中期趋势。");
        } else {
            score -= 8;
            reasons.add("MA5低于MA20，短线动能仍需修复。");
        }

        if (ma20 > ma60) {
            score += 10;
            reasons.add("MA20高于MA60，中期趋势保持向上。");
        } else {
            score -= 10;
            reasons.add("MA20低于MA60，中期趋势压力仍在。");
        }

        Double change5d = recentChange(rows, 5);
        if (change5d != null) {
            if (change5d >= 3) {
                score += 8;
                reasons.add(String.format("近5日涨幅约%.2f%%，短线动能较强。", change5d));
            } else if (change5d <= -3) {
                score -= 8;
                reasons.add(String.format("近5日跌幅约%.2f%%，短线承压明显。", Math.abs(change5d)));
            } else {
                reasons.add(String.format("近5日涨跌幅约%.2f%%，短线波动相对温和。", change5d));
            }
        }

        Double volumeRatio = volumeRatio(rows, 20);
        if (volumeRatio != null) {
            if (volumeRatio >= 1.5 && close > ma20) {
                score += 6;
                reasons.add(String.format("当日量能约为20日均量的%.2f倍，放量配合价格走强。", volumeRatio));
            } else if (volumeRatio >= 1.5) {
                score -= 6;
                risks.add(String.format("当日量能约为20日均量的%.2f倍，但价格仍偏弱，需警惕放量下跌。", volumeRatio));
            } else if (volumeRatio <= 0.7) {
                risks.add(String.format("当日量能约为20日均量的%.2f倍，成交活跃度偏低。", volumeRatio));
            }
        }

        score = Math.max(0, Math.min(100, Math.round(score)));
        String action;
        String actionText;
        if (score >= 65) {
            action = "buy";
            actionText = "买入";
            risks.add("若跌破MA20或放量回落，应重新评估买入条件。");
        } else if (score <= 35) {
            action = "sell";
            actionText = "卖出";
            risks.add("若快速收复MA20并放量转强，应重新评估卖出条件。");
        } else {
            action = "hold";
            actionText = "持有";
            risks.add("当前多空信号不够一致，适合等待更明确的突破或跌破信号。");
        }

        return new AdviceDto(action, actionText, score, reasons, risks, TimeUtil.nowIsoSeconds());
    }

    /** Calculate percentage close-price change over a recent window. */
    private Double recentChange(List<KlineRow> rows, int days) {
        if (rows.size() <= days) {
            return null;
        }
        Double current = rows.get(rows.size() - 1).close;
        Double previous = rows.get(rows.size() - days - 1).close;
        if (current == null || previous == null || previous == 0) {
            return null;
        }
        return (current - previous) / previous * 100;
    }

    /** Compare the latest volume with the average of the previous window. */
    private Double volumeRatio(List<KlineRow> rows, int days) {
        if (rows.size() < days + 1) {
            return null;
        }
        Double current = rows.get(rows.size() - 1).volume;
        if (current == null) {
            return null;
        }
        List<Double> previousValues = rows.subList(rows.size() - days - 1, rows.size() - 1).stream()
                .map(row -> row.volume)
                .filter(value -> value != null)
                .toList();
        if (previousValues.isEmpty()) {
            return null;
        }
        double average = previousValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return average == 0 ? null : current / average;
    }
}

