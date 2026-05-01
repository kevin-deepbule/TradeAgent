// Vue state and actions for stock data, watchlist, WebSocket updates, and copying rows.

import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { wsBase } from "../config";
import {
  createWatchlistItem,
  deleteWatchlistItem,
  fetchDefaultStock,
  fetchKline,
  fetchWatchlist,
  updateDefaultStock,
} from "../services/stockApi";
import { valueForExport } from "../utils/formatters";

export function useStockDashboard() {
  // Own the primary dashboard data flow and backend connection lifecycle.
  const queryInput = ref("000001");
  const currentQuery = ref("000001");
  const currentSymbol = ref("000001");
  const currentName = ref("");
  const defaultSymbol = ref("000001");
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

  let socket = null;
  let reconnectTimer = null;
  let socketSeq = 0;
  let rowsSignature = "";

  const latest = computed(() => rows.value.at(-1));

  const change = computed(() => {
    // Calculate the latest close-to-close change for the summary strip.
    if (rows.value.length < 2) return null;
    const last = rows.value.at(-1);
    const prev = rows.value.at(-2);
    if (!last?.close || !prev?.close) return null;
    const value = last.close - prev.close;
    const percent = (value / prev.close) * 100;
    return { value, percent };
  });

  const changeClass = computed(() => {
    // Pick the price color class according to the latest change direction.
    if (!change.value) return "";
    return change.value.value >= 0 ? "rise" : "fall";
  });

  const adviceActionClass = computed(() => {
    // Pick the advice color class according to the backend action signal.
    if (!advice.value?.action) return "";
    return `advice-${advice.value.action}`;
  });

  function applyPayload(payload) {
    // Apply a backend payload to UI state.
    const nextRows = payload.rows || [];
    const latestRow = nextRows.at(-1);
    const nextRowsSignature = [
      nextRows.length,
      latestRow?.date || "",
      latestRow?.open ?? "",
      latestRow?.close ?? "",
      latestRow?.high ?? "",
      latestRow?.low ?? "",
      latestRow?.volume ?? "",
    ].join("|");
    if (nextRowsSignature !== rowsSignature) {
      rows.value = nextRows;
      rowsSignature = nextRowsSignature;
    }
    if (payload.symbol) currentSymbol.value = payload.symbol;
    currentName.value = payload.name || "";
    updatedAt.value = payload.updatedAt || "";
    advice.value = payload.advice || null;
    error.value = payload.error || "";
    status.value = payload.error ? "接口异常" : "实时连接";
  }

  async function loadWatchlist() {
    // Fetch the persisted watchlist for the sidebar.
    watchlistLoading.value = true;
    try {
      watchlist.value = await fetchWatchlist();
    } catch {
      copyStatus.value = "自选列表加载失败";
    } finally {
      watchlistLoading.value = false;
    }
  }

  async function fetchOnce(query) {
    // Fetch a single K-line payload as a fallback when WebSocket is unavailable.
    applyPayload(await fetchKline(query));
  }

  function connect(query) {
    // Open a WebSocket for live payloads and reconnect the latest query on close.
    clearTimeout(reconnectTimer);
    const seq = ++socketSeq;
    if (socket) socket.close();

    status.value = "连接中";
    socket = new WebSocket(`${wsBase}/ws/stocks/${encodeURIComponent(query)}`);

    socket.onmessage = (event) => {
      applyPayload(JSON.parse(event.data));
    };
    socket.onerror = () => {
      status.value = "连接异常";
      fetchOnce(query).catch(() => {});
    };
    socket.onclose = () => {
      if (seq !== socketSeq) return;
      status.value = "已断开，重连中";
      reconnectTimer = window.setTimeout(() => connect(currentQuery.value), 3000);
    };
  }

  function activateQuery(query) {
    // Switch to a query and reconnect immediately when the value is unchanged.
    queryInput.value = query;
    if (currentQuery.value === query) {
      connect(query);
      return;
    }
    currentQuery.value = query;
  }

  async function loadDefaultStock() {
    // Load the persisted default stock before opening the first live connection.
    try {
      const defaultStock = await fetchDefaultStock();
      const symbol = defaultStock.symbol || "000001";
      defaultSymbol.value = symbol;
      currentSymbol.value = symbol;
      activateQuery(symbol);
    } catch {
      copyStatus.value = "默认股票加载失败，使用 000001";
      activateQuery(currentQuery.value);
    }
  }

  function submitQuery() {
    // Promote the search box value into the active query.
    const nextQuery = queryInput.value.trim();
    if (!nextQuery) return;
    stopCopySelection();
    copyStatus.value = "";
    currentQuery.value = nextQuery;
  }

  async function setCurrentAsDefaultStock() {
    // Persist the currently displayed stock as the default dashboard stock.
    if (!currentSymbol.value) return;
    try {
      const saved = await updateDefaultStock(currentSymbol.value);
      defaultSymbol.value = saved.symbol || currentSymbol.value;
      copyStatus.value = "已设为默认显示";
      window.setTimeout(() => {
        copyStatus.value = "";
      }, 1800);
    } catch (exc) {
      copyStatus.value = exc.message || "设置默认股票失败";
    }
  }

  async function addToWatchlist(query = queryInput.value) {
    // Resolve and persist a stock into the watchlist.
    const nextQuery = query.trim();
    if (!nextQuery) return;

    try {
      await createWatchlistItem(nextQuery);
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
      await deleteWatchlistItem(symbol);
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
  }

  function stopCopySelection() {
    // Leave copy-selection mode and clear the selected starting point.
    copySelectionMode.value = false;
    copyStartIndex.value = null;
  }

  function pickCopyStart(index) {
    // Save the first chart click when choosing a copy date range.
    copyStartIndex.value = index;
    copyStatus.value = `起始日期 ${rows.value[index].date}`;
  }

  async function writeRowsToClipboard(selectedRows) {
    // Copy selected K-line rows as tab-separated text for spreadsheets.
    if (!selectedRows.length) {
      copyStatus.value = "区间无数据";
      return;
    }

    const header = [
      "日期",
      "开盘",
      "收盘",
      "最高",
      "最低",
      "量能",
      "MA5",
      "MA20",
      "MA60",
    ];
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

  function copyRowsInRange(startIndex, endIndex) {
    // Copy an inclusive row range selected from the chart.
    const selectedRows = rows.value.slice(startIndex, endIndex + 1);
    writeRowsToClipboard(selectedRows);
  }

  watch(currentQuery, (query) => connect(query));

  onMounted(() => {
    loadWatchlist();
    loadDefaultStock();
  });

  onBeforeUnmount(() => {
    socketSeq += 1;
    clearTimeout(reconnectTimer);
    if (socket) socket.close();
  });

  return {
    queryInput,
    currentQuery,
    currentSymbol,
    currentName,
    defaultSymbol,
    rows,
    watchlist,
    watchlistLoading,
    updatedAt,
    advice,
    status,
    error,
    copyStatus,
    copySelectionMode,
    copyStartIndex,
    latest,
    change,
    changeClass,
    adviceActionClass,
    loadWatchlist,
    submitQuery,
    addToWatchlist,
    setCurrentAsDefaultStock,
    removeFromWatchlist,
    selectWatchlistStock,
    startCopySelection,
    stopCopySelection,
    pickCopyStart,
    copyRowsInRange,
  };
}
