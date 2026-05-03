// Vue state wrapper around strategy backtest calculation and display metadata.

import { computed, ref, watch } from "vue";
import {
  STRATEGY_MA20,
  backtestStatusText,
  calculateBacktest,
  performanceClass,
  signalLabel,
  strategyOptions,
} from "../services/backtest";
import { fetchKline } from "../services/stockApi";

export function useBacktest({ rows, currentSymbol, currentName, watchlist }) {
  // Manage selected strategy, current result, and automatic result refreshes.
  const selectedStrategy = ref(STRATEGY_MA20);
  const backtestResult = ref(null);
  const backtestStatus = ref("");
  const selectedWatchlistSymbols = ref([]);
  const watchlistBacktestResults = ref([]);
  const watchlistBacktestStatus = ref("");
  const watchlistBacktestRunning = ref(false);

  const selectedStrategyInfo = computed(() =>
    strategyOptions.find((strategy) => strategy.id === selectedStrategy.value),
  );

  const displayBacktestSignals = computed(() =>
    (backtestResult.value?.signals || []).slice().reverse(),
  );

  const hasResult = computed(() => Boolean(backtestResult.value));
  const selectedWatchlistCount = computed(() => selectedWatchlistSymbols.value.length);

  function refreshBacktestResult() {
    // Recalculate an existing backtest against the newest K-line rows.
    if (!rows.value.length) {
      backtestResult.value = null;
      backtestStatus.value = "暂无K线数据";
      return;
    }
    backtestResult.value = calculateBacktest(rows.value, selectedStrategy.value, {
      symbol: currentSymbol.value,
      name: currentName.value,
    });
    backtestStatus.value = backtestStatusText(backtestResult.value);
  }

  function runBacktest() {
    // Execute the selected strategy and publish the result.
    refreshBacktestResult();
  }

  function clearBacktest(statusText = "") {
    // Clear strategy output when the symbol or selected strategy changes.
    backtestResult.value = null;
    backtestStatus.value = statusText;
  }

  function clearWatchlistBacktestResults(statusText = "") {
    // Clear batch backtest output while keeping the current stock selection.
    watchlistBacktestResults.value = [];
    watchlistBacktestStatus.value = statusText;
  }

  function prepareWatchlistBacktestPage() {
    // Prime batch selection when entering the dedicated watchlist page.
    if (!watchlist.value.length) {
      watchlistBacktestStatus.value = "暂无自选股";
      return;
    }
    if (!selectedWatchlistSymbols.value.length) {
      const currentItem = watchlist.value.find(
        (item) => item.symbol === currentSymbol.value,
      );
      selectedWatchlistSymbols.value = currentItem ? [currentItem.symbol] : [];
    }
  }

  function setWatchlistBacktestSelection(symbol, checked) {
    // Update one selected watchlist symbol from a checkbox change.
    const nextSymbols = new Set(selectedWatchlistSymbols.value);
    if (checked) {
      nextSymbols.add(symbol);
    } else {
      nextSymbols.delete(symbol);
    }
    selectedWatchlistSymbols.value = Array.from(nextSymbols);
  }

  function selectAllWatchlistBacktests() {
    // Select every currently available watchlist symbol for batch execution.
    selectedWatchlistSymbols.value = watchlist.value.map((item) => item.symbol);
  }

  function clearWatchlistBacktestSelection() {
    // Clear the batch selection without hiding the picker.
    selectedWatchlistSymbols.value = [];
  }

  async function runWatchlistBacktest() {
    // Fetch selected watchlist K-lines and run the shared strategy engine on each.
    if (watchlistBacktestRunning.value) return;
    const selectedItems = watchlist.value.filter((item) =>
      selectedWatchlistSymbols.value.includes(item.symbol),
    );
    if (!selectedItems.length) {
      watchlistBacktestStatus.value = "请选择自选股";
      return;
    }

    watchlistBacktestRunning.value = true;
    watchlistBacktestResults.value = [];
    watchlistBacktestStatus.value = `正在回测 0/${selectedItems.length}`;

    const nextResults = [];
    try {
      for (const [index, item] of selectedItems.entries()) {
        try {
          const payload = await fetchKline(item.symbol);
          const result = calculateBacktest(payload.rows || [], selectedStrategy.value, {
            symbol: payload.symbol || item.symbol,
            name: payload.name || item.name || "",
          });
          nextResults.push({
            symbol: payload.symbol || item.symbol,
            name: payload.name || item.name || "",
            result,
            error: "",
          });
        } catch (exc) {
          nextResults.push({
            symbol: item.symbol,
            name: item.name || "",
            result: null,
            error: exc.message || "回测失败",
          });
        }
        watchlistBacktestResults.value = nextResults.slice();
        watchlistBacktestStatus.value = `正在回测 ${index + 1}/${selectedItems.length}`;
      }

      const successCount = nextResults.filter((item) => item.result).length;
      const failedCount = nextResults.length - successCount;
      watchlistBacktestStatus.value = failedCount
        ? `完成 ${successCount} 只，失败 ${failedCount} 只`
        : `完成 ${successCount} 只自选股回测`;
    } finally {
      watchlistBacktestRunning.value = false;
    }
  }

  watch(selectedStrategy, () => {
    clearBacktest();
    clearWatchlistBacktestResults();
  });
  watch(watchlist, (items) => {
    const availableSymbols = new Set(items.map((item) => item.symbol));
    selectedWatchlistSymbols.value = selectedWatchlistSymbols.value.filter((symbol) =>
      availableSymbols.has(symbol),
    );
  });
  watch(rows, () => {
    if (hasResult.value) refreshBacktestResult();
  });

  return {
    strategyOptions,
    selectedStrategy,
    selectedStrategyInfo,
    backtestResult,
    backtestStatus,
    displayBacktestSignals,
    hasResult,
    selectedWatchlistSymbols,
    selectedWatchlistCount,
    watchlistBacktestResults,
    watchlistBacktestStatus,
    watchlistBacktestRunning,
    runBacktest,
    clearBacktest,
    clearWatchlistBacktestResults,
    prepareWatchlistBacktestPage,
    setWatchlistBacktestSelection,
    selectAllWatchlistBacktests,
    clearWatchlistBacktestSelection,
    runWatchlistBacktest,
    signalLabel,
    performanceClass,
  };
}
