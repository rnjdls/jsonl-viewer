import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useJsonlSearch } from "./hooks/useJsonlSearch";

import { TopBar } from "./components/TopBar/TopBar";
import { SearchBar } from "./components/SearchBar/SearchBar";
import { JsonCard } from "./components/JsonCard/JsonCard";
import { EmptyState } from "./components/EmptyState/EmptyState";

import {
  getCountStatus,
  getCounts,
  getEntry,
  getEntryRaw,
  getPreview,
  getStats,
  pauseIngestion,
  reloadData,
  resumeIngestion,
  resetData,
} from "./utils/api";
import { copyText } from "./utils/clipboard";
import {
  TIMEZONE_LOCAL,
  coerceTimeZoneOrLocal,
  formatDateTimeTooltip,
  formatTime,
} from "./utils/datetime";

import "./App.css";

const DEFAULT_PREVIEW_LIMIT = 10;
const DEFAULT_SORT_DIR = "desc";
const LOCAL_STORAGE_TIMEZONE_KEY = "jsonlLive.timeZone";
const ADMIN_ACTION = {
  RELOAD: "reload",
  RESET: "reset",
};
const ADMIN_ACTION_META = {
  [ADMIN_ACTION.RELOAD]: {
    title: "Reload File",
    description: "Current rows will be cleared and the source will be reloaded from the beginning.",
    confirmLabel: "Yes, Reload",
    loadingLabel: "Reloading source...",
    errorMessage: "Failed to reload data.",
    run: reloadData,
  },
  [ADMIN_ACTION.RESET]: {
    title: "Delete All",
    description: "Current rows will be removed and the source position will move to the end/newest point.",
    confirmLabel: "Yes, Delete All",
    loadingLabel: "Deleting rows...",
    errorMessage: "Failed to reset data.",
    run: resetData,
  },
};

function getCopyLabel(copyStatus) {
  if (copyStatus === "copying") return "Copying...";
  if (copyStatus === "copied") return "Copied";
  return "Copy";
}

function toFilterPayload(filters, filtersOp) {
  if (!filters || filters.length === 0) {
    return { filtersOp, filters: [] };
  }

  return {
    filtersOp,
    filters: filters.map((filter) => {
      if (filter.type === "text") {
        return {
          type: "text",
          query: filter.query,
        };
      }
      return null;
    }).filter(Boolean),
  };
}

function isSameStats(prevStats, nextStats) {
  if (prevStats === nextStats) return true;
  if (!prevStats || !nextStats) return false;
  return prevStats.filePath === nextStats.filePath
    && prevStats.totalCount === nextStats.totalCount
    && prevStats.parsedCount === nextStats.parsedCount
    && prevStats.errorCount === nextStats.errorCount
    && prevStats.lastIngestedAt === nextStats.lastIngestedAt
    && prevStats.sourceRevision === nextStats.sourceRevision
    && prevStats.searchStatus === nextStats.searchStatus
    && prevStats.ingestPaused === nextStats.ingestPaused
    && prevStats.ingestedBytes === nextStats.ingestedBytes
    && prevStats.targetBytes === nextStats.targetBytes
    && prevStats.exactCountAvailable === nextStats.exactCountAvailable;
}

function isSameCounts(prevCounts, nextCounts) {
  if (prevCounts === nextCounts) return true;
  if (!prevCounts || !nextCounts) return false;
  return prevCounts.totalCount === nextCounts.totalCount
    && prevCounts.matchCount === nextCounts.matchCount
    && prevCounts.status === nextCounts.status
    && prevCounts.requestHash === nextCounts.requestHash
    && prevCounts.sourceRevision === nextCounts.sourceRevision
    && prevCounts.computedRevision === nextCounts.computedRevision
    && prevCounts.lastComputedAt === nextCounts.lastComputedAt;
}

