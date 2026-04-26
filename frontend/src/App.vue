<script setup>
// Main dashboard component for watchlist, K-line chart, and trade advice state.
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";

const apiBase = import.meta.env.VITE_API_BASE || "http://localhost:8001";
const wsBase =
  import.meta.env.VITE_WS_BASE ||
  apiBase.replace(/^http/, "ws").replace(/\/$/, "");

const chartEl = ref(null);
const queryInput = ref("000001");
const currentQuery = ref("000001");
const currentSymbol = ref("000001");
const currentName = ref("");
const rows = ref([]);
const watchlist = ref([]);
const watchlistLoading = ref(false);
const updatedAt = ref("");
const advice = ref(null);
const status = ref("连接中");
const error = ref("");
const copyStatus = ref("");
const copySelectionMode = ref(false);
const copyStartIndex = ref(null);

let chart = null;
let socket = null;
let reconnectTimer = null;
let socketSeq = 0;

// Select the newest K-line row for summary metrics.
const latest = computed(() => rows.value.at(-1));

// Calculate the latest close-to-close change for the summary strip.
const change = computed(() => {
  if (rows.value.length < 2) return null;
  const last = rows.value.at(-1);
  const prev = rows.value.at(-2);
  if (!last?.close || !prev?.close) return null;
  const value = last.close - prev.close;
  const percent = (value / prev.close) * 100;
  return { value, percent };
});

// Pick the price color class according to the latest change direction.
const changeClass = computed(() => {
  if (!change.value) return "";
  return change.value.value >= 0 ? "rise" : "fall";
});

// Pick the advice color class according to the backend action signal.
const adviceActionClass = computed(() => {
  if (!advice.value?.action) return "";
  return `advice-${advice.value.action}`;
});

function formatNumber(value, digits = 2) {
  // Format numeric display values and keep missing values visually stable.
  if (value === null || value === undefined || Number.isNaN(value)) return "--";
  return Number(value).toFixed(digits);
}

function valueForExport(value) {
  // Keep missing table cells blank when copying K-line data.
  if (value === null || value === undefined || Number.isNaN(value)) return "";
  return value;
}

function chartOption() {
  // Build the full ECharts option from the current K-line rows.
  const dates = rows.value.map((item) => item.date);
  const candle = rows.value.map((item) => [
    item.open,
    item.close,
    item.low,
    item.high,
  ]);
  const ma5 = rows.value.map((item) => item.ma5);
  const ma20 = rows.value.map((item) => item.ma20);
  const ma60 = rows.value.map((item) => item.ma60);

  return {
    animation: false,
    color: ["#2f80ed", "#f2994a", "#6fcf97"],
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "cross" },
      borderWidth: 1,
      textStyle: { fontSize: 12 },
    },
    legend: {
      top: 8,
      data: ["日K", "MA5", "MA20", "MA60"],
    },
    grid: [
      { left: 56, right: 28, top: 48, height: "62%" },
      { left: 56, right: 28, top: "78%", height: "12%" },
    ],
    xAxis: [
      {
        type: "category",
        data: dates,
        boundaryGap: false,
        axisLine: { lineStyle: { color: "#9aa4b2" } },
        axisLabel: { color: "#526071" },
        min: "dataMin",
        max: "dataMax",
      },
      {
        type: "category",
        gridIndex: 1,
        data: dates,
        boundaryGap: false,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: "#d6dbe3" } },
      },
    ],
    yAxis: [
      {
        scale: true,
        splitLine: { lineStyle: { color: "#edf0f5" } },
        axisLabel: { color: "#526071" },
      },
      {
        scale: true,
        gridIndex: 1,
        splitNumber: 2,
        splitLine: { lineStyle: { color: "#edf0f5" } },
        axisLabel: { color: "#526071" },
      },
    ],
    dataZoom: [
      { type: "inside", xAxisIndex: [0, 1], start: 35, end: 100 },
      { type: "slider", xAxisIndex: [0, 1], bottom: 10, height: 22 },
    ],
    series: [
      {
        name: "日K",
        type: "candlestick",
        data: candle,
        markLine: {
          silent: true,
          symbol: "none",
          label: { show: false },
          lineStyle: {
            color: "#195fc9",
            type: "dashed",
            width: 1.5,
          },
          data:
            copySelectionMode.value && copyStartIndex.value !== null
              ? [{ xAxis: dates[copyStartIndex.value] }]
              : [],
        },
        itemStyle: {
          color: "#d64545",
          color0: "#1a9b68",
          borderColor: "#d64545",
          borderColor0: "#1a9b68",
        },
      },
      {
        name: "MA5",
        type: "line",
        data: ma5,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: "MA20",
        type: "line",
        data: ma20,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: "MA60",
        type: "line",
        data: ma60,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5 },
      },
      {
        name: "成交量",
        type: "bar",
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: rows.value.map((item) => item.volume),
        itemStyle: { color: "#9aa4b2" },
      },
    ],
  };
}

