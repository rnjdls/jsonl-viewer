const API_BASE = import.meta.env.VITE_API_BASE || "/api";

async function apiFetch(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!res.ok) {
    const message = await res.text();
    throw new Error(message || `Request failed (${res.status})`);
  }

  if (res.status === 204) return null;
  return res.json();
}

export function getStats() {
  return apiFetch("/stats");
}

export function getCounts(payload) {
  return apiFetch("/filters/count", {
    method: "POST",
    body: JSON.stringify(payload || {}),
  });
}

export function getPreview(payload) {
  return apiFetch("/filters/preview", {
    method: "POST",
    body: JSON.stringify(payload || {}),
  });
}

export function resetData() {
  return apiFetch("/admin/reset", { method: "POST" });
}

export function reloadData() {
  return apiFetch("/admin/reload", { method: "POST" });
}
