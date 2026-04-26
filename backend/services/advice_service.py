"""Generate lightweight trade advice from existing K-line rows."""

from datetime import datetime
from typing import Any


def _number(value: Any) -> float | None:
    """Convert a raw row value to float when numeric."""
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _recent_change(rows: list[dict[str, Any]], days: int) -> float | None:
    """Calculate percentage close-price change over a recent window."""
    if len(rows) <= days:
        return None
    current = _number(rows[-1].get("close"))
    previous = _number(rows[-days - 1].get("close"))
    if current is None or previous in (None, 0):
        return None
    return (current - previous) / previous * 100


def _volume_ratio(rows: list[dict[str, Any]], days: int = 20) -> float | None:
    """Compare the latest volume with the average of the previous window."""
    if len(rows) < days + 1:
        return None
    current = _number(rows[-1].get("volume"))
    previous_values = [
        _number(row.get("volume"))
        for row in rows[-days - 1 : -1]
        if _number(row.get("volume")) is not None
    ]
    if current is None or not previous_values:
        return None
    average = sum(previous_values) / len(previous_values)
    if average == 0:
        return None
    return current / average


def build_trade_advice(rows: list[dict[str, Any]]) -> dict[str, Any] | None:
    """Build buy/sell/hold advice from moving averages, price, and volume."""
    if len(rows) < 60:
        return None

    latest = rows[-1]
    close = _number(latest.get("close"))
    ma5 = _number(latest.get("ma5"))
    ma20 = _number(latest.get("ma20"))
    ma60 = _number(latest.get("ma60"))
    if None in (close, ma5, ma20, ma60):
        return None

    score = 50
    reasons: list[str] = []
    risks: list[str] = [
        "建议仅基于历史K线、均线和量能生成，未纳入基本面、消息面和大盘环境。",
    ]

    if close > ma20:
        score += 10
        reasons.append("收盘价位于MA20上方，短中期价格结构偏强。")
    else:
        score -= 10
        reasons.append("收盘价位于MA20下方，短中期价格结构偏弱。")

    if ma5 > ma20:
        score += 8
        reasons.append("MA5高于MA20，短线趋势强于中期趋势。")
    else:
        score -= 8
        reasons.append("MA5低于MA20，短线动能仍需修复。")

    if ma20 > ma60:
        score += 10
        reasons.append("MA20高于MA60，中期趋势保持向上。")
    else:
        score -= 10
        reasons.append("MA20低于MA60，中期趋势压力仍在。")

    change_5d = _recent_change(rows, 5)
    if change_5d is not None:
        if change_5d >= 3:
            score += 8
            reasons.append(f"近5日涨幅约{change_5d:.2f}%，短线动能较强。")
        elif change_5d <= -3:
            score -= 8
            reasons.append(f"近5日跌幅约{abs(change_5d):.2f}%，短线承压明显。")
        else:
            reasons.append(f"近5日涨跌幅约{change_5d:.2f}%，短线波动相对温和。")

    volume_ratio = _volume_ratio(rows)
    if volume_ratio is not None:
        if volume_ratio >= 1.5 and close > ma20:
            score += 6
            reasons.append(f"当日量能约为20日均量的{volume_ratio:.2f}倍，放量配合价格走强。")
        elif volume_ratio >= 1.5 and close <= ma20:
            score -= 6
            risks.append(f"当日量能约为20日均量的{volume_ratio:.2f}倍，但价格仍偏弱，需警惕放量下跌。")
        elif volume_ratio <= 0.7:
            risks.append(f"当日量能约为20日均量的{volume_ratio:.2f}倍，成交活跃度偏低。")

    score = max(0, min(100, round(score)))
    if score >= 65:
        action = "buy"
        action_text = "买入"
        risks.append("若跌破MA20或放量回落，应重新评估买入条件。")
    elif score <= 35:
        action = "sell"
        action_text = "卖出"
        risks.append("若快速收复MA20并放量转强，应重新评估卖出条件。")
    else:
        action = "hold"
        action_text = "持有"
        risks.append("当前多空信号不够一致，适合等待更明确的突破或跌破信号。")

    return {
        "action": action,
        "actionText": action_text,
        "score": score,
        "reasons": reasons,
        "risks": risks,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
    }