function renderChart() {
  // Initialize or update the chart instance after data/layout changes.
  if (!chartEl.value) return;
  if (!chart) chart = echarts.init(chartEl.value);
  chart.setOption(chartOption(), true);
}

function applyPayload(payload) {
  // Apply a backend payload to UI state and schedule a chart redraw.
  rows.value = payload.rows || [];
  if (payload.symbol) currentSymbol.value = payload.symbol;
  currentName.value = payload.name || "";
  updatedAt.value = payload.updatedAt || "";
  advice.value = payload.advice || null;
  error.value = payload.error || "";
  status.value = payload.error ? "接口异常" : "实时连接";
  nextTick(renderChart);
}

async function loadWatchlist() {
  // Fetch the persisted watchlist for the sidebar.
  watchlistLoading.value = true;
  try {
    const response = await fetch(`${apiBase}/api/watchlist`);
    watchlist.value = await response.json();
  } catch {
    copyStatus.value = "自选列表加载失败";
  } finally {
    watchlistLoading.value = false;
  }
}

async function fetchOnce(query) {
  // Fetch a single K-line payload as a fallback when WebSocket is unavailable.
  const response = await fetch(
    `${apiBase}/api/stocks/${encodeURIComponent(query)}/kline`,
  );
  applyPayload(await response.json());
}

function connect(query) {
  // Open a WebSocket for live payloads and reconnect the latest query on close.
  clearTimeout(reconnectTimer);
  const seq = ++socketSeq;
  if (socket) socket.close();

  status.value = "连接中";
  socket = new WebSocket(`${wsBase}/ws/stocks/${encodeURIComponent(query)}`);

  // Apply every pushed payload from the backend cache.
  socket.onmessage = (event) => {
    applyPayload(JSON.parse(event.data));
  };
  // Fall back to HTTP once if the live connection errors.
  socket.onerror = () => {
    status.value = "连接异常";
    fetchOnce(query).catch(() => {});
  };
  // Reconnect only for the most recent socket generation.
  socket.onclose = () => {
    if (seq !== socketSeq) return;
    status.value = "已断开，重连中";
    reconnectTimer = window.setTimeout(() => connect(currentQuery.value), 3000);
  };
}

function submitQuery() {
  // Promote the search box value into the active query.
  const nextQuery = queryInput.value.trim();
  if (!nextQuery) return;
  stopCopySelection();
  copyStatus.value = "";
  currentQuery.value = nextQuery;
}

async function addToWatchlist(query = queryInput.value) {
  // Resolve and persist a stock into the watchlist.
  const nextQuery = query.trim();
  if (!nextQuery) return;

  try {
    const response = await fetch(`${apiBase}/api/watchlist`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query: nextQuery }),
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.detail || "加入自选失败");
    }
    await loadWatchlist();
    copyStatus.value = "已加入自选";
    window.setTimeout(() => {
      copyStatus.value = "";
    }, 1800);
  } catch (exc) {
    copyStatus.value = exc.message || "加入自选失败";
  }
}

async function removeFromWatchlist(symbol) {
  // Delete a stock from the watchlist and refresh the sidebar.
  try {
    await fetch(`${apiBase}/api/watchlist/${encodeURIComponent(symbol)}`, {
      method: "DELETE",
    });
    await loadWatchlist();
    if (currentSymbol.value === symbol) {
      copyStatus.value = "已从自选移除";
      window.setTimeout(() => {
        copyStatus.value = "";
      }, 1800);
    }
  } catch {
    copyStatus.value = "移除失败";
  }
}

function selectWatchlistStock(item) {
  // Switch the dashboard to a stock selected from the sidebar.
  stopCopySelection();
  const query = item.symbol;
  queryInput.value = item.symbol;
  currentQuery.value = query;
}

function startCopySelection() {
  // Enter the chart date-range selection mode for copying rows.
  if (!rows.value.length) {
    copyStatus.value = "暂无数据";
    return;
  }
  copySelectionMode.value = true;
  copyStartIndex.value = null;
  copyStatus.value = "请选择起始日期";
  nextTick(renderChart);
}

function stopCopySelection() {
  // Leave copy-selection mode and clear the selected starting point.
  copySelectionMode.value = false;
  copyStartIndex.value = null;
  nextTick(renderChart);
}

