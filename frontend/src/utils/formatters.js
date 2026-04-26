// Shared display and export formatting helpers for dashboard values.

export function formatNumber(value, digits = 2) {
  // Format numeric display values and keep missing values visually stable.
  if (value === null || value === undefined || Number.isNaN(value)) return "--";
  return Number(value).toFixed(digits);
}

export function formatSignedPercent(value, digits = 2) {
  // Format return percentages with a visible positive sign.
  if (value === null || value === undefined || Number.isNaN(value)) return "--";
  const number = Number(value);
  const sign = number > 0 ? "+" : "";
  return `${sign}${number.toFixed(digits)}%`;
}

export function formatPlainPercent(value, digits = 2) {
  // Format percentage metrics that do not need an explicit plus sign.
  if (value === null || value === undefined || Number.isNaN(value)) return "--";
  return `${Number(value).toFixed(digits)}%`;
}

export function valueForExport(value) {
  // Keep missing table cells blank when copying K-line data.
  if (value === null || value === undefined || Number.isNaN(value)) return "";
  return value;
}

export function numericValue(value) {
  // Convert row values into finite numbers for strategy calculations.
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
