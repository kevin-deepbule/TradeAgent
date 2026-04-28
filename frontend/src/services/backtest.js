// Pure strategy backtest rules and calculations for A-share K-line rows.

import { numericValue } from "../utils/formatters";

export const STRATEGY_MA20 = "ma20-cross";
export const STRATEGY_VOLUME_DROP = "volume-drop";
export const STRATEGY_MA20_BREAKOUT = "ma20-breakout";
export const STRATEGY_BOLL_BREAK = "boll-break-buy";

const BOLL_WINDOW = 20;
const BOLL_MULTIPLIER = 2;

export const strategyOptions = [
  { id: STRATEGY_MA20, name: "20日线：站上买入，跌破卖出" },
  { id: STRATEGY_VOLUME_DROP, name: "放量急跌买入，放量卖出" },
  {
    id: STRATEGY_MA20_BREAKOUT,
    name: "MA20趋势跟随：有效突破",
  },
  { id: STRATEGY_BOLL_BREAK, name: "BOLL下轨买入，上轨卖出" },
];

export function signalLabel(type) {
  // Convert a strategy signal type into short Chinese display text.
  return type === "buy" ? "买入" : "卖出";
}

export function performanceClass(value) {
  // Match performance colors to the market convention already used in the app.
  if (value === null || value === undefined || Number.isNaN(value)) return "";
  return Number(value) >= 0 ? "rise" : "fall";
}

function priceLimitPercent(symbol = "", name = "") {
  // Estimate the A-share price limit band from symbol/name for open execution.
  const upperName = name.toUpperCase();
  if (upperName.includes("ST")) return 5;
  if (/^(300|301|688|689)/.test(symbol)) return 20;
  if (/^(8|4|920|430)/.test(symbol)) return 30;
  return 10;
}

function openChangePercent(row, previous) {
  // Compare today's open with the previous close for limit-up/down checks.
  const open = numericValue(row?.open);
  const previousClose = numericValue(previous?.close);
  if (open === null || previousClose === null || previousClose <= 0) return null;
  return ((open - previousClose) / previousClose) * 100;
}

function isLimitUpOpen(row, previous, limitPercent) {
  // Treat a near-limit-up open as unavailable for next-open buy execution.
  const changePercent = openChangePercent(row, previous);
  return changePercent !== null && changePercent >= limitPercent - 0.2;
}

function isLimitDownOpen(row, previous, limitPercent) {
  // Treat a near-limit-down open as unavailable for next-open sell execution.
  const changePercent = openChangePercent(row, previous);
  return changePercent !== null && changePercent <= -limitPercent + 0.2;
}

function averagePreviousVolume(data, index, days = 20) {
  // Calculate the average volume over the completed days before the current row.
  if (index < days) return null;
  const volumes = data
    .slice(index - days, index)
    .map((item) => numericValue(item.volume));
  if (volumes.length < days || volumes.some((value) => value === null)) return null;
  return volumes.reduce((sum, value) => sum + value, 0) / volumes.length;
}

function dailyChangePercent(row, previous) {
  // Calculate the close-to-close daily move used by volume-price strategies.
  const close = numericValue(row?.close);
  const previousClose = numericValue(previous?.close);
  if (close === null || previousClose === null || previousClose <= 0) return null;
  return ((close - previousClose) / previousClose) * 100;
}

function bollBandAt(data, index) {
  // Calculate the BOLL(20, 2) upper and lower bands for a completed row.
  if (index < BOLL_WINDOW - 1) return null;
  const closes = data
    .slice(index - BOLL_WINDOW + 1, index + 1)
    .map((item) => numericValue(item.close));
  if (closes.length < BOLL_WINDOW || closes.some((value) => value === null)) {
    return null;
  }

  const middle = closes.reduce((sum, value) => sum + value, 0) / BOLL_WINDOW;
  const variance =
    closes.reduce((sum, value) => sum + (value - middle) ** 2, 0) / BOLL_WINDOW;
  const offset = Math.sqrt(variance) * BOLL_MULTIPLIER;
  return { upper: middle + offset, lower: middle - offset };
}

