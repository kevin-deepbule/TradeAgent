// Vue lifecycle wrapper for the ECharts K-line chart instance.

import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import { makeKlineChartOption } from "../charts/klineChartOption";

export function useKlineChart({
  rows,
  copySelectionMode,
  copyStartIndex,
  backtestResult,
  onChartClick,
}) {
  // Create, update, resize, and dispose the K-line chart instance.
  const chartEl = ref(null);
  let chart = null;

  function renderChart() {
    // Initialize or update the chart instance after data/layout changes.
    if (!chartEl.value) return;
    if (!chart) chart = echarts.init(chartEl.value);
    chart.resize();
    chart.setOption(
      makeKlineChartOption({
        rows: rows.value,
        copySelectionMode: copySelectionMode.value,
        copyStartIndex: copyStartIndex.value,
        backtestResult: backtestResult.value,
      }),
      true,
    );
  }

  function scheduleRender() {
    // Wait for Vue DOM updates before asking ECharts to measure the container.
    nextTick(renderChart);
  }

  watch([rows, copySelectionMode, copyStartIndex, backtestResult], scheduleRender, {
    deep: true,
  });

  onMounted(() => {
    chart = echarts.init(chartEl.value);
    chart.on("click", onChartClick);
    renderChart();
    window.addEventListener("resize", renderChart);
  });

  onBeforeUnmount(() => {
    window.removeEventListener("resize", renderChart);
    if (chart) chart.off("click", onChartClick);
    if (chart) chart.dispose();
  });

  return {
    chartEl,
    renderChart,
  };
}
