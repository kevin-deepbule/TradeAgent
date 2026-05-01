// Vue lifecycle wrapper for the ECharts K-line chart instance.

import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import { makeKlineChartOption } from "../charts/klineChartOption";

export function useKlineChart({
  chartKey,
  rows,
  copySelectionMode,
  copyStartIndex,
  selectedKlineIndex,
  backtestResult,
  onChartClick,
}) {
  // Create, update, resize, and dispose the K-line chart instance.
  const chartEl = ref(null);
  let chart = null;
  let zoomRange = null;
  let activePointerIndex = null;
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

  function indexFromDateValue(value, dates) {
    // Convert an ECharts category value into a bounded row index.
    if (!dates.length) return null;
    if (dates.includes(value)) return dates.indexOf(value);
    const index = Number(value);
    if (!Number.isFinite(index)) return null;
    return Math.max(0, Math.min(dates.length - 1, Math.round(index)));
  }

  function pointerRowIndex(event) {
    // Read the nearest K-line index from a double-click anywhere inside the grids.
    if (!chart) return null;
    const offsetX = event.offsetX ?? event.event?.offsetX;
    const offsetY = event.offsetY ?? event.event?.offsetY;
    if (!Number.isFinite(offsetX) || !Number.isFinite(offsetY)) return null;
    const point = [offsetX, offsetY];
    const inMainGrid = chart.containPixel({ gridIndex: 0 }, point);
    const inVolumeGrid = chart.containPixel({ gridIndex: 1 }, point);
    if (!inMainGrid && !inVolumeGrid) return null;

    const dates = rows.value.map((item) => item.date);
    const finder = { xAxisIndex: inVolumeGrid ? 1 : 0 };
    const converted = chart.convertFromPixel(finder, point);
    const categoryValue = Array.isArray(converted) ? converted[0] : converted;
    return indexFromDateValue(categoryValue, dates);
  }

  function handleDataZoom() {
    // Persist manual chart navigation so live refreshes keep the same viewport.
    if (suppressZoomEvent) return;
    zoomRange = currentZoomRange();
  }

  function handleAxisPointerUpdate(params) {
    // Remember the exact date column where ECharts is drawing the hover guide.
    const dates = rows.value.map((item) => item.date);
    const axisInfo = (params.axesInfo || []).find(
      (item) => item.axisDim === "x" && [0, 1].includes(item.axisIndex),
    );
    const index = indexFromDateValue(axisInfo?.value, dates);
    if (index !== null) activePointerIndex = index;
  }

  function handlePointerDblClick(event) {
    // Select the K-line under the current ECharts hover guide line.
    const index = Number.isInteger(activePointerIndex)
      ? activePointerIndex
      : pointerRowIndex(event);
    if (index === null) return;
    selectedKlineIndex.value = index;
    chartEl.value?.focus();
  }

  function ensureKlineVisible(index, direction = 0) {
    // Pan the saved dataZoom window when keyboard navigation moves out of view.
    if (!chart || !Number.isInteger(index)) return;
    const dates = rows.value.map((item) => item.date);
    if (!dates.length || index < 0 || index >= dates.length) return;

    const range = currentZoomRange();
    const startIndex = indexFromDateValue(range?.startValue, dates);
    const endIndex = indexFromDateValue(range?.endValue, dates);
    if (startIndex === null || endIndex === null) return;
    if (index >= startIndex && index <= endIndex) return;

    const visibleCount = Math.max(endIndex - startIndex + 1, 1);
    let nextStart = startIndex;
    let nextEnd = endIndex;
    if (direction < 0 || index < startIndex) {
      nextStart = index;
      nextEnd = Math.min(dates.length - 1, nextStart + visibleCount - 1);
    } else {
      nextEnd = index;
      nextStart = Math.max(0, nextEnd - visibleCount + 1);
    }

    zoomRange = {
      startValue: dates[nextStart],
      endValue: dates[nextEnd],
    };
    [0, 1].forEach((dataZoomIndex) => {
      chart.dispatchAction({
        type: "dataZoom",
        dataZoomIndex,
        ...zoomRange,
      });
    });
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
        selectedKlineIndex: selectedKlineIndex.value,
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

  watch(
    [rows, copySelectionMode, copyStartIndex, selectedKlineIndex, backtestResult],
    scheduleRender,
    {
      deep: true,
    },
  );

  watch(chartKey, () => {
    // Reset the saved viewport when switching to another stock.
    zoomRange = null;
    activePointerIndex = null;
    scheduleRender();
  });

  onMounted(() => {
    chart = echarts.init(chartEl.value);
    chart.on("click", onChartClick);
    chart.getZr().on("dblclick", handlePointerDblClick);
    chart.on("updateAxisPointer", handleAxisPointerUpdate);
    chart.on("dataZoom", handleDataZoom);
    renderChart();
    window.addEventListener("resize", renderChart);
  });

  onBeforeUnmount(() => {
    window.removeEventListener("resize", renderChart);
    if (chart) chart.off("click", onChartClick);
    if (chart) chart.getZr().off("dblclick", handlePointerDblClick);
    if (chart) chart.off("updateAxisPointer", handleAxisPointerUpdate);
    if (chart) chart.off("dataZoom", handleDataZoom);
    if (chart) chart.dispose();
  });

  return {
    chartEl,
    ensureKlineVisible,
    renderChart,
  };
}
