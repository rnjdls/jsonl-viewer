import { enrichWithDecodedHeaders } from "../../utils/jsonl";
import { getByPath } from "../../utils/search";
import { LEVEL_STYLES, STATUS_COLOR } from "../../constants";
import { JsonValue } from "../JsonValue/JsonValue";
import "./JsonCard.css";

/**
 * Renders a single parsed JSONL line as an expandable card.
 *
 * Enriches the JSON object with decoded base64 header annotations before
 * passing it to the recursive <JsonValue> renderer.
 *
 * @param {{
 *   entry: import("../../utils/jsonl").JsonlEntry,
 *   searchField: string
 * }} props
 */
export function JsonCard({ entry, searchField }) {
  const enriched = entry.parsed ? enrichWithDecodedHeaders(entry.parsed) : null;
  const lineLabel = entry.lineNo ?? entry.id;

  const level  = entry.parsed?.level;
  const status = entry.parsed?.response?.status;
  const method = entry.parsed?.request?.method;
  const path   = entry.parsed?.request?.path;

  const levelStyle  = LEVEL_STYLES[level] ?? null;
  const statusColor = status ? STATUS_COLOR(status) : null;

  const searchedValue =
    searchField && entry.parsed
      ? String(getByPath(entry.parsed, searchField) ?? "—")
      : null;

  return (
    <article className="card">
      {/* ── Header row ─────────────────────────────────── */}
      <header className="card-header">
        <span className="card-line-num">LINE {lineLabel}</span>

        {levelStyle && (
          <span className="card-badge" style={levelStyle}>
            {level}
          </span>
        )}

        {entry.parsed?.service && (
          <span className="card-badge card-badge--service">
            {entry.parsed.service}
          </span>
        )}

        {method && path && (
          <span className="card-badge card-badge--route">
            {method} {path}
          </span>
        )}

        {!entry.parsed && entry.error && (
          <span className="card-parse-error">PARSE ERROR: {entry.error}</span>
        )}

        {statusColor && (
          <span className="card-status" style={{ color: statusColor }}>
            HTTP {status}
          </span>
        )}

        {searchedValue && (
          <span className="card-search-hint">
            {searchField}: <em>{searchedValue}</em>
          </span>
        )}
      </header>

      {/* ── Body ───────────────────────────────────────── */}
      {entry.parsed ? (
        <div className="card-body">
          <JsonValue val={enriched} depth={0} />
        </div>
      ) : (
        <pre className="card-raw-error">{entry.raw}</pre>
      )}
    </article>
  );
}
