import {
  FIELD_FILTER_OP,
  FIELD_FILTER_OP_OPTIONS,
  FILTERS_OP,
  FILTER_TYPE,
} from "../../constants";
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
 *   filtersOp:            import("../../utils/search").FiltersOp,
 *   loading:              boolean,
 *   countStatus:          "pending" | "ready",
 *   globalDisabled:       boolean,
 *   onAddFieldFilter:     () => void,
 *   onAddTextFilter:      () => void,
 *   onAddTimestampFilter: () => void,
 *   onFiltersOpChange:    (op: import("../../utils/search").FiltersOp) => void,
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
  filtersOp,
  loading,
  countStatus,
  globalDisabled = false,
  onAddFieldFilter,
  onAddTextFilter,
  onAddTimestampFilter,
  onFiltersOpChange,
  onUpdateFilter,
  onRemoveFilter,
  onClearAll,
  onSearch,
}) {
  const visibleFilters = filters.filter((filter) => !filter.hidden);
  const canSearch = hasFilters || hasAppliedFilters;
  const operatorToggleDisabled = globalDisabled || activeCount <= 1;
  const hasVisibleTimestampFilter = visibleFilters.some(
    (filter) => filter.type === FILTER_TYPE.TIMESTAMP
  );

  return (
    <div className="sb">
      {/* ── Toolbar row ──────────────────────────────────── */}
      <div className="sb-toolbar">
        <span className="sb-toolbar-label">Filters</span>

        <button
          className="sb-add-btn"
          onClick={onAddFieldFilter}
          title="Add field filter"
          disabled={globalDisabled}
        >
          + Field
        </button>
        <button
          className="sb-add-btn sb-add-btn--text"
          onClick={onAddTextFilter}
          title="Add full-text filter"
          disabled={globalDisabled}
        >
          + Text
        </button>
        <button
          className="sb-add-btn sb-add-btn--ts"
          onClick={onAddTimestampFilter}
          title={hasVisibleTimestampFilter ? "Only one timestamp range filter is allowed" : "Add timestamp range filter"}
          disabled={globalDisabled || hasVisibleTimestampFilter}
        >
          + Timestamp Range
        </button>

        <div className="sb-match-toggle" role="group" aria-label="Filter match operator">
          <span className="sb-match-label">Match</span>
          <button
            className={`sb-match-btn ${filtersOp === FILTERS_OP.AND ? "is-active" : ""}`}
            onClick={() => onFiltersOpChange(FILTERS_OP.AND)}
            disabled={operatorToggleDisabled}
            type="button"
          >
            All (AND)
          </button>
          <button
            className={`sb-match-btn ${filtersOp === FILTERS_OP.OR ? "is-active" : ""}`}
            onClick={() => onFiltersOpChange(FILTERS_OP.OR)}
            disabled={operatorToggleDisabled}
            type="button"
          >
            Any (OR)
          </button>
        </div>

        {visibleFilters.length > 0 && (
          <button className="sb-clear-all" onClick={onClearAll} disabled={globalDisabled}>
            ✕ Clear all
          </button>
        )}

        <button
          className="sb-search-btn"
          onClick={onSearch}
          disabled={globalDisabled || !canSearch || loading}
        >
          Search
        </button>

        <span className="sb-count">
          {loading ? (
            <span>Loading counts...</span>
          ) : countStatus === "pending" ? (
            <span>Calculating exact count...</span>
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
      {visibleFilters.length > 0 && (
        <div className="sb-filters">
          {visibleFilters.map((filter) =>
            filter.type === FILTER_TYPE.FIELD
              ? (
                <FieldFilterRow
                  key={filter.id}
                  filter={filter}
                  disabled={globalDisabled}
                  onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                  onRemove={() => onRemoveFilter(filter.id)}
                />
              )
              : filter.type === FILTER_TYPE.TEXT
                ? (
                  <TextFilterRow
                    key={filter.id}
                    filter={filter}
                    disabled={globalDisabled}
                    onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                    onRemove={() => onRemoveFilter(filter.id)}
                  />
                )
                : (
                  <TimestampFilterRow
                    key={filter.id}
                    filter={filter}
                    disabled={globalDisabled}
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

function FieldFilterRow({ filter, onUpdate, onRemove, disabled = false }) {
  const op = filter.op || FIELD_FILTER_OP.CONTAINS;
  const isContainsOp = op === FIELD_FILTER_OP.CONTAINS;
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
        disabled={disabled}
      />

      <select
        className="sb-select sb-select--field-op"
        value={op}
        onChange={(e) => onUpdate({ op: e.target.value })}
        aria-label="Field operation"
        disabled={disabled}
      >
        {FIELD_FILTER_OP_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      {isContainsOp && (
        <input
          className="sb-input sb-input--value"
          type="text"
          placeholder="value (partial match)"
          value={filter.value}
          onChange={(e) => onUpdate({ value: e.target.value })}
          spellCheck={false}
          aria-label="Match value"
          disabled={disabled}
        />
      )}

      <button className="sb-remove" onClick={onRemove} aria-label="Remove filter" disabled={disabled}>
        ✕
      </button>
    </div>
  );
}

/* ── TextFilterRow ─────────────────────────────────────── */

function TextFilterRow({ filter, onUpdate, onRemove, disabled = false }) {
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
        disabled={disabled}
      />

      <button className="sb-remove" onClick={onRemove} aria-label="Remove filter" disabled={disabled}>
        ✕
      </button>
    </div>
  );
}

/* ── TimestampFilterRow ─────────────────────────────────── */

function TimestampFilterRow({ filter, onUpdate, onRemove, disabled = false }) {
  const placeholder = "headers.timestampField";
  const fromPlaceholder = "2026-04-06T13:23:58.801145590Z";
  const toPlaceholder = "2026-04-06T13:23:58+08:00 or 1712560000";
  const epochHint = "Epoch examples: 1712560000 / 1712560000000";
  return (
    <div className="sb-row sb-row--timestamp">
      <span className="sb-row-type sb-row-type--ts">TIME</span>

      <input
        className="sb-input sb-input--ts-field"
        type="text"
        placeholder={placeholder}
        value={filter.field}
        onChange={(e) => onUpdate({ field: e.target.value })}
        spellCheck={false}
        aria-label="Timestamp field path"
        disabled={disabled}
      />

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
        disabled={disabled}
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
        disabled={disabled}
      />

      <button className="sb-remove" onClick={onRemove} aria-label="Remove filter" disabled={disabled}>
        ✕
      </button>
    </div>
  );
}
