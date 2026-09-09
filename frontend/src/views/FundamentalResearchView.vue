<script setup>
// Financial-report valuation workflow with industry selection and traceable results.

import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import {
  createValuationRun,
  fetchResearchIndustries,
  fetchValuationRun,
} from "../services/stockApi";

defineEmits(["back"]);

const industries = ref([]);
const selectedIndustryCodes = ref([]);
const industrySearch = ref("");
const reportPeriod = ref(latestCompletedQuarterEnd());
const asOfDate = ref(localDateText(new Date()));
const loadingIndustries = ref(false);
const running = ref(false);
const error = ref("");
const run = ref(null);
const selectedSymbol = ref("");
let pollTimer = null;

const industryGroups = computed(() => {
  // Group the filtered leaf industries by parent for compact multi-selection.
  const query = industrySearch.value.trim().toLowerCase();
  const groups = new Map();
  for (const industry of industries.value) {
    const searchable = `${industry.name || ""} ${industry.parentName || ""} ${industry.code || ""}`.toLowerCase();
    if (query && !searchable.includes(query)) continue;
    const parent = industry.parentName || "其他";
    if (!groups.has(parent)) groups.set(parent, []);
    groups.get(parent).push(industry);
  }
  return Array.from(groups.entries()).map(([parent, rows]) => ({ parent, rows }));
});

const results = computed(() => run.value?.results || []);

const selectedResult = computed(() => {
  // Keep the detail panel aligned with the selected row or current first result.
  return (
    results.value.find((item) => item.symbol === selectedSymbol.value) ||
    results.value[0] ||
    null
  );
});

