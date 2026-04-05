import { useCallback, useEffect, useMemo, useState } from "react";
import { useJsonlSearch } from "./hooks/useJsonlSearch";

import { TopBar } from "./components/TopBar/TopBar";
import { SearchBar } from "./components/SearchBar/SearchBar";
import { JsonCard } from "./components/JsonCard/JsonCard";
import { EmptyState } from "./components/EmptyState/EmptyState";

import { getCounts, getPreview, getStats, reloadData, resetData } from "./utils/api";

import "./App.css";

const PREVIEW_LIMIT = 200;

function toUtcTimestampPayload(value) {
  const trimmed = (value ?? "").trim();
  if (!trimmed) return undefined;

  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(trimmed)) {
    return `${trimmed}:00Z`;
  }

  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(trimmed)) {
    return `${trimmed}Z`;
  }

  if (trimmed.endsWith("Z")) {
    return trimmed;
  }

  return `${trimmed}Z`;
}

export default function App() {
  const [stats, setStats] = useState(null);
  const [statsError, setStatsError] = useState("");
  const [counts, setCounts] = useState(null);
  const [countsLoading, setCountsLoading] = useState(false);
  const [previewRows, setPreviewRows] = useState([]);
  const [previewCursor, setPreviewCursor] = useState(0);
  const [previewHasMore, setPreviewHasMore] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewActive, setPreviewActive] = useState(false);
  const [error, setError] = useState("");
  const [actionState, setActionState] = useState({ reset: false, reload: false });

  const {
    filters,
    activeFilters,
    appliedFilters,
    hasFilters,
    hasActiveFilters,
    hasAppliedFilters,
    addFieldFilter,
    addTextFilter,
    addTimestampFilter,
    updateFilter,
    removeFilter,
    clearAllFilters,
    applyFilters,
  } = useJsonlSearch([], { timestampField: stats?.timestampField });

  const filterPayload = useMemo(() => {
    if (!appliedFilters || appliedFilters.length === 0) return { filters: [] };
    return {
      filters: appliedFilters.map((filter) => {
        if (filter.type === "field") {
          return {
            type: "field",
            fieldPath: filter.field,
            valueContains: filter.value,
          };
        }
        if (filter.type === "text") {
          return {
            type: "text",
            query: filter.query,
          };
        }
        return {
          type: "timestamp",
          fieldPath: filter.field,
          from: toUtcTimestampPayload(filter.from),
          to: toUtcTimestampPayload(filter.to),
        };
      }),
    };
  }, [appliedFilters]);

  const refreshStats = useCallback(async () => {
    try {
      const data = await getStats();
      setStats(data);
      setStatsError("");
    } catch (err) {
      setStatsError(err.message || "Failed to reach backend.");
    }
  }, []);

  const refreshCounts = useCallback(async () => {
    if (!stats?.filePath) {
      setCounts({ totalCount: 0, matchCount: 0 });
      return;
    }
    setCountsLoading(true);
    try {
      const data = await getCounts(filterPayload);
      setCounts(data);
      setError("");
    } catch (err) {
      setError(err.message || "Failed to fetch counts.");
    } finally {
      setCountsLoading(false);
    }
  }, [filterPayload, stats?.filePath]);

  useEffect(() => {
    refreshStats();
    const timer = setInterval(refreshStats, 3000);
    return () => clearInterval(timer);
  }, [refreshStats]);

  useEffect(() => {
    if (!stats?.filePath) return;
    refreshCounts();
  }, [stats?.totalCount, stats?.filePath, refreshCounts]);

  const handleSearch = useCallback(async () => {
    applyFilters();
    setPreviewRows([]);
    setPreviewCursor(0);
    setPreviewHasMore(false);
    setPreviewActive(false);
  }, [applyFilters]);

  const loadPreview = useCallback(
    async (reset = false) => {
      if (!stats?.filePath) return;
      setPreviewLoading(true);
      try {
        const data = await getPreview({
          ...filterPayload,
          cursorId: reset ? 0 : previewCursor,
          limit: PREVIEW_LIMIT,
        });
        const rows = data?.rows ?? [];
        setPreviewRows((prev) => (reset ? rows : [...prev, ...rows]));
        setPreviewHasMore(Boolean(data?.nextCursorId));
        setPreviewCursor(data?.nextCursorId ?? previewCursor);
        setPreviewActive(true);
        setError("");
      } catch (err) {
        setError(err.message || "Failed to load preview.");
      } finally {
        setPreviewLoading(false);
      }
    },
    [filterPayload, previewCursor, stats?.filePath]
  );

  const handleReset = useCallback(async () => {
    setActionState((prev) => ({ ...prev, reset: true }));
    try {
      await resetData();
      await refreshStats();
      await refreshCounts();
      setPreviewRows([]);
      setPreviewActive(false);
      setPreviewHasMore(false);
      setPreviewCursor(0);
    } catch (err) {
      setError(err.message || "Failed to reset data.");
    } finally {
      setActionState((prev) => ({ ...prev, reset: false }));
    }
  }, [refreshCounts, refreshStats]);

  const handleReload = useCallback(async () => {
    setActionState((prev) => ({ ...prev, reload: true }));
    try {
      await reloadData();
      await refreshStats();
      await refreshCounts();
      setPreviewRows([]);
      setPreviewActive(false);
      setPreviewHasMore(false);
      setPreviewCursor(0);
    } catch (err) {
      setError(err.message || "Failed to reload data.");
    } finally {
      setActionState((prev) => ({ ...prev, reload: false }));
    }
  }, [refreshCounts, refreshStats]);

  const totalCount = counts?.totalCount ?? stats?.totalCount ?? 0;
  const matchCount = counts?.matchCount ?? 0;
  const activeCount = activeFilters?.length ?? 0;

  const emptyVariant = statsError
    ? "backend-offline"
    : !stats?.filePath
    ? "no-file"
    : counts && totalCount === 0
    ? "empty-file"
    : counts && matchCount === 0 && hasActiveFilters
    ? "no-results"
    : null;

  return (
    <div className="app">
      <TopBar
        filePath={stats?.filePath}
        lastIngestedAt={stats?.lastIngestedAt}
        totalCount={stats?.totalCount ?? 0}
        parsedCount={stats?.parsedCount ?? 0}
        errorCount={stats?.errorCount ?? 0}
        timestampField={stats?.timestampField}
        onReload={handleReload}
        onReset={handleReset}
        resetLoading={actionState.reset}
        reloadLoading={actionState.reload}
      />

      <SearchBar
        filters={filters}
        totalCount={totalCount}
        matchCount={matchCount}
        hasFilters={hasFilters}
        hasActiveFilters={hasActiveFilters}
        hasAppliedFilters={hasAppliedFilters}
        activeCount={activeCount}
        loading={countsLoading}
        timestampField={stats?.timestampField}
        onAddFieldFilter={addFieldFilter}
        onAddTextFilter={addTextFilter}
        onAddTimestampFilter={addTimestampFilter}
        onUpdateFilter={updateFilter}
        onRemoveFilter={removeFilter}
        onClearAll={clearAllFilters}
        onSearch={handleSearch}
      />

      {error && (
        <div className="app-error" role="alert">
          ⚠ {error}
        </div>
      )}

      <main className="app-main">
        {emptyVariant ? (
          <EmptyState variant={emptyVariant} />
        ) : (
          <section className="preview">
            <div className="preview-header">
              <div>
                <h2>Preview</h2>
                <p>
                  Showing up to <strong>{PREVIEW_LIMIT}</strong> lines per page.
                </p>
              </div>
              <div className="preview-actions">
                <button
                  className="preview-btn"
                  onClick={() => loadPreview(true)}
                  disabled={previewLoading || (counts && matchCount === 0)}
                >
                  {previewActive ? "Reload Preview" : "Load Preview"}
                </button>
                {previewActive && previewHasMore && (
                  <button
                    className="preview-btn preview-btn--secondary"
                    onClick={() => loadPreview(false)}
                    disabled={previewLoading}
                  >
                    Load More
                  </button>
                )}
              </div>
            </div>

            {!previewActive && (
              <div className="preview-empty">
                Preview is off by default to keep the UI fast. Click "Load Preview"
                to fetch the first page.
              </div>
            )}

            {previewActive && previewRows.length === 0 && !previewLoading && (
              <div className="preview-empty">No preview rows available.</div>
            )}

            {previewRows.map((row) => (
              <JsonCard
                key={`${row.id}-${row.raw?.length || 0}`}
                entry={{
                  id: row.id,
                  lineNo: row.lineNo,
                  raw: row.raw,
                  parsed: row.parsed,
                  error: row.error,
                }}
              />
            ))}

            {previewLoading && (
              <div className="preview-loading">Loading preview...</div>
            )}
          </section>
        )}
      </main>

      <footer className="app-footer">
        <span>JSONL Live Viewer</span>
        {stats?.filePath && (
          <span className="app-footer-meta">
            Source: <strong>{stats.filePath}</strong>
          </span>
        )}
        {stats?.lastIngestedAt && (
          <span className="app-footer-time">
            Last ingest: {new Date(stats.lastIngestedAt).toLocaleTimeString()}
          </span>
        )}
      </footer>
    </div>
  );
}
