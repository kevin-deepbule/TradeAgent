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

export function useBacktest({ rows, currentSymbol, currentName }) {
  // Manage selected strategy, current result, and automatic result refreshes.
  const selectedStrategy = ref(STRATEGY_MA20);
  const backtestResult = ref(null);
  const backtestStatus = ref("");

  const selectedStrategyInfo = computed(() =>
    strategyOptions.find((strategy) => strategy.id === selectedStrategy.value),
  );

  const displayBacktestSignals = computed(() =>
    (backtestResult.value?.signals || []).slice().reverse(),
  );

  const hasResult = computed(() => Boolean(backtestResult.value));

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

  watch(selectedStrategy, () => clearBacktest());
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
    runBacktest,
    clearBacktest,
    signalLabel,
    performanceClass,
  };
}
