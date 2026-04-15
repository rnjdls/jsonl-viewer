import { useEffect, useMemo, useState } from "react";
import {
  TIMEZONE_LOCAL,
  coerceTimeZoneOrLocal,
  formatDateTimeTooltip,
  formatTime,
} from "../../utils/datetime";
import "./TopBar.css";

const FALLBACK_TIME_ZONES = [
  "America/New_York",
  "America/Los_Angeles",
  "Europe/London",
  "Europe/Berlin",
  "Asia/Manila",
  "Asia/Singapore",
  "Asia/Tokyo",
  "Australia/Sydney",
];

function getSelectableTimeZones() {
  if (typeof Intl.supportedValuesOf === "function") {
    try {
      const supported = Intl.supportedValuesOf("timeZone");
      if (Array.isArray(supported) && supported.length > 0) {
        return supported;
      }
    } catch {
      // Fall through to fallback list.
    }
  }
  return FALLBACK_TIME_ZONES;
}

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
 *   sourceRevision: number,
 *   searchStatus: string,
 *   onReload: () => void,
 *   onReset: () => void,
 *   resetLoading: boolean,
 *   reloadLoading: boolean,
 *   timeZone: string,
 *   onTimeZoneChange: (timeZone: string) => void,
 * }} props
 */
export function TopBar({
  filePath,
  lastIngestedAt,
  totalCount,
  parsedCount,
  errorCount,
  timestampField,
  sourceRevision,
  searchStatus,
  onReload,
  onReset,
  resetLoading,
  reloadLoading,
  timeZone,
  onTimeZoneChange,
}) {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timerId = window.setInterval(() => {
      setNow(new Date());
    }, 1000);
    return () => window.clearInterval(timerId);
  }, []);

  const resolvedLocalTimeZone = useMemo(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || "System",
    []
  );

  const selectedTimeZone = coerceTimeZoneOrLocal(timeZone);

  const timeZoneOptions = useMemo(() => {
    const normalized = [...new Set(getSelectableTimeZones())].sort((left, right) =>
      left.localeCompare(right)
    );
    const withoutUtc = normalized.filter((tz) => tz !== "UTC");
    return [
      { value: TIMEZONE_LOCAL, label: `Local (${resolvedLocalTimeZone})` },
      { value: "UTC", label: "UTC" },
      ...withoutUtc.map((tz) => ({ value: tz, label: tz })),
    ];
  }, [resolvedLocalTimeZone]);

  const handleTimeZoneSelectChange = (event) => {
    onTimeZoneChange(event.target.value);
  };

  return (
    <header className="topbar">
      <span className="topbar-logo">
        JSONL<span className="topbar-logo-dot">·</span>Live
      </span>

      <div className="topbar-source">
        <span className="topbar-label">Source</span>
        <span className="topbar-source-value" title={filePath || "No source configured"}>
          {filePath || "No source configured"}
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
        <span className="topbar-chip">
          rev <strong>{sourceRevision}</strong>
        </span>
        <span className="topbar-chip">
          search <strong>{searchStatus}</strong>
        </span>
        {lastIngestedAt && (
          <span
            className="topbar-chip"
            title={formatDateTimeTooltip(lastIngestedAt, selectedTimeZone)}
          >
            updated <strong>{formatTime(lastIngestedAt, selectedTimeZone)}</strong>
          </span>
        )}
        <span className="topbar-chip" title={formatDateTimeTooltip(now, selectedTimeZone)}>
          now <strong>{formatTime(now, selectedTimeZone)}</strong>
        </span>
        <span className="topbar-chip topbar-chip--tz">
          tz
          <select
            className="topbar-chip-select"
            value={selectedTimeZone}
            onChange={handleTimeZoneSelectChange}
            aria-label="Timezone"
          >
            {timeZoneOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </span>
      </div>
    </header>
  );
}
