<script setup>
// Trade advice panel rendered from backend-generated advice payloads.

defineProps({
  advice: { type: Object, default: null },
  adviceActionClass: { type: String, default: "" },
});
</script>

<template>
  <section class="advice-panel">
    <div class="advice-header">
      <div>
        <span>当前操作建议</span>
        <strong v-if="advice" :class="adviceActionClass">
          {{ advice.actionText }}
        </strong>
        <strong v-else>当前无操作建议</strong>
      </div>
      <div v-if="advice" class="advice-score">
        <span>综合评分</span>
        <strong>{{ advice.score }}</strong>
      </div>
    </div>

    <div v-if="advice" class="advice-content">
      <div>
        <h2>理由说明</h2>
        <ul>
          <li v-for="reason in advice.reasons" :key="reason">{{ reason }}</li>
        </ul>
      </div>
      <div>
        <h2>风险提示</h2>
        <ul>
          <li v-for="risk in advice.risks" :key="risk">{{ risk }}</li>
        </ul>
      </div>
    </div>
  </section>
</template>
