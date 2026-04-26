<script setup>
// Sidebar panel for persisted watchlist rows and stock switching.

defineProps({
  currentSymbol: { type: String, default: "" },
  loading: { type: Boolean, default: false },
  watchlist: { type: Array, default: () => [] },
});

defineEmits(["refresh", "select", "remove"]);
</script>

<template>
  <aside class="watchlist-panel">
    <div class="watchlist-header">
      <h2>自选股票</h2>
      <button type="button" @click="$emit('refresh')">刷新</button>
    </div>
    <div v-if="loading" class="watchlist-empty">加载中</div>
    <div v-else-if="!watchlist.length" class="watchlist-empty">暂无自选</div>
    <ul v-else class="watchlist">
      <li
        v-for="item in watchlist"
        :key="item.symbol"
        :class="{ active: item.symbol === currentSymbol }"
      >
        <button type="button" class="watchlist-item" @click="$emit('select', item)">
          <strong>{{ item.name || item.symbol }}</strong>
          <span>{{ item.symbol }}</span>
        </button>
        <button
          type="button"
          class="remove-watch"
          aria-label="移除自选"
          @click="$emit('remove', item.symbol)"
        >
          ×
        </button>
      </li>
    </ul>
  </aside>
</template>