const PreviewSection = memo(function PreviewSection({
  previewLimit,
  previewSortDir,
  previewActive,
  previewRows,
  previewLoading,
  uiLocked,
  exactMatchReady,
  showPageTotal,
  totalPages,
  selectedPage,
  canGoPrev,
  canGoNext,
  matchCount,
  entryCopyStatusById,
  expandedById,
  entryDetailsById,
  entryRawById,
  entryDetailsLoadingById,
  entryRawLoadingById,
  jsonTreeExpandTokenById,
  onPreviewLimitChange,
  onSortDirChange,
  onLoadPreview,
  onPrevPage,
  onNextPage,
  onLoadBody,
  onLoadRaw,
  onCollapse,
  onExpandAll,
  onCopy,
}) {
  return (
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
              onChange={onPreviewLimitChange}
              disabled={uiLocked || previewLoading}
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
            Direction
            <select
              className="preview-select"
              value={previewSortDir}
              onChange={onSortDirChange}
              disabled={uiLocked || previewLoading}
            >
              <option value="asc">Asc</option>
              <option value="desc">Desc</option>
            </select>
          </label>
        </div>
        <div className="preview-actions">
          <button
            className="preview-btn"
            onClick={onLoadPreview}
            disabled={uiLocked || previewLoading || (exactMatchReady && matchCount === 0)}
          >
            {previewActive ? "Reload Preview" : "Load Preview"}
          </button>
          {previewActive && (
            <span className="preview-sort-label preview-page-label">
              Page <strong>{selectedPage}</strong>
              {showPageTotal && <span className="preview-page-total">of {totalPages}</span>}
            </span>
          )}
          {previewActive && (
            <button
              className="preview-btn preview-btn--secondary"
              onClick={onPrevPage}
              disabled={uiLocked || previewLoading || !canGoPrev}
            >
              Prev
            </button>
          )}
          {previewActive && (
            <button
              className="preview-btn preview-btn--secondary"
              onClick={onNextPage}
              disabled={uiLocked || previewLoading || !canGoNext}
            >
              Next
            </button>
          )}
        </div>
      </div>

      {!previewActive && !previewLoading && (
        <div className="preview-empty">
          Preview loads automatically when data is available. Use "Load Preview"
          to reload page 1 at any time.
        </div>
      )}

      {previewActive && !exactMatchReady && (
        <div className="preview-empty">
          Exact count is still pending. Preview pagination remains available with Prev/Next.
        </div>
      )}

      {previewActive && previewRows.length === 0 && !previewLoading && (
        <div className="preview-empty">No preview rows available.</div>
      )}

      {previewRows.map((row) => {
        const copyStatus = entryCopyStatusById[row.id] ?? "idle";
        return (
          <JsonCard
            key={row.id}
            row={row}
            rowId={row.id}
            expanded={Boolean(expandedById[row.id])}
            body={entryDetailsById[row.id]}
            fullRaw={entryRawById[row.id]}
            loadingBody={Boolean(entryDetailsLoadingById[row.id])}
            loadingRaw={Boolean(entryRawLoadingById[row.id])}
            onLoadBody={onLoadBody}
            onLoadRaw={onLoadRaw}
            onCollapse={onCollapse}
            onExpandAll={onExpandAll}
            onCopy={onCopy}
            copyLabel={getCopyLabel(copyStatus)}
            copyDisabled={uiLocked || copyStatus === "copying"}
            globalDisabled={uiLocked}
            expandAllToken={jsonTreeExpandTokenById[row.id] ?? 0}
          />
        );
      })}

      {previewLoading && (
        <div className="preview-loading">Loading preview...</div>
      )}
    </section>
  );
});

