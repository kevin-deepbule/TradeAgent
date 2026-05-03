<script setup>
// Dedicated view for selecting watchlist stocks and displaying batch backtests.

import { computed } from "vue";
import {
  formatPlainPercent,
  formatSignedPercent,
} from "../utils/formatters";

const selectedStrategy = defineModel("selectedStrategy", {
  type: String,
  default: "",
});

const selectedBacktestYears = defineModel("selectedBacktestYears", {
  type: Number,
  default: 5,
});

const props = defineProps({
  performanceClass: { type: Function, required: true },
  selectedStrategyInfo: { type: Object, default: null },
  selectedWatchlistCount: { type: Number, default: 0 },
  selectedWatchlistSymbols: { type: Array, default: () => [] },
  strategyOptions: { type: Array, default: () => [] },
  watchlist: { type: Array, default: () => [] },
  watchlistBacktestYearOptions: { type: Array, default: () => [] },
  watchlistBacktestResults: { type: Array, default: () => [] },
  watchlistBacktestRunning: { type: Boolean, default: false },
  watchlistBacktestStatus: { type: String, default: "" },
});

function validReturnValues(results, valueForItem) {
  // Collect finite return values from successful batch backtest records.
  return results
    .map(valueForItem)
    .filter((value) => Number.isFinite(Number(value)))
    .map(Number);
}

function average(values) {
  // Calculate the arithmetic mean for an existing list of return percentages.
  return values.reduce((total, value) => total + value, 0) / values.length;
}

function median(values) {
  // Calculate the middle return while preserving the caller's value order.
  const sortedValues = values.slice().sort((left, right) => left - right);
  const middleIndex = Math.floor(sortedValues.length / 2);
  if (sortedValues.length % 2) return sortedValues[middleIndex];
  return (sortedValues[middleIndex - 1] + sortedValues[middleIndex]) / 2;
}

function summarizeReturns(returns) {
  // Build the shared summary shape for strategy and benchmark return groups.
  if (!returns.length) {
    return {
      count: 0,
      averageReturn: null,
      medianReturn: null,
      maxReturn: null,
      minReturn: null,
    };
  }
  return {
    count: returns.length,
    averageReturn: average(returns),
    medianReturn: median(returns),
    maxReturn: Math.max(...returns),
    minReturn: Math.min(...returns),
  };
}

const watchlistBacktestSummary = computed(() => {
  // Summarize strategy and buy-and-hold returns for the result header.
  const strategyReturns = validReturnValues(
    props.watchlistBacktestResults,
    (item) => item.result?.totalReturn,
  );
  const benchmarkReturns = validReturnValues(
    props.watchlistBacktestResults,
    (item) => item.benchmarkReturn,
  );
  return {
    strategy: summarizeReturns(strategyReturns),
    benchmark: summarizeReturns(benchmarkReturns),
  };
});

defineEmits([
  "back",
  "toggle-watchlist-symbol",
  "select-all-watchlist-backtests",
  "clear-watchlist-backtest-selection",
  "run-watchlist-backtest",
]);
</script>

