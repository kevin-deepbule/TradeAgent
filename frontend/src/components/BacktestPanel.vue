<script setup>
// Strategy selector, backtest metrics, and executed signal list.

import {
  formatNumber,
  formatPlainPercent,
  formatSignedPercent,
} from "../utils/formatters";

const selectedStrategy = defineModel("selectedStrategy", {
  type: String,
  default: "",
});

defineProps({
  backtestResult: { type: Object, default: null },
  backtestStatus: { type: String, default: "" },
  displaySignals: { type: Array, default: () => [] },
  performanceClass: { type: Function, required: true },
  rows: { type: Array, default: () => [] },
  selectedStrategyInfo: { type: Object, default: null },
  signalLabel: { type: Function, required: true },
  strategyOptions: { type: Array, default: () => [] },
  watchlist: { type: Array, default: () => [] },
  watchlistBacktestRunning: { type: Boolean, default: false },
});

defineEmits([
  "run-backtest",
  "open-watchlist-backtest-page",
]);
</script>

<template>
  <aside class="backtest-panel">
    <div class="backtest-header">
      <div>
        <span>策略回测</span>
        <strong>{{ selectedStrategyInfo?.name }}</strong>
      </div>
    </div>

    <div class="strategy-field">
      <label for="strategy">策略</label>
      <select id="strategy" v-model="selectedStrategy">
        <option
          v-for="strategy in strategyOptions"
          :key="strategy.id"
          :value="strategy.id"
        >
          {{ strategy.name }}
        </option>
      </select>
    </div>

    <button
      class="backtest-run"
      type="button"
      :disabled="!rows.length"
      @click="$emit('run-backtest')"
    >
      回测
    </button>
    <button
      class="backtest-run watchlist-backtest-button"
      type="button"
      :disabled="!watchlist.length || watchlistBacktestRunning"
      @click="$emit('open-watchlist-backtest-page')"
    >
      回测选中自选股
    </button>

    <p class="backtest-status">
      {{ backtestStatus || (rows.length ? "待回测" : "暂无K线数据") }}
    </p>

    <div class="backtest-metrics">
      <div>
        <span>收益率</span>
        <strong :class="performanceClass(backtestResult?.totalReturn)">
          {{ backtestResult ? formatSignedPercent(backtestResult.totalReturn) : "--" }}
        </strong>
      </div>
      <div>
        <span>最大回撤</span>
        <strong class="drawdown-value">
          {{ backtestResult ? formatPlainPercent(backtestResult.maxDrawdown) : "--" }}
        </strong>
      </div>
      <div>
        <span>交易笔数</span>
        <strong>{{ backtestResult ? backtestResult.tradeCount : "--" }}</strong>
      </div>
      <div>
        <span>胜率</span>
        <strong>{{ backtestResult ? formatPlainPercent(backtestResult.winRate, 1) : "--" }}</strong>
      </div>
      <div>
        <span>买/卖点</span>
        <strong>
          {{
            backtestResult
              ? `${backtestResult.buyCount}/${backtestResult.sellCount}`
              : "--"
          }}
        </strong>
      </div>
      <div>
        <span>持仓日</span>
        <strong>{{ backtestResult ? backtestResult.holdingDays : "--" }}</strong>
      </div>
    </div>

    <div class="signal-list">
      <h3>买卖点</h3>
      <div v-if="!backtestResult" class="signal-empty">暂无结果</div>
      <div v-else-if="!displaySignals.length" class="signal-empty">未触发</div>
      <ol v-else>
        <li
          v-for="signal in displaySignals"
          :key="`${signal.type}-${signal.index}-${signal.date}`"
        >
          <span :class="['signal-badge', signal.type]">
            {{ signalLabel(signal.type) }}
          </span>
          <strong>{{ signal.date }}</strong>
          <span class="signal-price">{{ formatNumber(signal.price) }}</span>
        </li>
      </ol>
    </div>
  </aside>
</template>
