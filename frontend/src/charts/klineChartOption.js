// ECharts option builder for K-line, volume, moving averages, and backtest overlays.

import { numericValue } from "../utils/formatters";
import { signalLabel } from "../services/backtest";

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
      top: 8,
      data: ["日K", "MA5", "MA20", "MA60"],
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
      { type: "inside", xAxisIndex: [0, 1], start: 35, end: 100 },
      { type: "slider", xAxisIndex: [0, 1], bottom: 10, height: 22 },
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
