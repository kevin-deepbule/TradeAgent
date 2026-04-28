// ECharts option builder for K-line, volume, moving averages, and backtest overlays.

import { numericValue } from "../utils/formatters";
import { signalLabel } from "../services/backtest";

const DEFAULT_VISIBLE_MONTHS = 6;
const BOLL_WINDOW = 20;
const BOLL_MULTIPLIER = 2;

function formatLocalDate(date) {
  // Format a Date with local calendar fields to avoid UTC day shifts.
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function monthsBefore(dateText, months) {
  // Return a YYYY-MM-DD date string several calendar months before dateText.
  const date = new Date(`${dateText}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  date.setMonth(date.getMonth() - months);
  return formatLocalDate(date);
}

function defaultZoomStartValue(dates) {
  // Pick the first available trading day inside the default visible window.
  const latestDate = dates.at(-1);
  if (!latestDate) return undefined;
  const cutoff = monthsBefore(latestDate, DEFAULT_VISIBLE_MONTHS);
  if (!cutoff) return dates[0];
  return dates.find((date) => date >= cutoff) || dates[0];
}

function calculateBollBands(rows) {
  // Calculate BOLL(20, 2) upper and lower bands from closing prices.
  const closes = rows.map((item) => numericValue(item.close));
  const upper = [];
  const lower = [];

  closes.forEach((close, index) => {
    if (close === null || index < BOLL_WINDOW - 1) {
      upper.push(null);
      lower.push(null);
      return;
    }

    const windowCloses = closes.slice(index - BOLL_WINDOW + 1, index + 1);
    if (windowCloses.some((value) => value === null)) {
      upper.push(null);
      lower.push(null);
      return;
    }

    const mean =
      windowCloses.reduce((sum, value) => sum + value, 0) / BOLL_WINDOW;
    const variance =
      windowCloses.reduce((sum, value) => sum + (value - mean) ** 2, 0) /
      BOLL_WINDOW;
    const offset = Math.sqrt(variance) * BOLL_MULTIPLIER;
    upper.push(mean + offset);
    lower.push(mean - offset);
  });

  return { upper, lower };
}

function backtestMarkPoints(rows, dates, backtestResult) {
  // Convert backtest buy/sell executions into ECharts marker points.
  return (backtestResult?.signals || [])
    .map((signal) => {
      if (!dates[signal.index]) return null;
      return {
        name: signalLabel(signal.type),
        coord: [dates[signal.index], signal.price],
        value: signal.type === "buy" ? "买" : "卖",
        symbol: "pin",
        symbolSize: 44,
        symbolOffset: [0, signal.type === "buy" ? 24 : -24],
        itemStyle: {
          color: signal.type === "buy" ? "#c43836" : "#16865d",
        },
        label: {
          color: "#ffffff",
          fontWeight: 700,
          formatter: "{c}",
        },
      };
    })
    .filter(Boolean);
}

function backtestMarkAreas(dates, backtestResult) {
  // Convert holding ranges into yellow chart background bands.
  return (backtestResult?.holdingRanges || [])
    .map((range) => {
      const start = dates[range.startIndex];
      const end = dates[range.endIndex];
      if (!start || !end) return null;
      return [{ name: "持仓", xAxis: start }, { xAxis: end }];
    })
    .filter(Boolean);
}

export function makeKlineChartOption({
  rows,
  copySelectionMode,
  copyStartIndex,
  backtestResult,
}) {
  // Build the full ECharts option from K-line rows and dashboard overlays.
  const dates = rows.map((item) => item.date);
  const candle = rows.map((item) => [
    numericValue(item.open),
    numericValue(item.close),
    numericValue(item.low),
    numericValue(item.high),
  ]);
  const ma5 = rows.map((item) => item.ma5);
  const ma20 = rows.map((item) => item.ma20);
  const ma60 = rows.map((item) => item.ma60);
  const strategyMarks = backtestMarkPoints(rows, dates, backtestResult);
  const holdingAreas = backtestMarkAreas(dates, backtestResult);
  const zoomStartValue = defaultZoomStartValue(dates);
  const bollBands = calculateBollBands(rows);
  const legendData = ["日K", "MA5", "MA20", "MA60", "BOLL上轨", "BOLL下轨"];

  return {
    animation: false,
    color: ["#2f80ed", "#f2994a", "#6fcf97"],
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "cross" },
      borderWidth: 1,
      textStyle: { fontSize: 12 },
    },
    legend: {
      type: "scroll",
      top: 8,
      data: legendData,
      selected: {
        BOLL上轨: false,
        BOLL下轨: false,
      },
    },
    grid: [
      { left: 56, right: 28, top: 48, height: "62%" },
      { left: 56, right: 28, top: "78%", height: "12%" },
    ],
    xAxis: [
      {
        type: "category",
        data: dates,
        boundaryGap: false,
        axisLine: { lineStyle: { color: "#9aa4b2" } },
        axisLabel: { color: "#526071" },
        min: "dataMin",
        max: "dataMax",
      },
      {
        type: "category",
        gridIndex: 1,
        data: dates,
        boundaryGap: false,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: "#d6dbe3" } },
      },
    ],
    yAxis: [
      {
        scale: true,
        splitLine: { lineStyle: { color: "#edf0f5" } },
        axisLabel: { color: "#526071" },
      },
      {
        scale: true,
        gridIndex: 1,
        splitNumber: 2,
        splitLine: { lineStyle: { color: "#edf0f5" } },
        axisLabel: { color: "#526071" },
      },
    ],
    dataZoom: [
      {
        type: "inside",
        xAxisIndex: [0, 1],
        startValue: zoomStartValue,
        end: 100,
      },
      {
        type: "slider",
        xAxisIndex: [0, 1],
        startValue: zoomStartValue,
        end: 100,
        bottom: 10,
        height: 22,
      },
    ],
    series: [
      {
        name: "日K",
        type: "candlestick",
        data: candle,
        markLine: {
          silent: true,
          symbol: "none",
          label: { show: false },
          lineStyle: {
            color: "#195fc9",
            type: "dashed",
            width: 1.5,
          },
          data:
            copySelectionMode && copyStartIndex !== null
              ? [{ xAxis: dates[copyStartIndex] }]
              : [],
        },
        markPoint: {
          data: strategyMarks,
        },
        markArea: {
          silent: true,
          label: { show: false },
          itemStyle: { color: "rgba(247, 196, 83, 0.18)" },
          data: holdingAreas,
        },
        itemStyle: {
          color: "#d64545",
          color0: "#1a9b68",
          borderColor: "#d64545",
          borderColor0: "#1a9b68",
        },
      },
      {
        name: "MA5",
        type: "line",
        data: ma5,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: "MA20",
        type: "line",
        data: ma20,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: "MA60",
        type: "line",
        data: ma60,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: "BOLL上轨",
        type: "line",
        data: bollBands.upper,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.3, color: "#7a4cc2" },
      },
      {
        name: "BOLL下轨",
        type: "line",
        data: bollBands.lower,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.3, color: "#00a6a6" },
      },
      {
        name: "成交量",
        type: "bar",
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: rows.map((item) => item.volume),
        itemStyle: { color: "#9aa4b2" },
      },
    ],
  };
}
