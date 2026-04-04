import "./TopBar.css";

/**
 * Sticky application header.
 *
 * Displays the app logo, backend file path, and ingestion stats.
 *
 * @param {{
 *   filePath: string | null,
 *   lastIngestedAt: string | null,
 *   totalCount: number,
 *   parsedCount: number,
 *   errorCount: number,
 *   timestampField: string | null,
 *   onReload: () => void,
 *   onReset: () => void,
 *   resetLoading: boolean,
 *   reloadLoading: boolean,
 * }} props
 */
export function TopBar({
  filePath,
  lastIngestedAt,
  totalCount,
  parsedCount,
  errorCount,
  timestampField,
  onReload,
  onReset,
  resetLoading,
  reloadLoading,
}) {
  return (
    <header className="topbar">
      <span className="topbar-logo">
        JSONL<span className="topbar-logo-dot">·</span>Live
      </span>

      <div className="topbar-source">
        <span className="topbar-label">Source</span>
        <span className="topbar-source-value" title={filePath || "No file configured"}>
          {filePath || "No file configured"}
        </span>
      </div>

      <div className="topbar-actions">
        <button
          className="topbar-btn topbar-btn--reload"
          onClick={onReload}
          disabled={!filePath || reloadLoading}
        >
          {reloadLoading ? "Reloading..." : "Reload File"}
        </button>
        <button
          className="topbar-btn topbar-btn--reset"
          onClick={onReset}
          disabled={!filePath || resetLoading}
        >
          {resetLoading ? "Resetting..." : "Delete All"}
        </button>
      </div>

      <div className="topbar-meta">
        <span className="topbar-chip">
          <strong>{totalCount}</strong> total
        </span>
        <span className="topbar-chip">
          <strong>{parsedCount}</strong> parsed
        </span>
        <span className="topbar-chip">
          <strong>{errorCount}</strong> errors
        </span>
        {timestampField && (
          <span className="topbar-chip">
            ts: <strong>{timestampField}</strong>
          </span>
        )}
        {lastIngestedAt && (
          <span className="topbar-chip">
            updated <strong>{new Date(lastIngestedAt).toLocaleTimeString()}</strong>
          </span>
        )}
      </div>
    </header>
  );
}
