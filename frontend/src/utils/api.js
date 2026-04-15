const API_BASE = import.meta.env.VITE_API_BASE || "/api";

async function apiFetch(path, options = {}) {
  const hasBody = options.body !== undefined;
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      ...(hasBody ? { "Content-Type": "application/json" } : {}),
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

async function apiFetchText(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!res.ok) {
    const message = await res.text();
    throw new Error(message || `Request failed (${res.status})`);
  }

  return res.text();
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

export function getCountStatus(requestHash) {
  return apiFetch(`/filters/count/${requestHash}`);
}

export function getPreview(payload) {
  return apiFetch("/filters/preview", {
    method: "POST",
    body: JSON.stringify(payload || {}),
  });
}

export function getEntry(id) {
  return apiFetch(`/entries/${id}`);
}

export function getEntryRaw(id) {
  return apiFetchText(`/entries/${id}/raw`, {
    headers: {
      Accept: "text/plain",
    },
  });
}

export function resetData() {
  return apiFetch("/admin/reset", { method: "POST" });
}

export function reloadData() {
  return apiFetch("/admin/reload", { method: "POST" });
}

export function pauseIngestion() {
  return apiFetch("/admin/pause", { method: "POST" });
}

export function resumeIngestion() {
  return apiFetch("/admin/resume", { method: "POST" });
}
