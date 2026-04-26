<script setup>
// Dashboard header with stock query, watchlist add, and copy-mode actions.

const queryInput = defineModel("queryInput", { type: String, default: "" });

defineProps({
  copySelectionMode: { type: Boolean, default: false },
});

defineEmits(["submit-query", "add-watchlist", "toggle-copy-selection"]);
</script>

<template>
  <header class="topbar">
    <div>
      <h1>A 股 K 线看板</h1>
      <p>后端每 5 秒刷新 AkShare 数据，前端自动同步最新缓存。</p>
    </div>
    <div class="header-actions">
      <form class="symbol-form" @submit.prevent="$emit('submit-query')">
        <label for="symbol">代码/名称</label>
        <input id="symbol" v-model="queryInput" placeholder="000001 或 平安银行" />
        <button type="submit">查询</button>
      </form>
      <button class="secondary-button" type="button" @click="$emit('add-watchlist')">
        加入自选
      </button>
      <button class="copy-button" type="button" @click="$emit('toggle-copy-selection')">
        {{ copySelectionMode ? "取消选区" : "复制K线数据" }}
      </button>
    </div>
  </header>
</template>