async function writeRowsToClipboard(selectedRows) {
  // Copy selected K-line rows as tab-separated text for spreadsheets.
  if (!selectedRows.length) {
    copyStatus.value = "区间无数据";
    return;
  }

  const header = ["日期", "开盘", "收盘", "最高", "最低", "量能", "MA5", "MA20", "MA60"];
  const lines = selectedRows.map((item) =>
    [
      item.date,
      valueForExport(item.open),
      valueForExport(item.close),
      valueForExport(item.high),
      valueForExport(item.low),
      valueForExport(item.volume),
      valueForExport(item.ma5),
      valueForExport(item.ma20),
      valueForExport(item.ma60),
    ].join("\t"),
  );
  const text = [header.join("\t"), ...lines].join("\n");

  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
  }

  stopCopySelection();
  copyStatus.value = `已复制 ${selectedRows.length} 日`;
  window.setTimeout(() => {
    copyStatus.value = "";
  }, 2200);
}

function handleChartClick(params) {
  // Use two candlestick clicks to choose and copy an inclusive date range.
  if (!copySelectionMode.value) return;
  if (params.componentType !== "series" || params.seriesIndex !== 0) return;

  const clickedIndex = params.dataIndex;
  if (typeof clickedIndex !== "number") return;

  if (copyStartIndex.value === null) {
    copyStartIndex.value = clickedIndex;
    copyStatus.value = `起始日期 ${rows.value[clickedIndex].date}`;
    renderChart();
    return;
  }

  const startIndex = Math.min(copyStartIndex.value, clickedIndex);
  const endIndex = Math.max(copyStartIndex.value, clickedIndex);
  const selectedRows = rows.value.slice(startIndex, endIndex + 1);
  writeRowsToClipboard(selectedRows);
}

// Reconnect live data whenever the active query changes.
watch(currentQuery, (query) => connect(query));

// Initialize chart, data, and browser event listeners when mounted.
onMounted(() => {
  chart = echarts.init(chartEl.value);
  chart.on("click", handleChartClick);
  loadWatchlist();
  connect(currentQuery.value);
  window.addEventListener("resize", renderChart);
});

// Close sockets and dispose chart resources before the component unmounts.
onBeforeUnmount(() => {
  socketSeq += 1;
  clearTimeout(reconnectTimer);
  if (socket) socket.close();
  window.removeEventListener("resize", renderChart);
  if (chart) chart.off("click", handleChartClick);
  if (chart) chart.dispose();
});
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <h1>A 股 K 线看板</h1>
        <p>后端每 5 秒刷新 AkShare 数据，前端自动同步最新缓存。</p>
      </div>
      <div class="header-actions">
        <form class="symbol-form" @submit.prevent="submitQuery">
          <label for="symbol">代码/名称</label>
          <input id="symbol" v-model="queryInput" placeholder="000001 或 平安银行" />
          <button type="submit">查询</button>
        </form>
        <button class="secondary-button" type="button" @click="addToWatchlist()">
          加入自选
        </button>
        <button
          class="copy-button"
          type="button"
          @click="copySelectionMode ? stopCopySelection() : startCopySelection()"
        >
          {{ copySelectionMode ? "取消选区" : "复制K线数据" }}
        </button>
      </div>
    </header>

    <div class="main-layout">
      <aside class="watchlist-panel">
        <div class="watchlist-header">
          <h2>自选股票</h2>
          <button type="button" @click="loadWatchlist">刷新</button>
        </div>
        <div v-if="watchlistLoading" class="watchlist-empty">加载中</div>
        <div v-else-if="!watchlist.length" class="watchlist-empty">暂无自选</div>
        <ul v-else class="watchlist">
          <li
            v-for="item in watchlist"
            :key="item.symbol"
            :class="{ active: item.symbol === currentSymbol }"
          >
            <button type="button" class="watchlist-item" @click="selectWatchlistStock(item)">
              <strong>{{ item.name || item.symbol }}</strong>
              <span>{{ item.symbol }}</span>
            </button>
            <button
              type="button"
              class="remove-watch"
              aria-label="移除自选"
              @click="removeFromWatchlist(item.symbol)"
            >
              ×
            </button>
          </li>
        </ul>
      </aside>

      <div class="workspace">
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
            <strong>{{ copyStatus || status }}</strong>
          </div>
        </section>

        <section class="chart-panel">
          <div v-if="error" class="error-bar">{{ error }}</div>
          <div v-if="copySelectionMode" class="selection-tip">
            <span>
              {{
                copyStartIndex === null
                  ? "单击K线选择第一个日期。"
                  : `已选 ${rows[copyStartIndex]?.date}，再单击第二个日期后自动复制。`
              }}
            </span>
            <button type="button" @click="stopCopySelection">取消</button>
          </div>
          <div ref="chartEl" class="chart"></div>
        </section>

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
      </div>
    </div>
  </main>
</template>