export default function App() {
  const [stats, setStats] = useState(null);
  const [statsError, setStatsError] = useState("");
  const [counts, setCounts] = useState(null);
  const [countsLoading, setCountsLoading] = useState(false);
  const [pendingCountRequestHash, setPendingCountRequestHash] = useState(null);
  const [previewRows, setPreviewRows] = useState([]);
  const [previewLimit, setPreviewLimit] = useState(DEFAULT_PREVIEW_LIMIT);
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
  const [entryCopyStatusById, setEntryCopyStatusById] = useState({});
  const [expandedById, setExpandedById] = useState({});
  const [jsonTreeExpandTokenById, setJsonTreeExpandTokenById] = useState({});
  const [error, setError] = useState("");
  const [actionState, setActionState] = useState({
    pauseToggle: false,
  });
  const [adminActionConfirming, setAdminActionConfirming] = useState(null);
  const [adminActionExecuting, setAdminActionExecuting] = useState(null);
  const [uiLocked, setUiLocked] = useState(false);
  const [timeZone, setTimeZone] = useState(() => {
    try {
      const persistedTimeZone = window.localStorage.getItem(LOCAL_STORAGE_TIMEZONE_KEY);
      return coerceTimeZoneOrLocal(persistedTimeZone);
    } catch {
      return TIMEZONE_LOCAL;
    }
  });
  const copyResetTimersRef = useRef({});
  const copyInFlightByIdRef = useRef({});
  const autoPreviewRunKeyRef = useRef("");
  const latestStatsRef = useRef(null);
  const exactCountAvailableRef = useRef(true);
  const previousExactCountAvailableRef = useRef(true);

  const clearAllCopyResetTimers = useCallback(() => {
    Object.values(copyResetTimersRef.current).forEach((timerId) => {
      clearTimeout(timerId);
    });
    copyResetTimersRef.current = {};
  }, []);

  const clearCopyResetTimer = useCallback((id) => {
    const timerId = copyResetTimersRef.current[id];
    if (timerId !== undefined) {
      clearTimeout(timerId);
      delete copyResetTimersRef.current[id];
    }
  }, []);

  const {
    filters,
    filtersOp,
    activeFilters,
    appliedFilters,
    hasFilters,
    hasActiveFilters,
    hasAppliedFilters,
    addTextFilter,
    setFiltersOp,
    updateFilter,
    removeFilter,
    clearAllFilters,
    applyFilters,
  } = useJsonlSearch([]);

  const resetPreviewState = useCallback(() => {
    clearAllCopyResetTimers();
    setPreviewRows([]);
    setCursorHistory([null]);
    setPageIndex(0);
    setNextCursor(null);
    setPreviewActive(false);
    setEntryDetailsById({});
    setEntryDetailsLoadingById({});
    setEntryRawById({});
    setEntryRawLoadingById({});
    setEntryCopyStatusById({});
    setExpandedById({});
    setJsonTreeExpandTokenById({});
    copyInFlightByIdRef.current = {};
  }, [clearAllCopyResetTimers]);

  useEffect(
    () => () => {
      clearAllCopyResetTimers();
    },
    [clearAllCopyResetTimers]
  );

  useEffect(() => {
    try {
      window.localStorage.setItem(LOCAL_STORAGE_TIMEZONE_KEY, timeZone);
    } catch {
      // Ignore blocked or unavailable localStorage.
    }
  }, [timeZone]);

  useEffect(() => {
    if (!adminActionConfirming) {
      return;
    }

    const handleWindowKeyDown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setAdminActionConfirming(null);
      }
    };

    window.addEventListener("keydown", handleWindowKeyDown);
    return () => {
      window.removeEventListener("keydown", handleWindowKeyDown);
    };
  }, [adminActionConfirming]);

  const activeFilterPayload = useMemo(
    () => toFilterPayload(activeFilters, filtersOp),
    [activeFilters, filtersOp]
  );

  const filterPayload = useMemo(
    () => toFilterPayload(appliedFilters, filtersOp),
    [appliedFilters, filtersOp]
  );

  const exactCountAvailable = stats?.exactCountAvailable ?? true;

  const isCurrentCountRevision = useCallback((countResponse) => {
    const responseRevision = countResponse?.sourceRevision;
    const latestStatsRevision = latestStatsRef.current?.sourceRevision;
    if (typeof responseRevision !== "number" || typeof latestStatsRevision !== "number") {
      return true;
    }
    return responseRevision === latestStatsRevision;
  }, []);

  const refreshStats = useCallback(async () => {
    try {
      const data = await getStats();
      setStats((prev) => (isSameStats(prev, data) ? prev : data));
      setStatsError((prev) => (prev ? "" : prev));
    } catch (err) {
      setStatsError(err.message || "Failed to reach backend.");
    }
  }, []);

  const refreshCounts = useCallback(
    async (payload) => {
      if (!stats?.filePath) {
        const emptyCounts = {
          totalCount: 0,
          matchCount: 0,
          status: "ready",
          requestHash: "",
          sourceRevision: 0,
          computedRevision: 0,
          lastComputedAt: null,
        };
        setCounts((prev) => (isSameCounts(prev, emptyCounts) ? prev : emptyCounts));
        setPendingCountRequestHash((prev) => (prev === null ? prev : null));
        return emptyCounts;
      }

      if (!exactCountAvailableRef.current) {
        setPendingCountRequestHash((prev) => (prev === null ? prev : null));
        setCountsLoading((prev) => (prev ? false : prev));
        return null;
      }

      setCountsLoading(true);
      try {
        const data = await getCounts(payload || { filtersOp, filters: [] });
        if (!isCurrentCountRevision(data)) {
          return null;
        }

        setCounts((prev) => (isSameCounts(prev, data) ? prev : data));
        if (data?.status === "pending" && data?.requestHash) {
          setPendingCountRequestHash((prev) => (prev === data.requestHash ? prev : data.requestHash));
        } else {
          setPendingCountRequestHash((prev) => (prev === null ? prev : null));
        }
        setError("");
        return data;
      } catch (err) {
        setError(err.message || "Failed to fetch counts.");
        return null;
      } finally {
        setCountsLoading(false);
      }
    },
    [filtersOp, isCurrentCountRevision, stats?.filePath]
  );

  useEffect(() => {
    refreshStats();
    const timer = setInterval(refreshStats, 3000);
    return () => clearInterval(timer);
  }, [refreshStats]);

  useEffect(() => {
    latestStatsRef.current = stats;
    exactCountAvailableRef.current = stats?.exactCountAvailable ?? true;
  }, [stats]);

  useEffect(() => {
    if (!stats?.filePath) {
      setCounts(null);
      setPendingCountRequestHash(null);
      return;
    }

    if (!(stats?.exactCountAvailable ?? true)) {
      setPendingCountRequestHash((prev) => (prev === null ? prev : null));
      setCountsLoading((prev) => (prev ? false : prev));
      return;
    }

    refreshCounts(filterPayload);
  }, [refreshCounts, stats?.filePath]);

  useEffect(() => {
    const previouslyAvailable = previousExactCountAvailableRef.current;
    previousExactCountAvailableRef.current = exactCountAvailable;

    if (!stats?.filePath) {
      return;
    }
    if (!exactCountAvailable) {
      setPendingCountRequestHash((prev) => (prev === null ? prev : null));
      setCountsLoading((prev) => (prev ? false : prev));
      return;
    }
    if (!previouslyAvailable && exactCountAvailable) {
      refreshCounts(filterPayload);
    }
  }, [exactCountAvailable, filterPayload, refreshCounts, stats?.filePath]);

  useEffect(() => {
    if (!pendingCountRequestHash || !stats?.filePath || !exactCountAvailable) {
      return;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const data = await getCountStatus(pendingCountRequestHash);
        if (cancelled) return;
        if (!isCurrentCountRevision(data)) {
          setPendingCountRequestHash((prev) => (prev === null ? prev : null));
          return;
        }
        setCounts((prev) => (isSameCounts(prev, data) ? prev : data));
        if (data?.status === "ready") {
          setPendingCountRequestHash((prev) => (prev === null ? prev : null));
        }
      } catch (err) {
        if (cancelled) return;
        setError(err.message || "Failed to poll count status.");
      }
    };

    poll();
    const timer = setInterval(poll, 1500);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [exactCountAvailable, isCurrentCountRevision, pendingCountRequestHash, stats?.filePath]);

  useEffect(() => {
    if (!pendingCountRequestHash || !stats?.filePath || !exactCountAvailable) {
      return;
    }
    refreshCounts(filterPayload);
  }, [
    exactCountAvailable,
    filterPayload,
    refreshCounts,
    stats?.filePath,
    stats?.sourceRevision,
  ]);

  const requestPreviewPage = useCallback(
    async (cursor, filterPayloadOverride) =>
      getPreview({
        ...(filterPayloadOverride ?? filterPayload),
        cursor,
        sortDir: previewSortDir,
        limit: previewLimit,
      }),
    [filterPayload, previewLimit, previewSortDir]
  );

  const fetchPreviewPage = useCallback(
    async (cursor, targetPageIndex, filterPayloadOverride) => {
      if (!stats?.filePath) return;
      setPreviewLoading(true);
      try {
        const data = await requestPreviewPage(cursor, filterPayloadOverride);
        const rows = data?.rows ?? [];
        const receivedNextCursor = data?.nextCursor ?? null;
        setPreviewRows(rows);
        setPreviewActive(true);
        setPageIndex(targetPageIndex);
        setNextCursor(receivedNextCursor);
        setCursorHistory((prev) => {
          const nextHistory = [...prev];
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

  const handleClearAll = useCallback(async () => {
    if (uiLocked) return;

    const emptyPayload = { filtersOp, filters: [] };
    clearAllFilters();
    resetPreviewState();

    const nextCounts = await refreshCounts(emptyPayload);
    if (!stats?.filePath) return;

    const totalCountAfterClear = nextCounts?.totalCount ?? stats?.totalCount ?? 0;
    if (totalCountAfterClear <= 0) return;

    await fetchPreviewPage(null, 0, emptyPayload);
  }, [
    clearAllFilters,
    fetchPreviewPage,
    filtersOp,
    refreshCounts,
    resetPreviewState,
    stats?.filePath,
    stats?.totalCount,
    uiLocked,
  ]);

  const handleSearch = useCallback(async () => {
    if (uiLocked) return;
    applyFilters();
    resetPreviewState();
    if (!stats?.filePath || (stats?.totalCount ?? 0) <= 0) return;

    if (!exactCountAvailable && activeFilterPayload.filters.length > 0) {
      setPendingCountRequestHash((prev) => (prev === null ? prev : null));
      setCountsLoading((prev) => (prev ? false : prev));
      await fetchPreviewPage(null, 0, activeFilterPayload);
      return;
    }

    const nextCounts = await refreshCounts(activeFilterPayload);
    const exactMatchReadyAfterSearch = nextCounts
      && nextCounts.status === "ready"
      && nextCounts.computedRevision === nextCounts.sourceRevision;
    const matchAfterSearch = nextCounts?.matchCount ?? 0;
    if (exactMatchReadyAfterSearch && matchAfterSearch === 0) return;

    await fetchPreviewPage(null, 0, activeFilterPayload);
  }, [
    activeFilterPayload,
    applyFilters,
    exactCountAvailable,
    fetchPreviewPage,
    refreshCounts,
    resetPreviewState,
    stats?.filePath,
    stats?.totalCount,
    uiLocked,
  ]);

  useEffect(() => {
    if (!stats?.filePath || uiLocked || previewActive || previewLoading) {
      return;
    }

    const sourceRevision = stats?.sourceRevision ?? 0;
    const autoPreviewKey = `${stats.filePath}::${sourceRevision}`;
    if (autoPreviewRunKeyRef.current === autoPreviewKey) {
      return;
    }

    const totalForAutoPreview = stats?.totalCount ?? 0;
    if (totalForAutoPreview <= 0) {
      return;
    }

    autoPreviewRunKeyRef.current = autoPreviewKey;
    fetchPreviewPage(null, 0);
  }, [
    fetchPreviewPage,
    previewActive,
    previewLimit,
    previewLoading,
    previewSortDir,
    stats?.filePath,
    stats?.sourceRevision,
    stats?.totalCount,
    uiLocked,
  ]);

  const handleLoadPreview = useCallback(async () => {
    if (uiLocked) return;
    await fetchPreviewPage(null, 0);
  }, [fetchPreviewPage, uiLocked]);

  const handleNextPage = useCallback(async () => {
    if (uiLocked || !previewActive || !nextCursor) return;
    await fetchPreviewPage(nextCursor, pageIndex + 1);
  }, [fetchPreviewPage, nextCursor, pageIndex, previewActive, uiLocked]);

  const handlePrevPage = useCallback(async () => {
    if (uiLocked || !previewActive || pageIndex === 0) return;
    const prevCursor = cursorHistory[pageIndex - 1] ?? null;
    await fetchPreviewPage(prevCursor, pageIndex - 1);
  }, [cursorHistory, fetchPreviewPage, pageIndex, previewActive, uiLocked]);

  const handleSortDirChange = useCallback(
    (event) => {
      if (uiLocked) return;
      const nextSortDir = event.target.value;
      autoPreviewRunKeyRef.current = "";
      setPreviewSortDir(nextSortDir);
      resetPreviewState();
    },
    [resetPreviewState, uiLocked]
  );

  const handlePreviewLimitChange = useCallback(
    (event) => {
      if (uiLocked) return;
      const nextLimit = Number.parseInt(event.target.value, 10);
      if (Number.isNaN(nextLimit)) return;
      autoPreviewRunKeyRef.current = "";
      setPreviewLimit(nextLimit);
      resetPreviewState();
    },
    [resetPreviewState, uiLocked]
  );

  const handleLoadBody = useCallback(
    async (id) => {
      if (uiLocked) return;
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
    [entryDetailsById, entryDetailsLoadingById, uiLocked]
  );

  const handleCollapseBody = useCallback((id) => {
    if (uiLocked) return;
    setExpandedById((prev) => ({ ...prev, [id]: false }));
    setJsonTreeExpandTokenById((prev) => {
      if (!(id in prev)) return prev;
      const next = { ...prev };
      delete next[id];
      return next;
    });
  }, [uiLocked]);

  const handleExpandAllJsonTree = useCallback((id) => {
    if (uiLocked) return;
    setJsonTreeExpandTokenById((prev) => ({
      ...prev,
      [id]: (prev[id] ?? 0) + 1,
    }));
  }, [uiLocked]);

  const handleLoadFullRaw = useCallback(
    async (id) => {
      if (uiLocked) return;
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
    [entryRawById, entryRawLoadingById, uiLocked]
  );

  const handleCopyRawLine = useCallback(
    async (id) => {
      if (uiLocked) return;
      if (copyInFlightByIdRef.current[id]) return;

      copyInFlightByIdRef.current[id] = true;
      setEntryCopyStatusById((prev) => ({ ...prev, [id]: "copying" }));

      try {
        const cachedRaw = entryRawById[id];
        const rawLine = cachedRaw ?? (await getEntryRaw(id));

        if (cachedRaw === undefined) {
          setEntryRawById((prev) => ({ ...prev, [id]: rawLine }));
        }

        await copyText(rawLine ?? "");
        clearCopyResetTimer(id);
        setEntryCopyStatusById((prev) => ({ ...prev, [id]: "copied" }));
        copyResetTimersRef.current[id] = window.setTimeout(() => {
          setEntryCopyStatusById((prev) => ({ ...prev, [id]: "idle" }));
          clearCopyResetTimer(id);
        }, 1200);
        setError("");
      } catch (err) {
        setEntryCopyStatusById((prev) => ({ ...prev, [id]: "idle" }));
        setError(err.message || "Failed to copy raw line.");
      } finally {
        copyInFlightByIdRef.current[id] = false;
      }
    },
    [clearCopyResetTimer, entryRawById, uiLocked]
  );

  const handleRequestAdminAction = useCallback(
    (action) => {
      if (uiLocked || !ADMIN_ACTION_META[action]) return;
      setAdminActionConfirming(action);
    },
    [uiLocked]
  );

  const handleCancelAdminActionConfirmation = useCallback(() => {
    if (uiLocked) return;
    setAdminActionConfirming(null);
  }, [uiLocked]);

  const handleConfirmAdminAction = useCallback(async () => {
    if (!adminActionConfirming || uiLocked) return;
    const actionMeta = ADMIN_ACTION_META[adminActionConfirming];
    if (!actionMeta) return;

    setAdminActionConfirming(null);
    setAdminActionExecuting(adminActionConfirming);
    setUiLocked(true);

    try {
      await actionMeta.run();
      await refreshStats();
      await refreshCounts(filterPayload);
      resetPreviewState();
      setError("");
    } catch (err) {
      setError(err.message || actionMeta.errorMessage);
    } finally {
      setAdminActionExecuting(null);
      setUiLocked(false);
    }
  }, [adminActionConfirming, filterPayload, refreshCounts, refreshStats, resetPreviewState, uiLocked]);

  const handlePauseToggle = useCallback(async () => {
    if (uiLocked) return;
    setActionState((prev) => ({ ...prev, pauseToggle: true }));
    try {
      if (stats?.ingestPaused) {
        await resumeIngestion();
      } else {
        await pauseIngestion();
      }
      await refreshStats();
      setError("");
    } catch (err) {
      setError(err.message || "Failed to toggle ingestion.");
    } finally {
      setActionState((prev) => ({ ...prev, pauseToggle: false }));
    }
  }, [refreshStats, stats?.ingestPaused, uiLocked]);

  const handleTimeZoneChange = useCallback((nextTimeZone) => {
    if (uiLocked) return;
    setTimeZone(coerceTimeZoneOrLocal(nextTimeZone));
  }, [uiLocked]);

  const confirmingAdminActionMeta = adminActionConfirming
    ? ADMIN_ACTION_META[adminActionConfirming]
    : null;
  const executingAdminActionMeta = adminActionExecuting
    ? ADMIN_ACTION_META[adminActionExecuting]
    : null;
  const totalCount = stats?.totalCount ?? counts?.totalCount ?? 0;
  const deferredCountsActive = Boolean(stats?.filePath) && !exactCountAvailable && hasAppliedFilters;
  const exactMatchReady =
    exactCountAvailable
    && counts?.status === "ready"
    && counts?.computedRevision === counts?.sourceRevision;
  const matchCount = exactMatchReady
    ? (counts?.matchCount ?? 0)
    : (hasAppliedFilters ? 0 : totalCount);
  const searchBarCountStatus = deferredCountsActive ? "deferred" : (counts?.status || "ready");
  const activeCount = activeFilters?.length ?? 0;
  const totalPages = exactMatchReady ? Math.max(1, Math.ceil(matchCount / previewLimit)) : null;
  const showPageTotal = exactMatchReady && typeof totalPages === "number";
  const selectedPage = pageIndex + 1;
  const canGoPrev = previewActive && pageIndex > 0;
  const canGoNext = previewActive
    && Boolean(nextCursor)
    && (!showPageTotal || pageIndex + 1 < totalPages);

  const emptyVariant = statsError
    ? "backend-offline"
    : !stats?.filePath
      ? "no-file"
      : totalCount === 0
        ? "empty-file"
        : exactMatchReady && matchCount === 0 && hasAppliedFilters
          ? "no-results"
          : null;

  return (
    <div className={`app ${uiLocked ? "app--busy" : ""}`} aria-busy={uiLocked}>
      <TopBar
        filePath={stats?.filePath}
        lastIngestedAt={stats?.lastIngestedAt}
        totalCount={stats?.totalCount ?? 0}
        errorCount={stats?.errorCount ?? 0}
        searchStatus={stats?.searchStatus || "ready"}
        ingestPaused={Boolean(stats?.ingestPaused)}
        ingestedBytes={stats?.ingestedBytes ?? null}
        targetBytes={stats?.targetBytes ?? null}
        onReload={() => handleRequestAdminAction(ADMIN_ACTION.RELOAD)}
        onReset={() => handleRequestAdminAction(ADMIN_ACTION.RESET)}
        onPauseToggle={handlePauseToggle}
        resetLoading={adminActionExecuting === ADMIN_ACTION.RESET}
        reloadLoading={adminActionExecuting === ADMIN_ACTION.RELOAD}
        pauseToggleLoading={actionState.pauseToggle}
        globalLocked={uiLocked}
        timeZone={timeZone}
        onTimeZoneChange={handleTimeZoneChange}
      />

      <SearchBar
        filters={filters}
        totalCount={totalCount}
        matchCount={matchCount}
        hasFilters={hasFilters}
        hasActiveFilters={hasActiveFilters}
        hasAppliedFilters={hasAppliedFilters}
        activeCount={activeCount}
        filtersOp={filtersOp}
        loading={countsLoading}
        countStatus={searchBarCountStatus}
        globalDisabled={uiLocked}
        onAddTextFilter={addTextFilter}
        onUpdateFilter={updateFilter}
        onRemoveFilter={removeFilter}
        onFiltersOpChange={setFiltersOp}
        onClearAll={handleClearAll}
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
          <PreviewSection
            previewLimit={previewLimit}
            previewSortDir={previewSortDir}
            previewActive={previewActive}
            previewRows={previewRows}
            previewLoading={previewLoading}
            uiLocked={uiLocked}
            exactMatchReady={exactMatchReady}
            showPageTotal={showPageTotal}
            totalPages={totalPages}
            selectedPage={selectedPage}
            canGoPrev={canGoPrev}
            canGoNext={canGoNext}
            matchCount={matchCount}
            entryCopyStatusById={entryCopyStatusById}
            expandedById={expandedById}
            entryDetailsById={entryDetailsById}
            entryRawById={entryRawById}
            entryDetailsLoadingById={entryDetailsLoadingById}
            entryRawLoadingById={entryRawLoadingById}
            jsonTreeExpandTokenById={jsonTreeExpandTokenById}
            onPreviewLimitChange={handlePreviewLimitChange}
            onSortDirChange={handleSortDirChange}
            onLoadPreview={handleLoadPreview}
            onPrevPage={handlePrevPage}
            onNextPage={handleNextPage}
            onLoadBody={handleLoadBody}
            onLoadRaw={handleLoadFullRaw}
            onCollapse={handleCollapseBody}
            onExpandAll={handleExpandAllJsonTree}
            onCopy={handleCopyRawLine}
          />
        )}
      </main>

      {confirmingAdminActionMeta && (
        <div className="admin-confirm-backdrop" role="presentation">
          <div
            className="admin-confirm-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-confirm-title"
            aria-describedby="admin-confirm-description"
          >
            <h2 id="admin-confirm-title" className="admin-confirm-title">
              {confirmingAdminActionMeta.title}
            </h2>
            <p id="admin-confirm-description" className="admin-confirm-description">
              {confirmingAdminActionMeta.description}
            </p>
            <div className="admin-confirm-actions">
              <button
                type="button"
                className="admin-confirm-btn admin-confirm-btn--secondary"
                onClick={handleCancelAdminActionConfirmation}
                autoFocus
              >
                Cancel
              </button>
              <button
                type="button"
                className="admin-confirm-btn admin-confirm-btn--primary"
                onClick={handleConfirmAdminAction}
              >
                {confirmingAdminActionMeta.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}

      {uiLocked && executingAdminActionMeta && (
        <div className="admin-loading-overlay" role="status" aria-live="polite">
          <div className="admin-loading-card">
            <div className="admin-loading-spinner" aria-hidden="true" />
            <p className="admin-loading-text">{executingAdminActionMeta.loadingLabel}</p>
          </div>
        </div>
      )}

      <footer className="app-footer">
        <span>JSONL Live Viewer</span>
        {stats?.filePath && (
          <span className="app-footer-meta">
            Source: <strong>{stats.filePath}</strong>
          </span>
        )}
        {stats?.lastIngestedAt && (
          <span
            className="app-footer-time"
            title={formatDateTimeTooltip(stats.lastIngestedAt, timeZone)}
          >
            Last ingest: {formatTime(stats.lastIngestedAt, timeZone)}
          </span>
        )}
      </footer>
    </div>
  );
}
