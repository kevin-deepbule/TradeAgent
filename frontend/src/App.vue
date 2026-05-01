<script setup>
// Dashboard shell that composes stock data state, backtesting, and UI panels.

import { computed } from "vue";
import AdvicePanel from "./components/AdvicePanel.vue";
import AppHeader from "./components/AppHeader.vue";
import BacktestPanel from "./components/BacktestPanel.vue";
import KlineChartPanel from "./components/KlineChartPanel.vue";
import SummaryCards from "./components/SummaryCards.vue";
import WatchlistPanel from "./components/WatchlistPanel.vue";
import { useBacktest } from "./composables/useBacktest";
import { useStockDashboard } from "./composables/useStockDashboard";

const {
  queryInput,
  currentSymbol,
  currentName,
  defaultSymbol,
  rows,
  watchlist,
  watchlistLoading,
  updatedAt,
  advice,
  status,
  error,
  copyStatus,
  copySelectionMode,
  copyStartIndex,
  latest,
  change,
  changeClass,
  adviceActionClass,
  loadWatchlist,
  submitQuery,
  addToWatchlist,
  setCurrentAsDefaultStock,
  removeFromWatchlist,
  selectWatchlistStock,
  startCopySelection,
  stopCopySelection,
  pickCopyStart,
  copyRowsInRange,
} = useStockDashboard();

const {
  strategyOptions,
  selectedStrategy,
  selectedStrategyInfo,
  backtestResult,
  backtestStatus,
  displayBacktestSignals,
  runBacktest,
  clearBacktest,
  signalLabel,
  performanceClass,
} = useBacktest({ rows, currentSymbol, currentName });

const statusText = computed(() => copyStatus.value || status.value);

function handleSubmitQuery() {
  // Clear stock-specific overlays before promoting a new query.
  clearBacktest();
  submitQuery();
}

function handleSelectWatchlistStock(item) {
  // Clear stock-specific overlays before switching from the watchlist.
  clearBacktest();
  selectWatchlistStock(item);
}

function toggleCopySelection() {
  // Toggle chart date-range copy mode from the header action.
  if (copySelectionMode.value) {
    stopCopySelection();
  } else {
    startCopySelection();
  }
}
</script>

<template>
  <main class="app-shell">
    <AppHeader
      v-model:query-input="queryInput"
      :copy-selection-mode="copySelectionMode"
      :current-symbol="currentSymbol"
      :default-symbol="defaultSymbol"
      @submit-query="handleSubmitQuery"
      @add-watchlist="addToWatchlist()"
      @set-default-stock="setCurrentAsDefaultStock"
      @toggle-copy-selection="toggleCopySelection"
    />

    <div class="main-layout">
      <WatchlistPanel
        :current-symbol="currentSymbol"
        :loading="watchlistLoading"
        :watchlist="watchlist"
        @refresh="loadWatchlist"
        @select="handleSelectWatchlistStock"
        @remove="removeFromWatchlist"
      />

      <div class="workspace">
        <SummaryCards
          :current-name="currentName"
          :current-symbol="currentSymbol"
          :latest="latest"
          :change="change"
          :change-class="changeClass"
          :updated-at="updatedAt"
          :status-text="statusText"
        />

        <KlineChartPanel
          :backtest-result="backtestResult"
          :current-symbol="currentSymbol"
          :copy-selection-mode="copySelectionMode"
          :copy-start-index="copyStartIndex"
          :error="error"
          :rows="rows"
          @stop-copy-selection="stopCopySelection"
          @pick-copy-start="pickCopyStart"
          @copy-range="copyRowsInRange"
        />

        <BacktestPanel
          v-model:selected-strategy="selectedStrategy"
          :backtest-result="backtestResult"
          :backtest-status="backtestStatus"
          :display-signals="displayBacktestSignals"
          :performance-class="performanceClass"
          :rows="rows"
          :selected-strategy-info="selectedStrategyInfo"
          :signal-label="signalLabel"
          :strategy-options="strategyOptions"
          @run-backtest="runBacktest"
        />

        <AdvicePanel :advice="advice" :advice-action-class="adviceActionClass" />
      </div>
    </div>
  </main>
</template>
