import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
 *   searchStatus: string,
 *   ingestPaused: boolean,
 *   onReload: () => void,
 *   onReset: () => void,
 *   onPauseToggle: () => void,
 *   resetLoading: boolean,
 *   reloadLoading: boolean,
 *   pauseToggleLoading: boolean,
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
  searchStatus,
  ingestPaused,
  onReload,
  onReset,
  onPauseToggle,
  resetLoading,
  reloadLoading,
  pauseToggleLoading,
  timeZone,
  onTimeZoneChange,
}) {
  const [now, setNow] = useState(() => new Date());
  const [menuOpen, setMenuOpen] = useState(false);
  const menuContainerRef = useRef(null);
  const menuTriggerRef = useRef(null);

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

  const closeMenu = useCallback(({ focusTrigger = false } = {}) => {
    setMenuOpen(false);
    if (focusTrigger) {
      window.requestAnimationFrame(() => {
        menuTriggerRef.current?.focus();
      });
    }
  }, []);

  const toggleMenu = useCallback(() => {
    setMenuOpen((isOpen) => !isOpen);
  }, []);

  const handleMenuActionClick = useCallback(
    (event, action) => {
      const closedFromKeyboard = event.detail === 0;
      closeMenu({ focusTrigger: closedFromKeyboard });
      action();
    },
    [closeMenu]
  );

  useEffect(() => {
    if (!menuOpen) {
      return;
    }

    const handleWindowMouseDown = (event) => {
      const target = event.target;
      if (menuContainerRef.current && !menuContainerRef.current.contains(target)) {
        closeMenu();
      }
    };

    const handleWindowKeyDown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeMenu({ focusTrigger: true });
      }
    };

    window.addEventListener("mousedown", handleWindowMouseDown);
    window.addEventListener("keydown", handleWindowKeyDown);

    return () => {
      window.removeEventListener("mousedown", handleWindowMouseDown);
      window.removeEventListener("keydown", handleWindowKeyDown);
    };
  }, [closeMenu, menuOpen]);

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
          className={`topbar-btn ${ingestPaused ? "topbar-btn--resume" : "topbar-btn--pause"}`}
          onClick={onPauseToggle}
          disabled={!filePath || pauseToggleLoading}
        >
          {pauseToggleLoading ? "Working..." : ingestPaused ? "Resume" : "Pause"}
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
        <span className="topbar-chip">
          search <strong>{searchStatus}</strong>
        </span>
        <span className={`topbar-chip ${ingestPaused ? "topbar-chip--paused" : "topbar-chip--running"}`}>
          ingest <strong>{ingestPaused ? "paused" : "running"}</strong>
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

      <div className="topbar-menu" ref={menuContainerRef}>
        <button
          type="button"
          ref={menuTriggerRef}
          className="topbar-menu-trigger"
          aria-label="Open admin menu"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-controls="topbar-admin-menu"
          onClick={toggleMenu}
        >
          <span aria-hidden="true">☰</span>
        </button>
        {menuOpen && (
          <div
            id="topbar-admin-menu"
            className="topbar-menu-dropdown"
            role="menu"
            aria-label="Admin actions"
          >
            <button
              type="button"
              role="menuitem"
              className="topbar-menu-item"
              onClick={(event) => handleMenuActionClick(event, onReload)}
              disabled={!filePath || reloadLoading}
            >
              {reloadLoading ? "Reloading..." : "Reload File"}
            </button>
            <button
              type="button"
              role="menuitem"
              className="topbar-menu-item topbar-menu-item--danger"
              onClick={(event) => handleMenuActionClick(event, onReset)}
              disabled={!filePath || resetLoading}
            >
              {resetLoading ? "Resetting..." : "Delete All"}
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
