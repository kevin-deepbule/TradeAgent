<script setup>
// Summary card strip for current stock, latest price, status, and update time.

import { formatNumber } from "../utils/formatters";

defineProps({
  currentName: { type: String, default: "" },
  currentSymbol: { type: String, default: "" },
  latest: { type: Object, default: null },
  change: { type: Object, default: null },
  changeClass: { type: String, default: "" },
  updatedAt: { type: String, default: "" },
  statusText: { type: String, default: "" },
});
</script>

<template>
  <section class="summary">
    <div>
      <span>当前股票</span>
      <strong>{{ currentName ? `${currentName} ${currentSymbol}` : currentSymbol }}</strong>
    </div>
    <div>
      <span>最新收盘</span>
      <strong>{{ formatNumber(latest?.close) }}</strong>
    </div>
    <div>
      <span>涨跌</span>
      <strong :class="changeClass">
        {{ change ? formatNumber(change.value) : "--" }}
        <small>{{ change ? `${formatNumber(change.percent)}%` : "" }}</small>
      </strong>
    </div>
    <div>
      <span>更新时间</span>
      <strong>{{ updatedAt || "--" }}</strong>
    </div>
    <div>
      <span>状态</span>
      <strong>{{ statusText }}</strong>
    </div>
  </section>
</template>
