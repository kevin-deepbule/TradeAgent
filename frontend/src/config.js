// Frontend runtime configuration derived from Vite environment variables.

export const apiBase = import.meta.env.VITE_API_BASE || "http://localhost:8001";

export const wsBase =
  import.meta.env.VITE_WS_BASE ||
  apiBase.replace(/^http/, "ws").replace(/\/$/, "");
