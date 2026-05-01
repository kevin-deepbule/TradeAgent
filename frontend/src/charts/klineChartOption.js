// ECharts option builder for K-line, volume, moving averages, and backtest overlays.

import { formatSignedPercent, numericValue } from "../utils/formatters";
import { signalLabel } from "../services/backtest";

const DEFAULT_VISIBLE_COUNT = 120;
const BOLL_WINDOW = 20;
const BOLL_MULTIPLIER = 2;
const VOLUME_MA_WINDOWS = [5, 20, 60];
const MOVING_AVERAGE_COLORS = {
  5: "#2f80ed",
  20: "#f2994a",
  60: "#6fcf97",
};
const POINTER_LINE_STYLE = {
  color: "#9aa4b2",
  type: "dashed",
  width: 1,
};

function defaultZoomRange(dates) {
  // Show a stable number of recent K-lines instead of a calendar-month window.
  if (!dates.length) return {};
  const startIndex = Math.max(dates.length - DEFAULT_VISIBLE_COUNT, 0);
  return {
    startValue: dates[startIndex],
    endValue: dates.at(-1),
  };
}

function normalizeZoomRange(dates, zoomRange) {
  // Keep a user-selected date range only when both endpoints exist in the data.
  if (!zoomRange?.startValue || !zoomRange?.endValue) return defaultZoomRange(dates);
  if (!dates.includes(zoomRange.startValue) || !dates.includes(zoomRange.endValue)) {
    return defaultZoomRange(dates);
  }
  return {
    startValue: zoomRange.startValue,
    endValue: zoomRange.endValue,
  };
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

function calculateVolumeAverage(rows, days) {
  // Calculate a rolling average volume line for the lower volume chart.
  const volumes = rows.map((item) => numericValue(item.volume));
  return volumes.map((volume, index) => {
    if (volume === null || index < days - 1) return null;
    const windowVolumes = volumes.slice(index - days + 1, index + 1);
    if (windowVolumes.some((value) => value === null)) return null;
    return windowVolumes.reduce((sum, value) => sum + value, 0) / days;
  });
}

function isSelectedIndex(rows, index) {
  // Check whether a chart row index can be highlighted.
  return Number.isInteger(index) && index >= 0 && index < rows.length;
}

function volumeBarColor(row, opacity = 0.72) {
  // Match volume bar color to the corresponding candle direction.
  const open = numericValue(row?.open);
  const close = numericValue(row?.close);
  if (open === null || close === null) return "#9aa4b2";
  return close >= open
    ? `rgba(214,69,69,${opacity})`
    : `rgba(26,155,104,${opacity})`;
}

function buildCandleData(rows) {
  // Shape OHLC rows for the candlestick series.
  return rows.map((item) => [
    numericValue(item.open),
    numericValue(item.close),
    numericValue(item.low),
    numericValue(item.high),
  ]);
}

function buildVolumeData(rows) {
  // Shape volume rows for the lower bar series.
  return rows.map((item) => item.volume);
}

function selectedMarkLine(dates, selectedIndex) {
  // Build a vertical guide line for the selected date.
  const date = dates[selectedIndex];
  if (!date) return [];
  return [
    {
      name: "选中",
      xAxis: date,
      lineStyle: {
        ...POINTER_LINE_STYLE,
      },
    },
  ];
}

function formatTooltipNumber(value, digits = 2) {
  // Format a tooltip number while keeping empty values readable.
  const number = numericValue(value);
  return number === null ? "--" : number.toFixed(digits);
}

function formatTooltipVolume(value) {
  // Format chart volume without forcing decimal noise into the tooltip.
  const number = numericValue(value);
  return number === null ? "--" : number.toLocaleString("zh-CN");
}

function ratioInfo(value, base) {
  // Return display text and status color for a value/base ratio.
  const number = numericValue(value);
  const baseNumber = numericValue(base);
  if (number === null || baseNumber === null || baseNumber <= 0) {
    return {
      percent: "--",
      relation: "数据不足",
      color: "#667085",
      background: "#f2f4f7",
    };
  }
  const percent = (number / baseNumber) * 100;
  const gap = percent - 100;
  if (Math.abs(gap) < 0.005) {
    return {
      percent: `${percent.toFixed(2)}%`,
      relation: "持平",
      color: "#667085",
      background: "#f2f4f7",
    };
  }
  const isAbove = gap > 0;
  return {
    percent: `${percent.toFixed(2)}%`,
    relation: `${isAbove ? "高于" : "低于"} ${Math.abs(gap).toFixed(2)}%`,
    color: isAbove ? "#c43836" : "#16865d",
    background: isAbove ? "#fff1f0" : "#ecfdf3",
  };
}

function tooltipPriceCell(label, value, formatter = formatTooltipNumber) {
  // Render a compact label/value cell for OHLC data.
  return `
    <div style="display:grid;gap:1px;">
      <span style="color:#667085;font-size:10px;">${label}</span>
      <strong style="color:#182230;font-size:12px;font-weight:700;overflow-wrap:anywhere;">${formatter(value)}</strong>
    </div>
  `;
}

function tooltipMetric(label, info) {
  // Render a ratio metric with a colored status badge.
  return `
    <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;">
      <span style="color:#475467;font-size:11px;">${label}</span>
      <span style="display:inline-flex;align-items:center;gap:5px;min-width:108px;justify-content:flex-end;">
        <strong style="color:${info.color};font-size:11px;font-weight:750;">${info.percent}</strong>
        <span style="border-radius:999px;background:${info.background};color:${info.color};padding:1px 5px;font-size:10px;line-height:1.35;">${info.relation}</span>
      </span>
    </div>
  `;
}

function makeTooltipFormatter(rows, volumeAverages) {
  // Build the hover card content from the row data backing the chart point.
  return (params) => {
    const firstParam = Array.isArray(params) ? params[0] : params;
    const index = firstParam?.dataIndex;
    const row = rows[index];
    if (!row) return "";
    const closeMa5 = ratioInfo(row.close, row.ma5);
    const closeMa20 = ratioInfo(row.close, row.ma20);
    const closeMa60 = ratioInfo(row.close, row.ma60);
    const volumeMa20 = ratioInfo(row.volume, volumeAverages[index]);

    return `
      <div style="min-width:224px;color:#182230;">
        <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:7px;">
          <div>
            <div style="color:#667085;font-size:10px;line-height:1.25;">交易日</div>
            <div style="font-size:12px;font-weight:750;line-height:1.4;">${row.date}</div>
          </div>
          <div style="text-align:right;">
            <div style="color:#667085;font-size:10px;line-height:1.25;">收盘</div>
            <div style="color:${closeMa20.color};font-size:16px;font-weight:800;line-height:1.1;">${formatTooltipNumber(row.close)}</div>
          </div>
        </div>
        <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px 12px;border-top:1px solid rgba(238,242,246,0.85);border-bottom:1px solid rgba(238,242,246,0.85);padding:7px 0;margin-bottom:7px;">
          ${tooltipPriceCell("开盘", row.open)}
          ${tooltipPriceCell("最高", row.high)}
          ${tooltipPriceCell("最低", row.low)}
          ${tooltipPriceCell("成交量", row.volume, formatTooltipVolume)}
        </div>
        <div style="display:grid;gap:5px;">
          ${tooltipMetric("收盘 / MA5", closeMa5)}
          ${tooltipMetric("收盘 / MA20", closeMa20)}
          ${tooltipMetric("收盘 / MA60", closeMa60)}
          ${tooltipMetric("量能 / 20日均量", volumeMa20)}
        </div>
      </div>
    `;
  };
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

function tradeReturnMarkPoints(dates, backtestResult) {
  // Convert each executed trade into a compact return label at its exit point.
  return (backtestResult?.trades || [])
    .map((trade) => {
      const exitIndex = trade.exitIndex;
      const exitDate = dates[exitIndex];
      const exitPrice = numericValue(trade.exitPrice);
      const returnPct = numericValue(trade.returnPct);
      if (!exitDate || exitPrice === null || returnPct === null) return null;

      const isProfit = returnPct >= 0;
      return {
        name: trade.isOpen ? "浮动盈亏率" : "交易盈亏率",
        coord: [exitDate, exitPrice],
        value: `${trade.isOpen ? "浮 " : ""}${formatSignedPercent(returnPct)}`,
        symbol: "roundRect",
        symbolSize: trade.isOpen ? [76, 24] : [64, 24],
        symbolOffset: [0, -58],
        itemStyle: {
          color: isProfit ? "#c43836" : "#16865d",
          borderColor: "#ffffff",
          borderWidth: 1.5,
          shadowBlur: 7,
          shadowColor: "rgba(16,24,40,0.18)",
        },
        label: {
          color: "#ffffff",
          fontSize: 10,
          fontWeight: 750,
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
  selectedKlineIndex,
  backtestResult,
  zoomRange,
}) {
  // Build the full ECharts option from K-line rows and dashboard overlays.
  const dates = rows.map((item) => item.date);
  const activeSelectedIndex = isSelectedIndex(rows, selectedKlineIndex)
    ? selectedKlineIndex
    : null;
  const candle = buildCandleData(rows);
  const volumeBars = buildVolumeData(rows);
  const ma5 = rows.map((item) => item.ma5);
  const ma20 = rows.map((item) => item.ma20);
  const ma60 = rows.map((item) => item.ma60);
  const strategyMarks = backtestMarkPoints(rows, dates, backtestResult);
  const tradeReturnMarks = tradeReturnMarkPoints(dates, backtestResult);
  const holdingAreas = backtestMarkAreas(dates, backtestResult);
  const selectedLines = selectedMarkLine(dates, activeSelectedIndex);
  const activeZoomRange = normalizeZoomRange(dates, zoomRange);
  const bollBands = calculateBollBands(rows);
  const volumeAverages = Object.fromEntries(
    VOLUME_MA_WINDOWS.map((days) => [days, calculateVolumeAverage(rows, days)]),
  );
  const legendData = [
    "日K",
    "MA5",
    "MA20",
    "MA60",
    "BOLL上轨",
    "BOLL下轨",
    "成交量",
    "量MA5",
    "量MA20",
    "量MA60",
  ];

  return {
    animation: false,
    color: ["#2f80ed", "#f2994a", "#6fcf97"],
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "cross" },
      confine: true,
      backgroundColor: "rgba(255, 255, 255, 0.72)",
      borderColor: "#dce2ea",
      borderWidth: 1,
      borderRadius: 8,
      padding: [8, 10],
      extraCssText:
        "box-shadow:0 6px 18px rgba(16,24,40,0.08);backdrop-filter:blur(4px);",
      textStyle: {
        color: "#182230",
        fontFamily:
          'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        fontSize: 12,
      },
      formatter: makeTooltipFormatter(rows, volumeAverages[20]),
    },
    axisPointer: {
      link: [{ xAxisIndex: [0, 1] }],
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
        axisPointer: {
          show: true,
          type: "shadow",
          shadowStyle: { color: "rgba(25,95,201,0.12)" },
          label: { show: false },
        },
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
        zoomLock: true,
        ...activeZoomRange,
      },
      {
        type: "slider",
        xAxisIndex: [0, 1],
        zoomLock: true,
        ...activeZoomRange,
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
          data: [
            ...(copySelectionMode && copyStartIndex !== null
              ? [
                  {
                    xAxis: dates[copyStartIndex],
                    lineStyle: {
                      ...POINTER_LINE_STYLE,
                    },
                  },
                ]
              : []),
            ...selectedLines,
          ],
        },
        markPoint: {
          data: [...strategyMarks, ...tradeReturnMarks],
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
        lineStyle: { width: 1.5, color: MOVING_AVERAGE_COLORS[5] },
      },
      {
        name: "MA20",
        type: "line",
        data: ma20,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5, color: MOVING_AVERAGE_COLORS[20] },
      },
      {
        name: "MA60",
        type: "line",
        data: ma60,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5, color: MOVING_AVERAGE_COLORS[60] },
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
        data: volumeBars,
        markLine: {
          silent: true,
          symbol: "none",
          label: { show: false },
          data: selectedLines,
        },
        itemStyle: {
          color: (params) => {
            return volumeBarColor(rows[params.dataIndex]);
          },
        },
        emphasis: {
          itemStyle: {
            color: (params) => {
              return volumeBarColor(rows[params.dataIndex], 1);
            },
          },
        },
      },
      {
        name: "量MA5",
        type: "line",
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeAverages[5],
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.25, color: MOVING_AVERAGE_COLORS[5] },
      },
      {
        name: "量MA20",
        type: "line",
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeAverages[20],
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.35, color: MOVING_AVERAGE_COLORS[20] },
      },
      {
        name: "量MA60",
        type: "line",
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeAverages[60],
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.35, color: MOVING_AVERAGE_COLORS[60] },
      },
    ],
  };
}
