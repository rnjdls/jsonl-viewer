import { JsonValue } from "../JsonValue/JsonValue";
import "./JsonCard.css";

export function JsonCard({
  row,
  expanded,
  body,
  fullRaw,
  loadingBody,
  loadingRaw,
  onLoadBody,
  onLoadRaw,
  onCollapse,
  onExpandAll,
  onCopy,
  copyLabel = "Copy",
  copyDisabled = false,
  expandAllToken = 0,
  globalDisabled = false,
}) {
  const lineLabel = row.lineNo ?? row.id;
  const hasParsedBody = Boolean(body?.parsed);
  const hasParseError = Boolean(body?.error ?? row.error);
  const compactValue = {
    key: row.key ?? null,
    headers: row.headers ?? null,
  };

  const timestampLabel = row.ts
    ? new Date(row.ts).toLocaleString()
    : null;

  return (
    <article className="card">
      <header className="card-header">
        <span className="card-line-num">LINE {lineLabel}</span>
        {timestampLabel && (
          <span className="card-ts">
            {timestampLabel}
          </span>
        )}

        <div className="card-actions">
          <button
            className="card-btn card-btn--secondary"
            onClick={onCopy}
            disabled={globalDisabled || copyDisabled}
            title="Copy raw line"
            aria-label="Copy raw JSON line to clipboard"
          >
            {copyLabel}
          </button>
          {expanded && hasParsedBody && (
            <button
              className="card-btn card-btn--secondary"
              onClick={onExpandAll}
              title="Expand all JSON fields"
              aria-label="Expand all JSON fields"
              disabled={globalDisabled}
            >
              Expand all
            </button>
          )}
          {expanded ? (
            <button className="card-btn card-btn--secondary" onClick={onCollapse} disabled={globalDisabled}>
              Collapse
            </button>
          ) : (
            <button className="card-btn" onClick={onLoadBody} disabled={globalDisabled || loadingBody}>
              {loadingBody ? "Loading body..." : "Load body"}
            </button>
          )}
        </div>
      </header>

      <div className="card-body">
        {!expanded && (
          <div className="card-section">
            <div className="card-section-title">Kafka Preview</div>
            <JsonValue val={compactValue} depth={0} />
          </div>
        )}

        {expanded && hasParsedBody && (
          <div className="card-section">
            <div className="card-section-title">Body</div>
            <JsonValue val={body.parsed} depth={0} expandAllToken={expandAllToken} />
          </div>
        )}

        {hasParseError && (
          <div className="card-error-block">
            <div className="card-section-title card-section-title--error">Parse Error</div>
            <p className="card-parse-error">{body?.error ?? row.error}</p>
            {fullRaw ? (
              <pre className="card-raw-error">{fullRaw}</pre>
            ) : (
              row.rawSnippet && <pre className="card-raw-error">{row.rawSnippet}</pre>
            )}
            {row.rawTruncated && !fullRaw && (
              <button
                className="card-btn card-btn--secondary"
                onClick={onLoadRaw}
                disabled={globalDisabled || loadingRaw}
              >
                {loadingRaw ? "Loading full raw..." : "Load full raw"}
              </button>
            )}
          </div>
        )}
        {expanded && !hasParsedBody && !hasParseError && (
          <div className="card-empty">No parsed body available for this row.</div>
        )}
      </div>
    </article>
  );
}
