// Vue lifecycle wrapper for the ECharts K-line chart instance.

import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import { makeKlineChartOption } from "../charts/klineChartOption";

export function useKlineChart({
  chartKey,
  rows,
  copySelectionMode,
  copyStartIndex,
  backtestResult,
  onChartClick,
}) {
  // Create, update, resize, and dispose the K-line chart instance.
  const chartEl = ref(null);
  let chart = null;
  let zoomRange = null;
  let suppressZoomEvent = false;

  function dateForZoomValue(value, dates) {
    // Convert ECharts dataZoom values back to date category labels.
    if (dates.includes(value)) return value;
    const index = Number(value);
    if (!Number.isFinite(index)) return null;
    return dates[Math.max(0, Math.min(dates.length - 1, Math.round(index)))] || null;
  }

  function dateForZoomPercent(percent, dates) {
    // Convert ECharts percentage zoom bounds into nearest date labels.
    if (!Number.isFinite(percent) || !dates.length) return null;
    const index = Math.round((percent / 100) * (dates.length - 1));
    return dates[Math.max(0, Math.min(dates.length - 1, index))] || null;
  }

  function currentZoomRange() {
    // Read the active chart dataZoom window as stable date labels.
    if (!chart) return null;
    const dates = rows.value.map((item) => item.date);
    const range = chart.getOption()?.dataZoom?.[0];
    if (!range || !dates.length) return null;

    const startValue =
      dateForZoomValue(range.startValue, dates) ??
      dateForZoomPercent(Number(range.start), dates);
    const endValue =
      dateForZoomValue(range.endValue, dates) ??
      dateForZoomPercent(Number(range.end), dates);
    if (!startValue || !endValue) return null;
    return { startValue, endValue };
  }

  function handleDataZoom() {
    // Persist manual chart navigation so live refreshes keep the same viewport.
    if (suppressZoomEvent) return;
    zoomRange = currentZoomRange();
  }

  function renderChart() {
    // Initialize or update the chart instance after data/layout changes.
    if (!chartEl.value) return;
    if (!chart) chart = echarts.init(chartEl.value);
    chart.resize();
    suppressZoomEvent = true;
    chart.setOption(
      makeKlineChartOption({
        rows: rows.value,
        copySelectionMode: copySelectionMode.value,
        copyStartIndex: copyStartIndex.value,
        backtestResult: backtestResult.value,
        zoomRange,
      }),
      true,
    );
    window.setTimeout(() => {
      suppressZoomEvent = false;
    }, 0);
  }

  function scheduleRender() {
    // Wait for Vue DOM updates before asking ECharts to measure the container.
    nextTick(renderChart);
  }

  watch([rows, copySelectionMode, copyStartIndex, backtestResult], scheduleRender, {
    deep: true,
  });

  watch(chartKey, () => {
    // Reset the saved viewport when switching to another stock.
    zoomRange = null;
    scheduleRender();
  });

  onMounted(() => {
    chart = echarts.init(chartEl.value);
    chart.on("click", onChartClick);
    chart.on("dataZoom", handleDataZoom);
    renderChart();
    window.addEventListener("resize", renderChart);
  });

  onBeforeUnmount(() => {
    window.removeEventListener("resize", renderChart);
    if (chart) chart.off("click", onChartClick);
    if (chart) chart.off("dataZoom", handleDataZoom);
    if (chart) chart.dispose();
  });

  return {
    chartEl,
    renderChart,
  };
}
