import { FILTER_TYPE, COMMON_TIMESTAMP_FIELDS } from "../../constants";
import "./SearchBar.css";

/**
 * Multi-filter search bar.
 *
 * Renders the list of current filters (field, text, and timestamp-range
 * filters) and exposes controls to add, edit, and remove them.
 *
 * @param {{
 *   filters:              import("../../utils/search").Filter[],
 *   totalCount:           number,
 *   matchCount:           number,
 *   hasFilters:           boolean,
 *   hasActiveFilters:     boolean,
 *   hasAppliedFilters:    boolean,
 *   activeCount:          number,
 *   loading:              boolean,
 *   timestampField?:      string,
 *   onAddFieldFilter:     () => void,
 *   onAddTextFilter:      () => void,
 *   onAddTimestampFilter: () => void,
 *   onUpdateFilter:       (id: string, patch: object) => void,
 *   onRemoveFilter:       (id: string) => void,
 *   onClearAll:           () => void,
 *   onSearch:             () => void,
 * }} props
 */
export function SearchBar({
  filters,
  totalCount,
  matchCount,
  hasFilters,
  hasActiveFilters,
  hasAppliedFilters,
  activeCount,
  loading,
  timestampField,
  onAddFieldFilter,
  onAddTextFilter,
  onAddTimestampFilter,
  onUpdateFilter,
  onRemoveFilter,
  onClearAll,
  onSearch,
}) {
  const canSearch = hasFilters || hasAppliedFilters;

  return (
    <div className="sb">
      {/* ── Toolbar row ──────────────────────────────────── */}
      <div className="sb-toolbar">
        <span className="sb-toolbar-label">Filters</span>

        <button className="sb-add-btn" onClick={onAddFieldFilter} title="Add field filter">
          + Field
        </button>
        <button className="sb-add-btn sb-add-btn--text" onClick={onAddTextFilter} title="Add full-text filter">
          + Text
        </button>
        <button className="sb-add-btn sb-add-btn--ts" onClick={onAddTimestampFilter} title="Add timestamp range filter">
          + Timestamp Range
        </button>

        {hasFilters && (
          <button className="sb-clear-all" onClick={onClearAll}>
            ✕ Clear all
          </button>
        )}

        <button className="sb-search-btn" onClick={onSearch} disabled={!canSearch || loading}>
          Search
        </button>

        <span className="sb-count">
          {loading ? (
            <span>Loading counts...</span>
          ) : (
            <>
              <strong>{matchCount}</strong> / {totalCount} lines
            </>
          )}
          {hasActiveFilters && (
            <span className="sb-count-badge">{activeCount} active</span>
          )}
        </span>
      </div>

      {/* ── Filter rows ──────────────────────────────────── */}
      {filters.length > 0 && (
        <div className="sb-filters">
          {filters.map((filter) =>
            filter.type === FILTER_TYPE.FIELD
              ? (
                <FieldFilterRow
                  key={filter.id}
                  filter={filter}
                  onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                  onRemove={() => onRemoveFilter(filter.id)}
                />
              )
              : filter.type === FILTER_TYPE.TEXT
                ? (
                  <TextFilterRow
                    key={filter.id}
                    filter={filter}
                    onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                    onRemove={() => onRemoveFilter(filter.id)}
                  />
                )
                : (
                  <TimestampFilterRow
                    key={filter.id}
                    filter={filter}
                    timestampField={timestampField}
                    onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                    onRemove={() => onRemoveFilter(filter.id)}
                  />
                )
          )}
        </div>
      )}
    </div>
  );
}

/* ── FieldFilterRow ─────────────────────────────────────── */

function FieldFilterRow({ filter, onUpdate, onRemove }) {
  return (
    <div className="sb-row sb-row--field">
      <span className="sb-row-type sb-row-type--field">FIELD</span>

      <input
        className="sb-input sb-input--field"
        type="text"
        placeholder="field key"
        value={filter.field}
        onChange={(e) => onUpdate({ field: e.target.value })}
        spellCheck={false}
        aria-label="Field key"
      />

      <span className="sb-row-eq">=</span>

      <input
        className="sb-input sb-input--value"
        type="text"
        placeholder="value (partial match)"
        value={filter.value}
        onChange={(e) => onUpdate({ value: e.target.value })}
        spellCheck={false}
        aria-label="Match value"
      />

      <button className="sb-remove" onClick={onRemove} aria-label="Remove filter">✕</button>
    </div>
  );
}

/* ── TextFilterRow ─────────────────────────────────────── */

function TextFilterRow({ filter, onUpdate, onRemove }) {
  return (
    <div className="sb-row sb-row--text">
      <span className="sb-row-type sb-row-type--text">TEXT</span>

      <input
        className="sb-input sb-input--text-query"
        type="text"
        placeholder="search parsed JSON text"
        value={filter.query}
        onChange={(e) => onUpdate({ query: e.target.value })}
        spellCheck={false}
        aria-label="Full text query"
      />

      <button className="sb-remove" onClick={onRemove} aria-label="Remove filter">✕</button>
    </div>
  );
}

/* ── TimestampFilterRow ─────────────────────────────────── */

function TimestampFilterRow({ filter, timestampField, onUpdate, onRemove }) {
  const placeholder = timestampField ? `${timestampField} (server)` : "timestamp field";
  const fromPlaceholder = "2026-04-06T13:23:58.801145590Z";
  const toPlaceholder = "2026-04-06T13:23:58+08:00 or 1712560000";
  const epochHint = "Epoch examples: 1712560000 / 1712560000000";
  return (
    <div className="sb-row sb-row--timestamp">
      <span className="sb-row-type sb-row-type--ts">TIME</span>

      {/* Field selector — datalist gives quick suggestions */}
      <input
        className="sb-input sb-input--ts-field"
        type="text"
        placeholder={placeholder}
        list="ts-field-suggestions"
        value={filter.field}
        onChange={(e) => onUpdate({ field: e.target.value })}
        spellCheck={false}
        aria-label="Timestamp field path"
      />
      <datalist id="ts-field-suggestions">
        {COMMON_TIMESTAMP_FIELDS.map((f) => (
          <option key={f} value={f} />
        ))}
      </datalist>

      <span className="sb-row-between">between</span>

      <input
        className="sb-input sb-input--datetime"
        type="text"
        placeholder={fromPlaceholder}
        title={epochHint}
        value={filter.from}
        onChange={(e) => onUpdate({ from: e.target.value })}
        spellCheck={false}
        aria-label="From timestamp"
      />

      <span className="sb-row-and">and</span>

      <input
        className="sb-input sb-input--datetime"
        type="text"
        placeholder={toPlaceholder}
        title={epochHint}
        value={filter.to}
        onChange={(e) => onUpdate({ to: e.target.value })}
        spellCheck={false}
        aria-label="To timestamp"
      />

      <button className="sb-remove" onClick={onRemove} aria-label="Remove filter">✕</button>
    </div>
  );
}