function bollBreakAction(data, index, holding) {
  // Buy on a lower-band close and sell when intraday high breaks the upper band.
  if (index < 1) return null;
  const row = data[index];
  const previous = data[index - 1];
  const close = numericValue(row?.close);
  const high = numericValue(row?.high);
  const previousClose = numericValue(previous?.close);
  const previousHigh = numericValue(previous?.high);
  const band = bollBandAt(data, index);
  const previousBand = bollBandAt(data, index - 1);
  if (
    [close, high, previousClose, previousHigh].some((value) => value === null) ||
    !band ||
    !previousBand
  ) {
    return null;
  }

  const crossesBelowLower = previousClose >= previousBand.lower && close < band.lower;
  const highBreaksUpper = previousHigh <= previousBand.upper && high > band.upper;
  if (!holding && crossesBelowLower) return "buy";
  if (holding && highBreaksUpper) return "sell";
  return null;
}

function ma20BreakoutAction(data, index, holding) {
  // Apply the MA20 breakout, MA60 slope, and overheat take-profit rules.
  const row = data[index];
  const previous = data[index - 1];
  if (!row || !previous) return null;

  const close = numericValue(row.close);
  const ma20 = numericValue(row.ma20);
  const ma60 = numericValue(row.ma60);
  const previousMa20 = numericValue(previous.ma20);
  const previousMa60 = numericValue(previous.ma60);
  if ([close, ma20, ma60, previousMa20, previousMa60].some((value) => value === null)) {
    return null;
  }

  const ma20Rising = ma20 > previousMa20;
  const ma60Rising = ma60 > previousMa60;

  if (!holding) {
    const validBreakout = close > ma20 * 1.02;
    return validBreakout && ma60Rising ? "buy" : null;
  }

  const trendBroken = close < ma20 * 0.98 || !ma20Rising;
  if (trendBroken) return "sell";

  const volume = numericValue(row.volume);
  const volumeAverage = averagePreviousVolume(data, index);
  const changePercent = dailyChangePercent(row, previous);
  if ([volume, volumeAverage, changePercent].some((value) => value === null)) {
    return null;
  }

  const trendStillHealthy = close > ma20 && ma20Rising;
  const overheated = close > ma20 * 1.2;
  const volumeStall = volume > volumeAverage * 2 && changePercent < 2;
  return trendStillHealthy && overheated && volumeStall ? "sell" : null;
}

function strategyAction(strategyId, data, index, holding) {
  // Return only a signal; calculateBacktest applies next-open and limit rules.
  const row = data[index];
  const previous = data[index - 1];
  if (!row) return null;

  if (strategyId === STRATEGY_MA20) {
    const close = numericValue(row.close);
    const ma20 = numericValue(row.ma20);
    if ([close, ma20].some((value) => value === null)) return null;
    if (!holding && close > ma20) return "buy";
    if (holding && close < ma20) return "sell";
    return null;
  }

  if (strategyId === STRATEGY_VOLUME_DROP) {
    if (!previous) return null;
    const close = numericValue(row.close);
    const previousClose = numericValue(previous.close);
    const volume = numericValue(row.volume);
    const volumeAverage = averagePreviousVolume(data, index);
    if ([close, previousClose, volume, volumeAverage].some((value) => value === null)) {
      return null;
    }
    const volumeSpike = volume > volumeAverage * 2;
    const dropPercent = ((close - previousClose) / previousClose) * 100;
    if (holding && volumeSpike) return "sell";
    if (!holding && volumeSpike && dropPercent <= -4) return "buy";
  }

  if (strategyId === STRATEGY_MA20_BREAKOUT) {
    return ma20BreakoutAction(data, index, holding);
  }

  if (strategyId === STRATEGY_BOLL_BREAK) {
    return bollBreakAction(data, index, holding);
  }

  return null;
}

function calculateMaxDrawdown(equityCurve) {
  // Calculate the worst peak-to-trough decline in the equity curve.
  let peak = 1;
  let maxDrawdown = 0;
  equityCurve.forEach((point) => {
    if (!point || !Number.isFinite(point.equity)) return;
    peak = Math.max(peak, point.equity);
    const drawdown = peak > 0 ? point.equity / peak - 1 : 0;
    maxDrawdown = Math.min(maxDrawdown, drawdown);
  });
  return maxDrawdown * 100;
}

