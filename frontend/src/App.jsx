import { useCallback, useEffect, useMemo, useState } from "react";
import { useJsonlSearch } from "./hooks/useJsonlSearch";

import { TopBar } from "./components/TopBar/TopBar";
import { SearchBar } from "./components/SearchBar/SearchBar";
import { JsonCard } from "./components/JsonCard/JsonCard";
import { EmptyState } from "./components/EmptyState/EmptyState";

import {
  getCounts,
  getEntry,
  getEntryRaw,
  getPreview,
  getStats,
  reloadData,
  resetData,
} from "./utils/api";

import "./App.css";

const DEFAULT_PREVIEW_LIMIT = 10;
const DEFAULT_SORT_BY = "timestamp";
const DEFAULT_SORT_DIR = "desc";

function toTimestampPayload(value) {
  const trimmed = (value ?? "").trim();
  return trimmed || undefined;
}

export default function App() {
  const [stats, setStats] = useState(null);
  const [statsError, setStatsError] = useState("");
  const [counts, setCounts] = useState(null);
  const [countsLoading, setCountsLoading] = useState(false);
  const [previewRows, setPreviewRows] = useState([]);
  const [previewLimit, setPreviewLimit] = useState(DEFAULT_PREVIEW_LIMIT);
  const [previewSortBy, setPreviewSortBy] = useState(DEFAULT_SORT_BY);
  const [previewSortDir, setPreviewSortDir] = useState(DEFAULT_SORT_DIR);
  const [cursorHistory, setCursorHistory] = useState([null]);
  const [pageIndex, setPageIndex] = useState(0);
  const [nextCursor, setNextCursor] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewActive, setPreviewActive] = useState(false);
  const [entryDetailsById, setEntryDetailsById] = useState({});
  const [entryDetailsLoadingById, setEntryDetailsLoadingById] = useState({});
  const [entryRawById, setEntryRawById] = useState({});
  const [entryRawLoadingById, setEntryRawLoadingById] = useState({});
  const [expandedById, setExpandedById] = useState({});
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

  const resetPreviewState = useCallback(() => {
    setPreviewRows([]);
    setCursorHistory([null]);
    setPageIndex(0);
    setNextCursor(null);
    setPreviewActive(false);
    setEntryDetailsById({});
    setEntryDetailsLoadingById({});
    setEntryRawById({});
    setEntryRawLoadingById({});
    setExpandedById({});
  }, []);

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
          from: toTimestampPayload(filter.from),
          to: toTimestampPayload(filter.to),
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
    resetPreviewState();
  }, [applyFilters, resetPreviewState]);

  const requestPreviewPage = useCallback(
    async (cursor) =>
      getPreview({
        ...filterPayload,
        cursor,
        sortBy: previewSortBy,
        sortDir: previewSortDir,
        limit: previewLimit,
      }),
    [filterPayload, previewLimit, previewSortBy, previewSortDir]
  );

  const fetchPreviewPage = useCallback(
    async (cursor, targetPageIndex, historySeed) => {
      if (!stats?.filePath) return;
      setPreviewLoading(true);
      try {
        const data = await requestPreviewPage(cursor);
        const rows = data?.rows ?? [];
        const receivedNextCursor = data?.nextCursor ?? null;
        setPreviewRows(rows);
        setPreviewActive(true);
        setPageIndex(targetPageIndex);
        setNextCursor(receivedNextCursor);
        setCursorHistory((prev) => {
          const baseHistory = historySeed ?? prev;
          const nextHistory = [...baseHistory];
          nextHistory[targetPageIndex] = cursor ?? null;
          if (receivedNextCursor) {
            nextHistory[targetPageIndex + 1] = receivedNextCursor;
          } else {
            nextHistory.length = targetPageIndex + 1;
          }
          return nextHistory;
        });
        setError("");
      } catch (err) {
        setError(err.message || "Failed to load preview.");
      } finally {
        setPreviewLoading(false);
      }
    },
    [requestPreviewPage, stats?.filePath]
  );

  const handleLoadPreview = useCallback(async () => {
    await fetchPreviewPage(null, 0);
  }, [fetchPreviewPage]);

  const handleNextPage = useCallback(async () => {
    if (!previewActive || !nextCursor) return;
    await fetchPreviewPage(nextCursor, pageIndex + 1);
  }, [fetchPreviewPage, nextCursor, pageIndex, previewActive]);

  const handlePrevPage = useCallback(async () => {
    if (!previewActive || pageIndex === 0) return;
    const prevCursor = cursorHistory[pageIndex - 1] ?? null;
    await fetchPreviewPage(prevCursor, pageIndex - 1);
  }, [cursorHistory, fetchPreviewPage, pageIndex, previewActive]);

  const handlePageSelectChange = useCallback(
    async (event) => {
      if (!previewActive) return;
      const requestedPage = Number.parseInt(event.target.value, 10);
      if (Number.isNaN(requestedPage) || requestedPage < 1) return;

      const targetPageIndex = requestedPage - 1;
      if (targetPageIndex === pageIndex) return;

      let workingHistory = [...cursorHistory];
      let targetCursor = workingHistory[targetPageIndex];

      if (targetCursor === undefined) {
        let scanIndex = workingHistory.length - 1;
        while (scanIndex < targetPageIndex) {
          const scanCursor = workingHistory[scanIndex];
          if (scanCursor === undefined) break;
          const scanData = await requestPreviewPage(scanCursor);
          const generatedNextCursor = scanData?.nextCursor ?? null;
          if (!generatedNextCursor) break;
          workingHistory[scanIndex + 1] = generatedNextCursor;
          scanIndex += 1;
        }
        targetCursor = workingHistory[targetPageIndex];
      }

      if (targetCursor === undefined) return;

      await fetchPreviewPage(targetCursor ?? null, targetPageIndex, workingHistory);
    },
    [cursorHistory, fetchPreviewPage, pageIndex, previewActive, requestPreviewPage]
  );

  const handleSortByChange = useCallback(
    (event) => {
      const nextSortBy = event.target.value;
      setPreviewSortBy(nextSortBy);
      resetPreviewState();
    },
    [resetPreviewState]
  );

  const handleSortDirChange = useCallback(
    (event) => {
      const nextSortDir = event.target.value;
      setPreviewSortDir(nextSortDir);
      resetPreviewState();
    },
    [resetPreviewState]
  );

  const handlePreviewLimitChange = useCallback(
    (event) => {
      const nextLimit = Number.parseInt(event.target.value, 10);
      if (Number.isNaN(nextLimit)) return;
      setPreviewLimit(nextLimit);
      resetPreviewState();
    },
    [resetPreviewState]
  );

  const handleLoadBody = useCallback(
    async (id) => {
      if (entryDetailsById[id] || entryDetailsLoadingById[id]) {
        setExpandedById((prev) => ({ ...prev, [id]: true }));
        return;
      }
      setEntryDetailsLoadingById((prev) => ({ ...prev, [id]: true }));
      try {
        const detail = await getEntry(id);
        setEntryDetailsById((prev) => ({ ...prev, [id]: detail }));
        setExpandedById((prev) => ({ ...prev, [id]: true }));
        setError("");
      } catch (err) {
        setError(err.message || "Failed to load row body.");
      } finally {
        setEntryDetailsLoadingById((prev) => ({ ...prev, [id]: false }));
      }
    },
    [entryDetailsById, entryDetailsLoadingById]
  );

  const handleCollapseBody = useCallback((id) => {
    setExpandedById((prev) => ({ ...prev, [id]: false }));
  }, []);

  const handleLoadFullRaw = useCallback(
    async (id) => {
      if (entryRawById[id] || entryRawLoadingById[id]) return;
      setEntryRawLoadingById((prev) => ({ ...prev, [id]: true }));
      try {
        const raw = await getEntryRaw(id);
        setEntryRawById((prev) => ({ ...prev, [id]: raw }));
        setError("");
      } catch (err) {
        setError(err.message || "Failed to load full raw line.");
      } finally {
        setEntryRawLoadingById((prev) => ({ ...prev, [id]: false }));
      }
    },
    [entryRawById, entryRawLoadingById]
  );

  const handleReset = useCallback(async () => {
    setActionState((prev) => ({ ...prev, reset: true }));
    try {
      await resetData();
      await refreshStats();
      await refreshCounts();
      resetPreviewState();
    } catch (err) {
      setError(err.message || "Failed to reset data.");
    } finally {
      setActionState((prev) => ({ ...prev, reset: false }));
    }
  }, [refreshCounts, refreshStats, resetPreviewState]);

  const handleReload = useCallback(async () => {
    setActionState((prev) => ({ ...prev, reload: true }));
    try {
      await reloadData();
      await refreshStats();
      await refreshCounts();
      resetPreviewState();
    } catch (err) {
      setError(err.message || "Failed to reload data.");
    } finally {
      setActionState((prev) => ({ ...prev, reload: false }));
    }
  }, [refreshCounts, refreshStats, resetPreviewState]);

  const totalCount = counts?.totalCount ?? stats?.totalCount ?? 0;
  const matchCount = counts?.matchCount ?? 0;
  const activeCount = activeFilters?.length ?? 0;
  const totalPages = Math.max(1, Math.ceil(matchCount / previewLimit));
  const selectedPage = Math.min(pageIndex + 1, totalPages);
  const canGoPrev = previewActive && pageIndex > 0;
  const canGoNext = previewActive && pageIndex + 1 < totalPages && Boolean(nextCursor);

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
                  Showing up to <strong>{previewLimit}</strong> lines per page.
                </p>
              </div>
              <div className="preview-sort">
                <label className="preview-sort-label">
                  Lines/page
                  <select
                    className="preview-select"
                    value={previewLimit}
                    onChange={handlePreviewLimitChange}
                    disabled={previewLoading}
                  >
                    <option value={10}>10</option>
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                    <option value={200}>200</option>
                    <option value={500}>500</option>
                  </select>
                </label>
                <label className="preview-sort-label">
                  Sort by
                  <select
                    className="preview-select"
                    value={previewSortBy}
                    onChange={handleSortByChange}
                    disabled={previewLoading}
                  >
                    <option value="timestamp">Timestamp</option>
                    <option value="lineNo">Line</option>
                    <option value="id">ID</option>
                  </select>
                </label>
                <label className="preview-sort-label">
                  Direction
                  <select
                    className="preview-select"
                    value={previewSortDir}
                    onChange={handleSortDirChange}
                    disabled={previewLoading}
                  >
                    <option value="asc">Asc</option>
                    <option value="desc">Desc</option>
                  </select>
                </label>
              </div>
              <div className="preview-actions">
                <button
                  className="preview-btn"
                  onClick={handleLoadPreview}
                  disabled={previewLoading || (counts && matchCount === 0)}
                >
                  {previewActive ? "Reload Preview" : "Load Preview"}
                </button>
                {previewActive && (
                  <label className="preview-sort-label">
                    Page
                    <select
                      className="preview-select"
                      value={selectedPage}
                      onChange={handlePageSelectChange}
                      disabled={previewLoading || totalPages <= 1}
                    >
                      {Array.from({ length: totalPages }, (_, idx) => idx + 1).map((pageNo) => (
                        <option key={`page-${pageNo}`} value={pageNo}>
                          {pageNo}
                        </option>
                      ))}
                    </select>
                    <span className="preview-page-total">of {totalPages}</span>
                  </label>
                )}
                {previewActive && (
                  <button
                    className="preview-btn preview-btn--secondary"
                    onClick={handlePrevPage}
                    disabled={previewLoading || !canGoPrev}
                  >
                    Prev
                  </button>
                )}
                {previewActive && (
                  <button
                    className="preview-btn preview-btn--secondary"
                    onClick={handleNextPage}
                    disabled={previewLoading || !canGoNext}
                  >
                    Next
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
                key={row.id}
                row={row}
                expanded={Boolean(expandedById[row.id])}
                body={entryDetailsById[row.id]}
                fullRaw={entryRawById[row.id]}
                loadingBody={Boolean(entryDetailsLoadingById[row.id])}
                loadingRaw={Boolean(entryRawLoadingById[row.id])}
                onLoadBody={() => handleLoadBody(row.id)}
                onLoadRaw={() => handleLoadFullRaw(row.id)}
                onCollapse={() => handleCollapseBody(row.id)}
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
