<script setup>
// K-line chart panel with copy-range overlay and backtest visual markers.

import { computed, ref, toRefs, watch } from "vue";
import { useKlineChart } from "../composables/useKlineChart";

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
  // Move the selected K-line one trading day at a time with arrow keys.
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
  // Reset selection when switching to another stock.
  selectedKlineIndex.value = null;
});

const { chartEl, ensureKlineVisible } = useKlineChart({
  chartKey: currentSymbol,
  rows,
  copySelectionMode,
  copyStartIndex,
  selectedKlineIndex,
  backtestResult,
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
    <div
      ref="chartEl"
      class="chart"
      tabindex="0"
      aria-label="K线图"
      @keydown="handleChartKeydown"
    ></div>
  </section>
</template>
