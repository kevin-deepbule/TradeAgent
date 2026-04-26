// Thin frontend API wrappers for stock K-line and watchlist endpoints.

import { apiBase } from "../config";

export async function fetchKline(query) {
  // Fetch a single K-line payload for a stock query.
  const response = await fetch(
    `${apiBase}/api/stocks/${encodeURIComponent(query)}/kline`,
  );
  return response.json();
}

export async function fetchWatchlist() {
  // Load the persisted watchlist from the backend.
  const response = await fetch(`${apiBase}/api/watchlist`);
  return response.json();
}

export async function createWatchlistItem(query) {
  // Resolve and save a stock into the backend watchlist.
  const response = await fetch(`${apiBase}/api/watchlist`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query }),
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.detail || "加入自选失败");
  }
  return response.json();
}

export async function deleteWatchlistItem(symbol) {
  // Remove a watchlist symbol from backend persistence.
  const response = await fetch(
    `${apiBase}/api/watchlist/${encodeURIComponent(symbol)}`,
    { method: "DELETE" },
  );
  return response.json().catch(() => ({}));
}
