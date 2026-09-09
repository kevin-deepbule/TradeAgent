<script setup>
// Dashboard header with stock query, research, profile, and copy-mode actions.

import { computed, ref } from "vue";
import { readProfile, resolveAvatar } from "../services/profileStorage";

const queryInput = defineModel("queryInput", { type: String, default: "" });

defineProps({
  copySelectionMode: { type: Boolean, default: false },
  currentSymbol: { type: String, default: "" },
  defaultSymbol: { type: String, default: "" },
});

defineEmits([
  "submit-query",
  "add-watchlist",
  "set-default-stock",
  "toggle-copy-selection",
  "open-research",
  "open-profile",
]);

const headerProfile = ref(readProfile());

const profileDisplayName = computed(() => {
  // Show the configured username when the profile has one.
  return headerProfile.value.username.trim() || "个人中心";
});

const profileAvatar = computed(() => {
  // Resolve the saved avatar into either an uploaded image or built-in default.
  return resolveAvatar(headerProfile.value, profileDisplayName.value);
});

const profileAvatarStyle = computed(() => {
  // Apply built-in avatar colors without affecting uploaded images.
  if (profileAvatar.value.type === "image") return {};
  return {
    background: profileAvatar.value.background,
    color: profileAvatar.value.color,
  };
});
</script>

<template>
  <header class="topbar">
    <div class="topbar-title">
      <h1>A 股 K 线看板</h1>
      <p>后端每 60 秒刷新 AkShare 数据，前端自动同步最新缓存。</p>
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
      <button
        class="secondary-button"
        type="button"
        :disabled="!currentSymbol || currentSymbol === defaultSymbol"
        @click="$emit('set-default-stock')"
      >
        {{ currentSymbol === defaultSymbol ? "已默认" : "设为默认" }}
      </button>
      <button class="copy-button" type="button" @click="$emit('toggle-copy-selection')">
        {{ copySelectionMode ? "取消选区" : "复制K线数据" }}
      </button>
      <button class="research-entry-button" type="button" @click="$emit('open-research')">
        财报估值
      </button>
    </div>

    <button
      class="profile-button"
      type="button"
      :title="profileDisplayName"
      @click="$emit('open-profile')"
    >
      <img
        v-if="profileAvatar.type === 'image'"
        :alt="profileAvatar.alt"
        :src="profileAvatar.src"
      />
      <span v-else :style="profileAvatarStyle">{{ profileAvatar.text }}</span>
    </button>
  </header>
</template>
