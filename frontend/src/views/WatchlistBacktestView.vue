<script setup>
// Dedicated view for selecting watchlist stocks and displaying batch backtests.

import {
  formatPlainPercent,
  formatSignedPercent,
} from "../utils/formatters";

const selectedStrategy = defineModel("selectedStrategy", {
  type: String,
  default: "",
});

defineProps({
  performanceClass: { type: Function, required: true },
  selectedStrategyInfo: { type: Object, default: null },
  selectedWatchlistCount: { type: Number, default: 0 },
  selectedWatchlistSymbols: { type: Array, default: () => [] },
  strategyOptions: { type: Array, default: () => [] },
  watchlist: { type: Array, default: () => [] },
  watchlistBacktestResults: { type: Array, default: () => [] },
  watchlistBacktestRunning: { type: Boolean, default: false },
  watchlistBacktestStatus: { type: String, default: "" },
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

        <button
          class="backtest-run batch-start-button"
          type="button"
          :disabled="!selectedWatchlistCount || watchlistBacktestRunning"
          @click="$emit('run-watchlist-backtest')"
        >
          {{ watchlistBacktestRunning ? "回测中" : "开始回测" }}
        </button>
      </aside>

      <section class="batch-result-panel">
        <div class="batch-result-header">
          <div>
            <span>回测结果</span>
            <strong>{{ watchlistBacktestStatus || "待回测" }}</strong>
          </div>
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
