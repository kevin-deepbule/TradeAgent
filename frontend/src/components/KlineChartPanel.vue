<script setup>
// K-line chart panel with copy-range overlay and backtest visual markers.

import { computed, ref, toRefs, watch } from "vue";
import { useKlineChart } from "../composables/useKlineChart";
import { numericValue, formatSignedPercent } from "../utils/formatters";

const props = defineProps({
  backtestResult: { type: Object, default: null },
  currentSymbol: { type: String, default: "" },
  copySelectionMode: { type: Boolean, default: false },
  copyStartIndex: { type: Number, default: null },
  error: { type: String, default: "" },
  rows: { type: Array, default: () => [] },
});

const emit = defineEmits(["stop-copy-selection", "pick-copy-start", "copy-range"]);

const { rows, currentSymbol, copySelectionMode, copyStartIndex, backtestResult } =
  toRefs(props);
const selectedKlineIndex = ref(null);
const manualEntry = ref(null);
const manualSignals = ref([]);
const manualTrades = ref([]);
const manualHoldingRanges = ref([]);
const manualTradeStatus = ref("");

const manualOpenTrade = computed(() => {
  // Calculate floating P/L against the currently selected K-line.
  const entry = manualEntry.value;
  if (!entry || selectedKlineIndex.value === null) return null;
  const exitIndex = Math.max(entry.index, selectedKlineIndex.value);
  const exitRow = props.rows[exitIndex];
  const exitPrice =
    numericValue(exitRow?.close) ?? numericValue(exitRow?.open) ?? entry.price;
  if (!exitRow || exitPrice === null || exitPrice <= 0) return null;
  return {
    entryDate: entry.date,
    entryIndex: entry.index,
    exitDate: exitRow.date,
    exitIndex,
    entryPrice: entry.price,
    exitPrice,
    returnPct: ((exitPrice - entry.price) / entry.price) * 100,
    isOpen: true,
  };
});

const manualTradeResult = computed(() => {
  // Present manual keyboard trades in the same overlay shape as backtests.
  const openTrade = manualOpenTrade.value;
  const openRange = manualEntry.value
    ? {
        startIndex: manualEntry.value.index,
        endIndex: Math.max(
          manualEntry.value.index,
          selectedKlineIndex.value ?? manualEntry.value.index,
        ),
      }
    : null;
  return {
    signals: manualSignals.value,
    trades: openTrade ? [...manualTrades.value, openTrade] : manualTrades.value,
    holdingRanges: openRange
      ? [...manualHoldingRanges.value, openRange]
      : manualHoldingRanges.value,
  };
});

const manualTotalReturn = computed(() => {
  // Compound all completed manual trades plus the current open trade.
  const trades = manualOpenTrade.value
    ? [...manualTrades.value, manualOpenTrade.value]
    : manualTrades.value;
  if (!trades.length) return null;
  const equity = trades.reduce(
    (value, trade) => value * (1 + trade.returnPct / 100),
    1,
  );
  return (equity - 1) * 100;
});

const manualTradeDisplayStatus = computed(() => {
  // Show current manual trade state without requiring a separate panel.
  const totalReturnText =
    manualTotalReturn.value === null
      ? ""
      : `，累计盈亏 ${formatSignedPercent(manualTotalReturn.value)}`;
  const openTrade = manualOpenTrade.value;
  if (openTrade) {
    return `持仓中 ${openTrade.entryDate} 买入 ${openTrade.entryPrice.toFixed(2)}，当前盈亏 ${formatSignedPercent(openTrade.returnPct)}${totalReturnText}`;
  }
  if (manualTrades.value.length) return `手动交易 ${manualTrades.value.length} 笔${totalReturnText}`;
  return manualTradeStatus.value;
});

const hasManualTradeMarks = computed(() => {
  // Show clear controls only when manual trade overlays or messages exist.
  return Boolean(
    manualEntry.value ||
      manualSignals.value.length ||
      manualTrades.value.length ||
      manualHoldingRanges.value.length ||
      manualTradeStatus.value,
  );
});

const selectionMessage = computed(() => {
  // Explain the current date-range copy step.
  if (props.copyStartIndex === null) return "单击K线选择第一个日期。";
  return `已选 ${props.rows[props.copyStartIndex]?.date}，再单击第二个日期后自动复制。`;
});

function handleChartClick(params) {
  // Use two candlestick clicks to choose and copy an inclusive date range.
  if (!props.copySelectionMode) return;
  if (params.componentType !== "series" || params.seriesIndex !== 0) return;

  const clickedIndex = params.dataIndex;
  if (typeof clickedIndex !== "number") return;

  if (props.copyStartIndex === null) {
    emit("pick-copy-start", clickedIndex);
    return;
  }

  const startIndex = Math.min(props.copyStartIndex, clickedIndex);
  const endIndex = Math.max(props.copyStartIndex, clickedIndex);
  emit("copy-range", startIndex, endIndex);
}

function handleChartKeydown(event) {
  // Route chart keyboard shortcuts for manual trading and K-line navigation.
  if (["b", "B"].includes(event.key)) {
    event.preventDefault();
    buyNextOpen();
    return;
  }
  if (["s", "S"].includes(event.key)) {
    event.preventDefault();
    sellNextOpen();
    return;
  }
  if (!["ArrowLeft", "ArrowRight"].includes(event.key) || !props.rows.length) return;
  event.preventDefault();
  const direction = event.key === "ArrowLeft" ? -1 : 1;
  const fallbackIndex = props.rows.length - 1;
  const currentIndex =
    selectedKlineIndex.value === null ? fallbackIndex : selectedKlineIndex.value;
  const nextIndex = Math.max(
    0,
    Math.min(props.rows.length - 1, currentIndex + direction),
  );
  selectedKlineIndex.value = nextIndex;
  ensureKlineVisible(nextIndex, direction);
}