export function calculateBacktest(data, strategyId, stock = {}) {
  // Centralize all strategy execution at the next tradable open with limit checks.
  const strategy = strategyOptions.find((item) => item.id === strategyId);
  const limitPercent = priceLimitPercent(stock.symbol, stock.name);
  const signals = [];
  const trades = [];
  const holdingRanges = [];
  const equityCurve = [];
  let capital = 1;
  let shares = 0;
  let holding = false;
  let entry = null;
  let activeRange = null;
  let pendingOrder = null;
  let blockedBuyCount = 0;
  let blockedSellCount = 0;

  data.forEach((row, index) => {
    const previous = data[index - 1];
    const openPrice = numericValue(row.open);
    const closePrice = numericValue(row.close);
    const valuationPrice = closePrice ?? openPrice;

    if (pendingOrder && previous && openPrice !== null && openPrice > 0) {
      if (pendingOrder.type === "buy") {
        if (isLimitUpOpen(row, previous, limitPercent)) {
          blockedBuyCount += 1;
          pendingOrder = null;
        } else if (!holding) {
          shares = capital / openPrice;
          holding = true;
          entry = {
            index,
            date: row.date,
            price: openPrice,
            signalDate: pendingOrder.signalDate,
          };
          activeRange = { startIndex: index, endIndex: index };
          signals.push({
            type: "buy",
            index,
            date: row.date,
            price: openPrice,
            signalDate: pendingOrder.signalDate,
          });
          pendingOrder = null;
        }
      } else if (pendingOrder.type === "sell") {
        if (isLimitDownOpen(row, previous, limitPercent)) {
          blockedSellCount += 1;
        } else if (holding && entry) {
          capital = shares * openPrice;
          shares = 0;
          holding = false;
          signals.push({
            type: "sell",
            index,
            date: row.date,
            price: openPrice,
            signalDate: pendingOrder.signalDate,
          });
          trades.push({
            entryDate: entry.date,
            exitDate: row.date,
            entryPrice: entry.price,
            exitPrice: openPrice,
            returnPct: ((openPrice - entry.price) / entry.price) * 100,
            isOpen: false,
          });
          if (activeRange) {
            activeRange.endIndex = index;
            holdingRanges.push(activeRange);
          }
          activeRange = null;
          entry = null;
          pendingOrder = null;
        }
      }
    }

    if (holding && activeRange) activeRange.endIndex = index;

    const action =
      !pendingOrder && index < data.length - 1
        ? strategyAction(strategyId, data, index, holding)
        : null;
    if (action) {
      pendingOrder = { type: action, signalIndex: index, signalDate: row.date };
    }

    const previousEquity = equityCurve.at(-1)?.equity ?? capital;
    const equity =
      valuationPrice !== null && valuationPrice > 0
        ? holding
          ? shares * valuationPrice
          : capital
        : previousEquity;
    equityCurve.push({ date: row.date, equity });
  });

  const lastRow = data.at(-1);
  const lastPrice = numericValue(lastRow?.close) ?? numericValue(lastRow?.open);
  if (holding && activeRange) {
    activeRange.endIndex = Math.max(activeRange.startIndex, data.length - 1);
    holdingRanges.push(activeRange);
    if (entry && lastPrice !== null && lastPrice > 0) {
      trades.push({
        entryDate: entry.date,
        exitDate: lastRow?.date,
        entryPrice: entry.price,
        exitPrice: lastPrice,
        returnPct: ((lastPrice - entry.price) / entry.price) * 100,
        isOpen: true,
      });
    }
  }

  const finalEquity = equityCurve.at(-1)?.equity ?? 1;
  const winRate = trades.length
    ? (trades.filter((trade) => trade.returnPct > 0).length / trades.length) * 100
    : null;

  return {
    strategyId,
    strategyName: strategy?.name || "策略",
    totalReturn: (finalEquity - 1) * 100,
    maxDrawdown: calculateMaxDrawdown(equityCurve),
    tradeCount: trades.length,
    winRate,
    buyCount: signals.filter((signal) => signal.type === "buy").length,
    sellCount: signals.filter((signal) => signal.type === "sell").length,
    blockedBuyCount,
    blockedSellCount,
    holdingDays: holdingRanges.reduce(
      (total, range) => total + range.endIndex - range.startIndex + 1,
      0,
    ),
    openPosition: holding,
    pendingOrder,
    signals,
    trades,
    holdingRanges,
  };
}

export function backtestStatusText(result) {
  // Summarize the latest backtest state in the compact status line.
  const parts = [];
  if (result.signals.length) {
    parts.push(result.openPosition ? "回测完成，当前持仓" : "回测完成，当前空仓");
  } else {
    parts.push("未触发成交");
  }
  if (result.pendingOrder) {
    parts.push(`${signalLabel(result.pendingOrder.type)}信号待下一交易日开盘执行`);
  }
  if (result.blockedBuyCount) parts.push(`涨停未买${result.blockedBuyCount}次`);
  if (result.blockedSellCount) parts.push(`跌停未卖${result.blockedSellCount}次`);
  return parts.join("，");
}
