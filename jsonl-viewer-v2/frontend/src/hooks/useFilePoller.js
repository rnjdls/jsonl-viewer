import { useState, useEffect, useRef, useCallback } from "react";
import { parseJsonlText } from "../utils/jsonl";
import { POLL_INTERVAL_MS } from "../constants";

/**
 * @typedef {Object} FilePollerState
 * @property {import("../utils/jsonl").JsonlEntry[]} lines   - All parsed JSONL lines.
 * @property {string}       fileName     - Name of the open file.
 * @property {Date|null}    lastRead     - Timestamp of the most recent read.
 * @property {boolean}      liveMode     - Whether live polling is active.
 * @property {boolean}      polling      - Whether the interval is currently running.
 * @property {string}       error        - Last file-read error message, or "".
 * @property {boolean}      hasFile      - Whether a file is currently open.
 * @property {boolean}      supportsApi  - Whether File System Access API is available.
 * @property {() => void}   openFile     - Opens a file via the native picker.
 * @property {Function}     openFallback - onChange handler for a hidden <input type="file">.
 * @property {() => void}   toggleLive   - Toggles live polling on/off.
 */

/**
 * Manages opening a JSONL file, reading it on an interval, and parsing its
 * lines.  Uses the File System Access API when available so the file handle
 * can be held open and re-read as the file grows.  Falls back to a standard
 * FileReader for browsers / environments without the API.
 *
 * @returns {FilePollerState}
 */
export function useFilePoller() {
  const [fileHandle, setFileHandle] = useState(null);
  const [fileName, setFileName]     = useState("");
  const [lines, setLines]           = useState([]);
  const [lastRead, setLastRead]     = useState(null);
  const [liveMode, setLiveMode]     = useState(true);
  const [polling, setPolling]       = useState(false);
  const [error, setError]           = useState("");

  const intervalRef = useRef(null);

  const supportsApi =
    typeof window !== "undefined" && "showOpenFilePicker" in window;

  /* ── Core read function ──────────────────────────────── */
  const readHandle = useCallback(async (handle) => {
    try {
      const file = await handle.getFile();
      const text = await file.text();
      setLines(parseJsonlText(text));
      setLastRead(new Date());
      setError("");
    } catch (err) {
      setError(`Failed to read file: ${err.message}`);
    }
  }, []);

  /* ── Polling lifecycle ───────────────────────────────── */
  useEffect(() => {
    clearInterval(intervalRef.current);

    if (fileHandle && liveMode) {
      setPolling(true);
      intervalRef.current = setInterval(() => readHandle(fileHandle), POLL_INTERVAL_MS);
    } else {
      setPolling(false);
    }

    return () => clearInterval(intervalRef.current);
  }, [fileHandle, liveMode, readHandle]);

  /* ── File System Access API picker ──────────────────── */
  const openFile = useCallback(async () => {
    try {
      const [handle] = await window.showOpenFilePicker({
        types: [
          {
            description: "JSONL / NDJSON files",
            accept: { "application/json": [".jsonl", ".ndjson", ".json"] },
          },
        ],
      });
      setFileHandle(handle);
      setFileName(handle.name);
      await readHandle(handle);
    } catch (err) {
      if (err.name !== "AbortError") setError(err.message);
    }
  }, [readHandle]);

  /* ── Fallback <input type="file"> handler ────────────── */
  const openFallback = useCallback((e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setFileName(file.name);
    setLiveMode(false); // Can't re-read without a persistent handle.

    const reader = new FileReader();
    reader.onload = (ev) => {
      setLines(parseJsonlText(ev.target.result));
      setLastRead(new Date());
    };
    reader.onerror = () => setError("FileReader failed to read the file.");
    reader.readAsText(file);
  }, []);

  const toggleLive = useCallback(() => setLiveMode((v) => !v), []);

  return {
    lines,
    fileName,
    lastRead,
    liveMode,
    polling,
    error,
    hasFile: !!fileName,
    supportsApi,
    openFile,
    openFallback,
    toggleLive,
  };
}