function nextOpenExecution() {
  // Resolve the next trading day's open for a keyboard trade.
  if (selectedKlineIndex.value === null) {
    return { error: "请先双击选中一根K线" };
  }
  const index = selectedKlineIndex.value + 1;
  const row = props.rows[index];
  const price = numericValue(row?.open);
  if (!row || price === null || price <= 0) {
    return { error: "下一交易日开盘价不可用" };
  }
  return { index, row, price };
}

function buyNextOpen() {
  // Buy at the selected K-line's next trading day open.
  if (manualEntry.value) {
    manualTradeStatus.value = "已有持仓，请先按 S 卖出";
    return;
  }
  const execution = nextOpenExecution();
  if (execution.error) {
    manualTradeStatus.value = execution.error;
    return;
  }
  manualEntry.value = {
    index: execution.index,
    date: execution.row.date,
    price: execution.price,
    signalIndex: selectedKlineIndex.value,
  };
  manualSignals.value = [
    ...manualSignals.value,
    {
      type: "buy",
      index: execution.index,
      date: execution.row.date,
      price: execution.price,
      signalDate: props.rows[selectedKlineIndex.value]?.date,
    },
  ];
  manualTradeStatus.value = `买入 ${execution.row.date} 开盘 ${execution.price.toFixed(2)}`;
}

function sellNextOpen() {
  // Sell at the selected K-line's next trading day open and lock in P/L.
  const entry = manualEntry.value;
  if (!entry) {
    manualTradeStatus.value = "当前无持仓，请先按 B 买入";
    return;
  }
  const execution = nextOpenExecution();
  if (execution.error) {
    manualTradeStatus.value = execution.error;
    return;
  }
  if (execution.index <= entry.index) {
    manualTradeStatus.value = "卖出日需晚于买入日";
    return;
  }
  const returnPct = ((execution.price - entry.price) / entry.price) * 100;
  manualSignals.value = [
    ...manualSignals.value,
    {
      type: "sell",
      index: execution.index,
      date: execution.row.date,
      price: execution.price,
      signalDate: props.rows[selectedKlineIndex.value]?.date,
    },
  ];
  manualTrades.value = [
    ...manualTrades.value,
    {
      entryDate: entry.date,
      entryIndex: entry.index,
      exitDate: execution.row.date,
      exitIndex: execution.index,
      entryPrice: entry.price,
      exitPrice: execution.price,
      returnPct,
      isOpen: false,
    },
  ];
  manualHoldingRanges.value = [
    ...manualHoldingRanges.value,
    { startIndex: entry.index, endIndex: execution.index },
  ];
  manualEntry.value = null;
  const nextTrades = [...manualTrades.value];
  const totalReturn = nextTrades.reduce(
    (value, trade) => value * (1 + trade.returnPct / 100),
    1,
  );
  manualTradeStatus.value = `卖出 ${execution.row.date} 开盘 ${execution.price.toFixed(2)}，本笔 ${formatSignedPercent(returnPct)}，累计 ${formatSignedPercent((totalReturn - 1) * 100)}`;
}

function clearManualTradeMarks() {
  // Remove keyboard trade markers and reset the manual trade state.
  manualEntry.value = null;
  manualSignals.value = [];
  manualTrades.value = [];
  manualHoldingRanges.value = [];
  manualTradeStatus.value = "";
}

watch(rows, (nextRows) => {
  // Keep the selected index valid when live data changes.
  if (!nextRows.length) {
    selectedKlineIndex.value = null;
    return;
  }
  if (selectedKlineIndex.value !== null && selectedKlineIndex.value >= nextRows.length) {
    selectedKlineIndex.value = nextRows.length - 1;
  }
});

watch(currentSymbol, () => {
  // Reset selection and manual trades when switching to another stock.
  selectedKlineIndex.value = null;
  manualEntry.value = null;
  manualSignals.value = [];
  manualTrades.value = [];
  manualHoldingRanges.value = [];
  manualTradeStatus.value = "";
});

const { chartEl, ensureKlineVisible } = useKlineChart({
  chartKey: currentSymbol,
  rows,
  copySelectionMode,
  copyStartIndex,
  selectedKlineIndex,
  backtestResult,
  manualTradeResult,
  onChartClick: handleChartClick,
});
</script>

<template>
  <section class="chart-panel">
    <div v-if="error" class="error-bar">{{ error }}</div>
    <div v-if="copySelectionMode" class="selection-tip">
      <span>{{ selectionMessage }}</span>
      <button type="button" @click="$emit('stop-copy-selection')">取消</button>
    </div>
    <div v-if="hasManualTradeMarks" class="manual-trade-tools">
      <span v-if="manualTradeDisplayStatus" class="manual-trade-status">
        {{ manualTradeDisplayStatus }}
      </span>
      <button type="button" class="manual-trade-clear" @click="clearManualTradeMarks">
        清除标记
      </button>
    </div>
    <div
      ref="chartEl"
      class="chart"
      tabindex="0"
      aria-label="K线图"
      @keydown="handleChartKeydown"
    ></div>
  </section>
</template>