function localDateText(value) {
  // Format a browser-local date without UTC day shifting.
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function latestCompletedQuarterEnd() {
  // Pick the latest completed statutory quarter for the initial form value.
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth() + 1;
  if (month <= 3) return `${year - 1}-12-31`;
  if (month <= 6) return `${year}-03-31`;
  if (month <= 9) return `${year}-06-30`;
  return `${year}-09-30`;
}

async function loadIndustries() {
  // Fetch the current Shenwan industry tree through the backend boundary.
  loadingIndustries.value = true;
  error.value = "";
  try {
    industries.value = await fetchResearchIndustries();
  } catch (cause) {
    error.value = cause.message || "申万行业加载失败";
  } finally {
    loadingIndustries.value = false;
  }
}

function toggleIndustry(code, checked) {
  // Add or remove one leaf industry while retaining the user's selection order.
  if (checked && !selectedIndustryCodes.value.includes(code)) {
    selectedIndustryCodes.value.push(code);
  } else if (!checked) {
    selectedIndustryCodes.value = selectedIndustryCodes.value.filter(
      (selected) => selected !== code,
    );
  }
}

function selectVisibleIndustries() {
  // Select the currently filtered industry leaves up to the backend limit.
  const visibleCodes = industryGroups.value.flatMap((group) =>
    group.rows.map((industry) => industry.code),
  );
  selectedIndustryCodes.value = Array.from(
    new Set([...selectedIndustryCodes.value, ...visibleCodes]),
  ).slice(0, 20);
}

function clearIndustries() {
  // Clear every selected leaf industry before a new research scope is chosen.
  selectedIndustryCodes.value = [];
}

async function startRun() {
  // Queue a point-in-time valuation task and begin polling its progress.
  if (!selectedIndustryCodes.value.length) {
    error.value = "请至少选择一个申万三级行业";
    return;
  }
  running.value = true;
  error.value = "";
  run.value = null;
  selectedSymbol.value = "";
  clearPollTimer();
  try {
    run.value = await createValuationRun({
      industryCodes: selectedIndustryCodes.value,
      reportPeriod: reportPeriod.value,
      asOfDate: asOfDate.value,
      marketScope: "MAIN_BOARD",
    });
    await pollRun();
  } catch (cause) {
    running.value = false;
    error.value = cause.message || "财报估值任务创建失败";
  }
}

async function pollRun() {
  // Poll until the persisted background task reaches a terminal state.
  if (!run.value?.id) return;
  try {
    run.value = await fetchValuationRun(run.value.id);
    if (run.value.status === "COMPLETED") {
      running.value = false;
      selectedSymbol.value = run.value.results?.[0]?.symbol || "";
      return;
    }
    if (run.value.status === "FAILED") {
      running.value = false;
      error.value = run.value.error || "财报估值任务失败";
      return;
    }
    pollTimer = window.setTimeout(pollRun, 1500);
  } catch (cause) {
    running.value = false;
    error.value = cause.message || "财报估值任务读取失败";
  }
}

function clearPollTimer() {
  // Stop any pending task poll before leaving or starting another run.
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function formatYi(value) {
  // Format yuan-denominated financial values in hundred-million-yuan units.
  const number = Number(value);
  return Number.isFinite(number) ? `${(number / 100000000).toFixed(2)} 亿` : "--";
}

function formatNumber(value, digits = 2) {
  // Format finite decimal metrics and preserve missing-value visibility.
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(digits) : "--";
}

function formatPercent(value) {
  // Format ratio fields such as growth and upside as signed percentages.
  const number = Number(value);
  if (!Number.isFinite(number)) return "--";
  const sign = number > 0 ? "+" : "";
  return `${sign}${(number * 100).toFixed(1)}%`;
}

function scoreClass(score) {
  // Color high, medium, and low opportunity scores consistently.
  const value = Number(score);
  if (value >= 70) return "research-score-high";
  if (value < 45) return "research-score-low";
  return "research-score-mid";
}

function valuationClass(band) {
  // Map valuation bands to restrained research-only visual emphasis.
  if (["显著低估", "低估"].includes(band)) return "research-value-low";
  if (["偏贵", "高估"].includes(band)) return "research-value-high";
  return "research-value-fair";
}

function fairPeSourceText(source) {
  // Translate the adopted PE source into a concise Chinese audit label.
  if (source === "DEEPSEEK") return "DeepSeek";
  if (source === "RULE_FALLBACK") return "规则回退";
  return "规则基线";
}

onMounted(loadIndustries);
onBeforeUnmount(clearPollTimer);
</script>

<template>
  <section class="research-page">
    <header class="research-page-header">
      <button class="batch-back-button" type="button" @click="$emit('back')">
        返回
      </button>
      <div>
        <span>基本面研究</span>
        <h1>财报估值智能体</h1>
        <p>程序计算规则 PE 基线，DeepSeek 逐家公司复核合理 PE，异常结果自动回退。</p>
      </div>
    </header>

    <div v-if="error" class="research-error">{{ error }}</div>

    <div class="research-layout">
      <aside class="research-control-card">
        <div class="research-field">
          <label for="industry-search">申万三级行业</label>
          <input
            id="industry-search"
            v-model="industrySearch"
            placeholder="搜索医药、宠物、轮胎…"
          />
        </div>

        <div class="research-selection-actions">
          <span>已选 {{ selectedIndustryCodes.length }}/20</span>
          <div>
            <button type="button" @click="selectVisibleIndustries">选择筛选项</button>
            <button type="button" @click="clearIndustries">清空</button>
          </div>
        </div>

        <div class="research-industry-list">
          <p v-if="loadingIndustries">行业加载中…</p>
          <p v-else-if="!industryGroups.length">没有匹配行业</p>
          <section v-for="group in industryGroups" :key="group.parent">
            <h2>{{ group.parent }}</h2>
            <label v-for="industry in group.rows" :key="industry.code">
              <input
                type="checkbox"
                :checked="selectedIndustryCodes.includes(industry.code)"
                :disabled="
                  !selectedIndustryCodes.includes(industry.code) &&
                  selectedIndustryCodes.length >= 20
                "
                @change="toggleIndustry(industry.code, $event.target.checked)"
              />
              <span>{{ industry.name }}</span>
              <small>{{ industry.memberCount }} 家</small>
            </label>
          </section>
        </div>

        <div class="research-date-grid">
          <div class="research-field">
            <label for="research-report-period">报告期</label>
            <input id="research-report-period" v-model="reportPeriod" type="date" />
          </div>
          <div class="research-field">
            <label for="research-as-of">分析截止日</label>
            <input id="research-as-of" v-model="asOfDate" type="date" />
          </div>
        </div>

        <button
          class="research-run-button"
          type="button"
          :disabled="running || !selectedIndustryCodes.length"
          @click="startRun"
        >
          {{ running ? "分析中" : "开始财报估值" }}
        </button>

        <div v-if="run" class="research-progress">
          <div>
            <span>{{ run.message }}</span>
            <strong>{{ run.progress }}%</strong>
          </div>
          <progress :value="run.progress" max="100"></progress>
          <small>任务 {{ run.id }}</small>
        </div>
      </aside>

      <section class="research-result-card">
        <header class="research-result-header">
          <div>
            <span>综合预期差排序</span>
            <h2>{{ results.length ? `${results.length} 家公司` : "等待分析" }}</h2>
          </div>
          <p>同一规则版本可重复计算；缺失公司也保留状态。</p>
        </header>

        <div v-if="!results.length" class="research-empty">
          选择一个或多个申万三级行业，生成归母、扣非、全年预测和五档估值结果。
        </div>

        <div v-else class="research-table-wrap">
          <table class="research-table">
            <thead>
              <tr>
                <th>排名</th>
                <th>公司</th>
                <th>行业</th>
                <th>数据</th>
                <th>归母累计</th>
                <th>扣非累计</th>
                <th>全年预测</th>
                <th>预测 PE</th>
                <th>规则 PE</th>
                <th>DeepSeek PE</th>
                <th>最终 PE</th>
                <th>合理价格</th>
                <th>估值档</th>
                <th>综合分</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in results"
                :key="item.symbol"
                :class="{ active: selectedResult?.symbol === item.symbol }"
                @click="selectedSymbol = item.symbol"
              >
                <td>{{ item.rank }}</td>
                <td>
                  <strong>{{ item.name }}</strong>
                  <small>{{ item.symbol }}</small>
                </td>
                <td>{{ item.industryName }}</td>
                <td>
                  <strong>{{ item.sourceType }}</strong>
                  <small>{{ item.dataStatus }}</small>
                </td>
                <td>{{ formatYi(item.parentNetProfitYtd) }}</td>
                <td>{{ formatYi(item.deductNetProfitYtd) }}</td>
                <td>{{ formatYi(item.forecastBase) }}</td>
                <td>{{ formatNumber(item.forecastPe) }}</td>
                <td>{{ formatNumber(item.ruleFairPe) }}</td>
                <td>{{ formatNumber(item.deepSeekFairPe) }}</td>
                <td>{{ formatNumber(item.fairPe) }}</td>
                <td>{{ formatNumber(item.fairPrice) }}</td>
                <td>
                  <span :class="valuationClass(item.valuationBand)">
                    {{ item.valuationBand }}
                  </span>
                </td>
                <td>
                  <strong :class="scoreClass(item.opportunityScore)">
                    {{ formatNumber(item.opportunityScore, 1) }}
                  </strong>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <article v-if="selectedResult" class="research-detail">
          <header>
            <div>
              <span>{{ selectedResult.symbol }} · {{ selectedResult.industryName }}</span>
              <h2>{{ selectedResult.name }}</h2>
            </div>
            <div class="research-detail-score">
              <span>综合分</span>
              <strong :class="scoreClass(selectedResult.opportunityScore)">
                {{ formatNumber(selectedResult.opportunityScore, 1) }}
              </strong>
            </div>
          </header>

          <div class="research-metrics">
            <div><span>单季同比</span><strong>{{ formatPercent(selectedResult.singleQuarterYoY) }}</strong></div>
            <div><span>全年增长</span><strong>{{ formatPercent(selectedResult.forecastGrowth) }}</strong></div>
            <div><span>预期差</span><strong>{{ formatPercent(selectedResult.expectationGap) }}</strong></div>
            <div><span>估值空间</span><strong>{{ formatPercent(selectedResult.valuationUpside) }}</strong></div>
            <div><span>盈利质量</span><strong>{{ formatNumber(selectedResult.qualityScore, 1) }}</strong></div>
            <div><span>预测置信度</span><strong>{{ selectedResult.confidenceLevel }}</strong></div>
            <div><span>规则 PE</span><strong>{{ formatNumber(selectedResult.ruleFairPe) }}</strong></div>
            <div><span>DeepSeek PE</span><strong>{{ formatNumber(selectedResult.deepSeekFairPe) }}</strong></div>
            <div><span>最终 PE</span><strong>{{ formatNumber(selectedResult.fairPe) }}</strong></div>
            <div><span>采用来源</span><strong>{{ fairPeSourceText(selectedResult.fairPeSource) }}</strong></div>
          </div>

          <div class="research-ai-summary">
            <span>DeepSeek PE 复核 · {{ selectedResult.aiStatus }} · {{ fairPeSourceText(selectedResult.fairPeSource) }}</span>
            <p>{{ selectedResult.aiSummary }}</p>
          </div>

          <div class="research-detail-columns">
            <section>
              <h3>判断依据</h3>
              <ul><li v-for="reason in selectedResult.reasons" :key="reason">{{ reason }}</li></ul>
            </section>
            <section>
              <h3>风险提示</h3>
              <ul><li v-for="risk in selectedResult.risks" :key="risk">{{ risk }}</li></ul>
            </section>
            <section>
              <h3>证伪条件</h3>
              <ul><li v-for="condition in selectedResult.falsificationConditions" :key="condition">{{ condition }}</li></ul>
            </section>
            <section>
              <h3>数据证据</h3>
              <ul><li v-for="item in selectedResult.evidence" :key="item">{{ item }}</li></ul>
            </section>
          </div>
        </article>
      </section>
    </div>
  </section>
</template>