<template>
  <section class="batch-backtest-page">
    <header class="batch-page-header">
      <button class="batch-back-button" type="button" @click="$emit('back')">
        返回
      </button>
      <div>
        <span>策略回测</span>
        <h1>自选股批量回测</h1>
        <p>{{ selectedStrategyInfo?.name }}</p>
      </div>
    </header>

    <div class="batch-page-layout">
      <aside class="batch-control-panel">
        <div class="strategy-field">
          <label for="batch-strategy">策略</label>
          <select id="batch-strategy" v-model="selectedStrategy">
            <option
              v-for="strategy in strategyOptions"
              :key="strategy.id"
              :value="strategy.id"
            >
              {{ strategy.name }}
            </option>
          </select>
        </div>

        <div class="strategy-field">
          <label for="batch-backtest-years">回测周期</label>
          <select
            id="batch-backtest-years"
            v-model.number="selectedBacktestYears"
            :disabled="watchlistBacktestRunning"
          >
            <option
              v-for="option in watchlistBacktestYearOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </div>

        <button
          class="backtest-run batch-start-button"
          type="button"
          :disabled="!selectedWatchlistCount || watchlistBacktestRunning"
          @click="$emit('run-watchlist-backtest')"
        >
          {{ watchlistBacktestRunning ? "回测中" : "开始回测" }}
        </button>

        <div class="batch-select-header">
          <span>选择自选股</span>
          <strong>{{ selectedWatchlistCount }}/{{ watchlist.length }}</strong>
        </div>

        <div class="watchlist-backtest-actions">
          <button
            type="button"
            :disabled="watchlistBacktestRunning || !watchlist.length"
            @click="$emit('select-all-watchlist-backtests')"
          >
            全选
          </button>
          <button
            type="button"
            :disabled="watchlistBacktestRunning || !selectedWatchlistCount"
            @click="$emit('clear-watchlist-backtest-selection')"
          >
            清空
          </button>
        </div>

        <div v-if="!watchlist.length" class="signal-empty">暂无自选股</div>
        <ul v-else class="watchlist-backtest-list batch-watchlist-list">
          <li v-for="item in watchlist" :key="item.symbol">
            <label>
              <input
                type="checkbox"
                :checked="selectedWatchlistSymbols.includes(item.symbol)"
                :disabled="watchlistBacktestRunning"
                @change="
                  $emit('toggle-watchlist-symbol', item.symbol, $event.target.checked)
                "
              />
              <span>
                <strong>{{ item.symbol }}</strong>
                <small>{{ item.name || "未命名" }}</small>
              </span>
            </label>
          </li>
        </ul>
      </aside>

      <section class="batch-result-panel">
        <div class="batch-result-header">
          <div>
            <span>回测结果</span>
            <strong>{{ watchlistBacktestStatus || "待回测" }}</strong>
          </div>
        </div>

        <div
          v-if="
            watchlistBacktestSummary.strategy.count ||
            watchlistBacktestSummary.benchmark.count
          "
          class="batch-summary-groups"
        >
          <section class="batch-summary-group">
            <h2>有策略的盈利数据</h2>
            <div class="batch-summary-metrics">
              <div>
                <span>有效样本</span>
                <strong>{{ watchlistBacktestSummary.strategy.count }} 只</strong>
              </div>
              <div>
                <span>平均盈利</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.strategy.averageReturn)
                  "
                >
                  {{
                    formatSignedPercent(
                      watchlistBacktestSummary.strategy.averageReturn,
                    )
                  }}
                </strong>
              </div>
              <div>
                <span>盈利中位数</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.strategy.medianReturn)
                  "
                >
                  {{
                    formatSignedPercent(
                      watchlistBacktestSummary.strategy.medianReturn,
                    )
                  }}
                </strong>
              </div>
              <div>
                <span>盈利最大值</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.strategy.maxReturn)
                  "
                >
                  {{ formatSignedPercent(watchlistBacktestSummary.strategy.maxReturn) }}
                </strong>
              </div>
              <div>
                <span>盈利最小值</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.strategy.minReturn)
                  "
                >
                  {{ formatSignedPercent(watchlistBacktestSummary.strategy.minReturn) }}
                </strong>
              </div>
            </div>
          </section>

          <section class="batch-summary-group">
            <h2>没有策略，一直持仓的盈利数据</h2>
            <div class="batch-summary-metrics">
              <div>
                <span>有效样本</span>
                <strong>{{ watchlistBacktestSummary.benchmark.count }} 只</strong>
              </div>
              <div>
                <span>平均盈利</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.benchmark.averageReturn)
                  "
                >
                  {{
                    formatSignedPercent(
                      watchlistBacktestSummary.benchmark.averageReturn,
                    )
                  }}
                </strong>
              </div>
              <div>
                <span>盈利中位数</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.benchmark.medianReturn)
                  "
                >
                  {{
                    formatSignedPercent(
                      watchlistBacktestSummary.benchmark.medianReturn,
                    )
                  }}
                </strong>
              </div>
              <div>
                <span>盈利最大值</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.benchmark.maxReturn)
                  "
                >
                  {{ formatSignedPercent(watchlistBacktestSummary.benchmark.maxReturn) }}
                </strong>
              </div>
              <div>
                <span>盈利最小值</span>
                <strong
                  :class="
                    performanceClass(watchlistBacktestSummary.benchmark.minReturn)
                  "
                >
                  {{ formatSignedPercent(watchlistBacktestSummary.benchmark.minReturn) }}
                </strong>
              </div>
            </div>
          </section>
        </div>

        <div v-if="!watchlistBacktestResults.length" class="batch-result-empty">
          请选择自选股并开始回测
        </div>
        <ol v-else class="batch-result-list">
          <li
            v-for="(item, index) in watchlistBacktestResults"
            :key="`${item.symbol}-${index}`"
          >
            <div class="watchlist-backtest-title">
              <strong>{{ item.symbol }}</strong>
              <span>{{ item.name || "未命名" }}</span>
            </div>
            <div v-if="item.result" class="watchlist-backtest-metrics">
              <span :class="performanceClass(item.result.totalReturn)">
                {{ formatSignedPercent(item.result.totalReturn) }}
              </span>
              <small>最大回撤 {{ formatPlainPercent(item.result.maxDrawdown) }}</small>
              <small>交易 {{ item.result.tradeCount }} 笔</small>
              <small>胜率 {{ formatPlainPercent(item.result.winRate, 1) }}</small>
              <small>买/卖 {{ item.result.buyCount }}/{{ item.result.sellCount }}</small>
              <small>持仓 {{ formatSignedPercent(item.benchmarkReturn) }}</small>
              <small>{{ item.result.openPosition ? "当前持仓" : "当前空仓" }}</small>
            </div>
            <div v-else class="watchlist-backtest-error">
              {{ item.error || "回测失败" }}
            </div>
          </li>
        </ol>
      </section>
    </div>
  </section>
</template>
