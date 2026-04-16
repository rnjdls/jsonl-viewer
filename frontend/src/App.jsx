import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { FIELD_FILTER_OP } from "./constants";

import "./App.css";

const DEFAULT_PREVIEW_LIMIT = 10;
const DEFAULT_SORT_DIR = "desc";
const INITIAL_PREVIEW_REFRESH_INTERVAL_MS = 3000;
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

function toTimestampPayload(value) {
  const trimmed = (value ?? "").trim();
  return trimmed || undefined;
}

function toFilterPayload(filters, filtersOp) {
  if (!filters || filters.length === 0) {
    return { filtersOp, filters: [] };
  }

  return {
    filtersOp,
    filters: filters.map((filter) => {
      if (filter.type === "field") {
        const op = filter.op || FIELD_FILTER_OP.CONTAINS;
        return {
          type: "field",
          fieldPath: filter.field,
          op,
          ...(op === FIELD_FILTER_OP.CONTAINS ? { valueContains: filter.value } : {}),
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
}

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
  const [initialPageAutoRefreshMode, setInitialPageAutoRefreshMode] = useState(false);
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
  const initialPageAutoRefreshTimerRef = useRef(null);
  const initialPageAutoRefreshEligibleRef = useRef(true);
  const latestSourceKeyRef = useRef(null);

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

  const clearInitialPageAutoRefreshTimer = useCallback(() => {
    if (initialPageAutoRefreshTimerRef.current !== null) {
      clearTimeout(initialPageAutoRefreshTimerRef.current);
      initialPageAutoRefreshTimerRef.current = null;
    }
  }, []);

  const leaveInitialPageAutoRefreshContext = useCallback(() => {
    initialPageAutoRefreshEligibleRef.current = false;
    clearInitialPageAutoRefreshTimer();
    setInitialPageAutoRefreshMode(false);
  }, [clearInitialPageAutoRefreshTimer]);

  const {
    filters,
    filtersOp,
    activeFilters,
    appliedFilters,
    hasFilters,
    hasActiveFilters,
    hasAppliedFilters,
    addFieldFilter,
    addTextFilter,
    addTimestampFilter,
    setFiltersOp,
    updateFilter,
    removeFilter,
    clearAllFilters,
    applyFilters,
  } = useJsonlSearch([]);

  const resetPreviewState = useCallback(() => {
    clearAllCopyResetTimers();
    clearInitialPageAutoRefreshTimer();
    setInitialPageAutoRefreshMode(false);
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
  }, [clearAllCopyResetTimers, clearInitialPageAutoRefreshTimer]);

  useEffect(
    () => () => {
      clearAllCopyResetTimers();
      clearInitialPageAutoRefreshTimer();
    },
    [clearAllCopyResetTimers, clearInitialPageAutoRefreshTimer]
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

  const refreshStats = useCallback(async () => {
    try {
      const data = await getStats();
      setStats(data);
      setStatsError("");
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
        setCounts(emptyCounts);
        setPendingCountRequestHash(null);
        return emptyCounts;
      }
      setCountsLoading(true);
      try {
        const data = await getCounts(payload || { filtersOp, filters: [] });
        setCounts(data);
        if (data?.status === "pending" && data?.requestHash) {
          setPendingCountRequestHash(data.requestHash);
        } else {
          setPendingCountRequestHash(null);
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
    [filtersOp, stats?.filePath]
  );

  useEffect(() => {
    refreshStats();
    const timer = setInterval(refreshStats, 3000);
    return () => clearInterval(timer);
  }, [refreshStats]);

  useEffect(() => {
    if (!stats?.filePath) {
      if (latestSourceKeyRef.current !== null) {
        latestSourceKeyRef.current = null;
        leaveInitialPageAutoRefreshContext();
      }
      setCounts(null);
      setPendingCountRequestHash(null);
      return;
    }
    refreshCounts(filterPayload);
  }, [leaveInitialPageAutoRefreshContext, refreshCounts, stats?.filePath]);

  useEffect(() => {
    if (!stats?.filePath) {
      return;
    }

    const sourceKey = stats.filePath;
    if (latestSourceKeyRef.current === null) {
      latestSourceKeyRef.current = sourceKey;
      return;
    }
    if (latestSourceKeyRef.current !== sourceKey) {
      latestSourceKeyRef.current = sourceKey;
      leaveInitialPageAutoRefreshContext();
    }
  }, [leaveInitialPageAutoRefreshContext, stats?.filePath]);

  useEffect(() => {
    if (!pendingCountRequestHash || !stats?.filePath) {
      return;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const data = await getCountStatus(pendingCountRequestHash);
        if (cancelled) return;
        setCounts(data);
        if (data?.status === "ready") {
          setPendingCountRequestHash(null);
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
  }, [pendingCountRequestHash, stats?.filePath]);

  useEffect(() => {
    if (!pendingCountRequestHash || !stats?.filePath) {
      return;
    }
    refreshCounts(filterPayload);
  }, [filterPayload, pendingCountRequestHash, refreshCounts, stats?.filePath, stats?.sourceRevision]);

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
    async (cursor, targetPageIndex, historySeed, filterPayloadOverride) => {
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

  const handleSearch = useCallback(async () => {
    if (uiLocked) return;
    leaveInitialPageAutoRefreshContext();
    applyFilters();
    resetPreviewState();
    const nextCounts = await refreshCounts(activeFilterPayload);
    if (!nextCounts) return;
    const totalAfterSearch = nextCounts?.totalCount ?? stats?.totalCount ?? 0;
    const exactMatchReadyAfterSearch =
      nextCounts?.status === "ready"
      && nextCounts?.computedRevision === nextCounts?.sourceRevision;
    const matchAfterSearch = nextCounts?.matchCount ?? 0;

    if (!stats?.filePath || totalAfterSearch <= 0) return;
    if (exactMatchReadyAfterSearch && matchAfterSearch === 0) return;

    await fetchPreviewPage(null, 0, undefined, activeFilterPayload);
  }, [
    activeFilterPayload,
    applyFilters,
    fetchPreviewPage,
    refreshCounts,
    resetPreviewState,
    stats?.filePath,
    stats?.totalCount,
    uiLocked,
    leaveInitialPageAutoRefreshContext,
  ]);

  useEffect(() => {
    if (uiLocked) {
      leaveInitialPageAutoRefreshContext();
    }
  }, [leaveInitialPageAutoRefreshContext, uiLocked]);

  useEffect(() => {
    if (!stats?.filePath || uiLocked || previewActive || previewLoading) {
      return;
    }

    const sourceRevision = hasAppliedFilters
      ? (counts?.sourceRevision ?? stats?.sourceRevision ?? 0)
      : (stats?.sourceRevision ?? counts?.sourceRevision ?? 0);
    const autoPreviewKey = `${stats.filePath}::${sourceRevision}`;
    if (autoPreviewRunKeyRef.current === autoPreviewKey) {
      return;
    }

    const totalFromCounts = counts?.totalCount;
    const totalForAutoPreview = hasAppliedFilters
      ? ((typeof totalFromCounts === "number" ? totalFromCounts : stats?.totalCount) ?? 0)
      : ((typeof stats?.totalCount === "number" ? stats.totalCount : totalFromCounts) ?? 0);
    if (totalForAutoPreview <= 0) {
      return;
    }

    const shouldKeepInitialPageAutoRefreshMode =
      initialPageAutoRefreshEligibleRef.current && !hasAppliedFilters;
    setInitialPageAutoRefreshMode(shouldKeepInitialPageAutoRefreshMode);
    autoPreviewRunKeyRef.current = autoPreviewKey;
    fetchPreviewPage(null, 0);
  }, [
    counts?.sourceRevision,
    counts?.totalCount,
    fetchPreviewPage,
    hasAppliedFilters,
    previewActive,
    previewLimit,
    previewLoading,
    previewSortDir,
    stats?.filePath,
    stats?.sourceRevision,
    stats?.totalCount,
    uiLocked,
  ]);

  useEffect(() => {
    clearInitialPageAutoRefreshTimer();
    if (!initialPageAutoRefreshMode) {
      return;
    }
    if (!stats?.filePath || hasAppliedFilters || pageIndex !== 0 || uiLocked || previewLoading) {
      return;
    }
    if ((cursorHistory[0] ?? null) !== null) {
      return;
    }

    const currentPageSize = previewRows.length;
    const totalRows = stats?.totalCount ?? 0;
    const isLinePageMatch = currentPageSize === previewLimit || currentPageSize >= totalRows;
    if (isLinePageMatch) {
      return;
    }

    initialPageAutoRefreshTimerRef.current = window.setTimeout(() => {
      fetchPreviewPage(null, 0);
    }, INITIAL_PREVIEW_REFRESH_INTERVAL_MS);
  }, [
    clearInitialPageAutoRefreshTimer,
    cursorHistory,
    fetchPreviewPage,
    hasAppliedFilters,
    initialPageAutoRefreshMode,
    pageIndex,
    previewLimit,
    previewLoading,
    previewRows.length,
    stats?.filePath,
    stats?.totalCount,
    uiLocked,
  ]);

  const handleLoadPreview = useCallback(async () => {
    if (uiLocked) return;
    leaveInitialPageAutoRefreshContext();
    await fetchPreviewPage(null, 0);
  }, [fetchPreviewPage, leaveInitialPageAutoRefreshContext, uiLocked]);

  const handleNextPage = useCallback(async () => {
    if (uiLocked || !previewActive || !nextCursor) return;
    leaveInitialPageAutoRefreshContext();
    await fetchPreviewPage(nextCursor, pageIndex + 1);
  }, [
    fetchPreviewPage,
    leaveInitialPageAutoRefreshContext,
    nextCursor,
    pageIndex,
    previewActive,
    uiLocked,
  ]);

  const handlePrevPage = useCallback(async () => {
    if (uiLocked || !previewActive || pageIndex === 0) return;
    leaveInitialPageAutoRefreshContext();
    const prevCursor = cursorHistory[pageIndex - 1] ?? null;
    await fetchPreviewPage(prevCursor, pageIndex - 1);
  }, [
    cursorHistory,
    fetchPreviewPage,
    leaveInitialPageAutoRefreshContext,
    pageIndex,
    previewActive,
    uiLocked,
  ]);

  const handlePageSelectChange = useCallback(
    async (event) => {
      if (uiLocked || !previewActive) return;
      leaveInitialPageAutoRefreshContext();
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
    [
      cursorHistory,
      fetchPreviewPage,
      leaveInitialPageAutoRefreshContext,
      pageIndex,
      previewActive,
      requestPreviewPage,
      uiLocked,
    ]
  );

  const handleSortDirChange = useCallback(
    (event) => {
      if (uiLocked) return;
      leaveInitialPageAutoRefreshContext();
      const nextSortDir = event.target.value;
      autoPreviewRunKeyRef.current = "";
      setPreviewSortDir(nextSortDir);
      resetPreviewState();
    },
    [leaveInitialPageAutoRefreshContext, resetPreviewState, uiLocked]
  );

  const handlePreviewLimitChange = useCallback(
    (event) => {
      if (uiLocked) return;
      leaveInitialPageAutoRefreshContext();
      const nextLimit = Number.parseInt(event.target.value, 10);
      if (Number.isNaN(nextLimit)) return;
      autoPreviewRunKeyRef.current = "";
      setPreviewLimit(nextLimit);
      resetPreviewState();
    },
    [leaveInitialPageAutoRefreshContext, resetPreviewState, uiLocked]
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
    leaveInitialPageAutoRefreshContext();
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
  }, [
    adminActionConfirming,
    filterPayload,
    leaveInitialPageAutoRefreshContext,
    refreshCounts,
    refreshStats,
    resetPreviewState,
    uiLocked,
  ]);

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
  const totalCount = hasAppliedFilters
    ? (counts?.totalCount ?? stats?.totalCount ?? 0)
    : (stats?.totalCount ?? counts?.totalCount ?? 0);
  const exactMatchReady =
    counts?.status === "ready"
    && counts?.computedRevision === counts?.sourceRevision;
  const matchCount = exactMatchReady ? (counts?.matchCount ?? 0) : 0;
  const activeCount = activeFilters?.length ?? 0;
  const totalPages = exactMatchReady ? Math.max(1, Math.ceil(matchCount / previewLimit)) : null;
  const selectedPage = pageIndex + 1;
  const canGoPrev = previewActive && pageIndex > 0;
  const canGoNext = previewActive
    && Boolean(nextCursor)
    && (!exactMatchReady || pageIndex + 1 < totalPages);

  const emptyVariant = statsError
    ? "backend-offline"
    : !stats?.filePath
      ? "no-file"
      : !hasAppliedFilters && (stats?.totalCount ?? 0) === 0
        ? "empty-file"
        : hasAppliedFilters && counts && totalCount === 0
          ? "empty-file"
          : counts && exactMatchReady && matchCount === 0 && hasAppliedFilters
            ? "no-results"
            : null;

  return (
    <div className={`app ${uiLocked ? "app--busy" : ""}`} aria-busy={uiLocked}>
      <TopBar
        filePath={stats?.filePath}
        lastIngestedAt={stats?.lastIngestedAt}
        totalCount={stats?.totalCount ?? 0}
        parsedCount={stats?.parsedCount ?? 0}
        errorCount={stats?.errorCount ?? 0}
        searchStatus={stats?.searchStatus || "ready"}
        ingestPaused={Boolean(stats?.ingestPaused)}
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
        countStatus={counts?.status || "ready"}
        globalDisabled={uiLocked}
        onAddFieldFilter={addFieldFilter}
        onAddTextFilter={addTextFilter}
        onAddTimestampFilter={addTimestampFilter}
        onUpdateFilter={updateFilter}
        onRemoveFilter={removeFilter}
        onFiltersOpChange={setFiltersOp}
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
                    onChange={handleSortDirChange}
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
                  onClick={handleLoadPreview}
                  disabled={uiLocked || previewLoading || (exactMatchReady && matchCount === 0)}
                >
                  {previewActive ? "Reload Preview" : "Load Preview"}
                </button>
                {previewActive && exactMatchReady && (
                  <label className="preview-sort-label">
                    Page
                    <select
                      className="preview-select"
                      value={selectedPage}
                      onChange={handlePageSelectChange}
                      disabled={uiLocked || previewLoading || totalPages <= 1}
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
                    disabled={uiLocked || previewLoading || !canGoPrev}
                  >
                    Prev
                  </button>
                )}
                {previewActive && (
                  <button
                    className="preview-btn preview-btn--secondary"
                    onClick={handleNextPage}
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
                Exact count is still pending. Page selection will unlock when
                the background count completes.
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
                  expanded={Boolean(expandedById[row.id])}
                  body={entryDetailsById[row.id]}
                  fullRaw={entryRawById[row.id]}
                  loadingBody={Boolean(entryDetailsLoadingById[row.id])}
                  loadingRaw={Boolean(entryRawLoadingById[row.id])}
                  onLoadBody={() => handleLoadBody(row.id)}
                  onLoadRaw={() => handleLoadFullRaw(row.id)}
                  onCollapse={() => handleCollapseBody(row.id)}
                  onExpandAll={() => handleExpandAllJsonTree(row.id)}
                  onCopy={() => handleCopyRawLine(row.id)}
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
