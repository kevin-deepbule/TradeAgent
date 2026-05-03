<script setup>
// Dashboard shell that composes stock data state, backtesting, and UI panels.

import { computed, ref } from "vue";
import AdvicePanel from "./components/AdvicePanel.vue";
import AppHeader from "./components/AppHeader.vue";
import BacktestPanel from "./components/BacktestPanel.vue";
import KlineChartPanel from "./components/KlineChartPanel.vue";
import SummaryCards from "./components/SummaryCards.vue";
import WatchlistPanel from "./components/WatchlistPanel.vue";
import { useBacktest } from "./composables/useBacktest";
import { useStockDashboard } from "./composables/useStockDashboard";
import WatchlistBacktestView from "./views/WatchlistBacktestView.vue";

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
  selectedWatchlistSymbols,
  selectedWatchlistCount,
  watchlistBacktestResults,
  watchlistBacktestStatus,
  watchlistBacktestRunning,
  runBacktest,
  clearBacktest,
  prepareWatchlistBacktestPage,
  setWatchlistBacktestSelection,
  selectAllWatchlistBacktests,
  clearWatchlistBacktestSelection,
  runWatchlistBacktest,
  signalLabel,
  performanceClass,
} = useBacktest({ rows, currentSymbol, currentName, watchlist });

const activePage = ref("dashboard");
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

function openWatchlistBacktestPage() {
  // Switch from the dashboard into the dedicated watchlist backtest page.
  prepareWatchlistBacktestPage();
  activePage.value = "watchlist-backtest";
}

function backToDashboard() {
  // Return to the main K-line dashboard without discarding batch results.
  activePage.value = "dashboard";
}
</script>

<template>
  <main class="app-shell">
    <WatchlistBacktestView
      v-if="activePage === 'watchlist-backtest'"
      v-model:selected-strategy="selectedStrategy"
      :performance-class="performanceClass"
      :selected-strategy-info="selectedStrategyInfo"
      :selected-watchlist-count="selectedWatchlistCount"
      :selected-watchlist-symbols="selectedWatchlistSymbols"
      :strategy-options="strategyOptions"
      :watchlist="watchlist"
      :watchlist-backtest-results="watchlistBacktestResults"
      :watchlist-backtest-running="watchlistBacktestRunning"
      :watchlist-backtest-status="watchlistBacktestStatus"
      @back="backToDashboard"
      @toggle-watchlist-symbol="setWatchlistBacktestSelection"
      @select-all-watchlist-backtests="selectAllWatchlistBacktests"
      @clear-watchlist-backtest-selection="clearWatchlistBacktestSelection"
      @run-watchlist-backtest="runWatchlistBacktest"
    />

    <template v-else>
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
            :watchlist="watchlist"
            :watchlist-backtest-running="watchlistBacktestRunning"
            @run-backtest="runBacktest"
            @open-watchlist-backtest-page="openWatchlistBacktestPage"
          />

          <AdvicePanel :advice="advice" :advice-action-class="adviceActionClass" />
        </div>
      </div>
    </template>
  </main>
</template>
