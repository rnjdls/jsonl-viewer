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
 * Renders the list of current filters (field and text filters) and exposes
 * controls to add, edit, and remove them.
 *
 * @param {{
 *   filters:           import("../../utils/search").Filter[],
 *   totalCount:        number,
 *   matchCount:        number,
 *   hasFilters:        boolean,
 *   hasActiveFilters:  boolean,
 *   hasAppliedFilters: boolean,
 *   activeCount:       number,
 *   filtersOp:         import("../../utils/search").FiltersOp,
 *   loading:           boolean,
 *   countStatus:       "pending" | "ready",
 *   globalDisabled:    boolean,
 *   onAddFieldFilter:  () => void,
 *   onAddTextFilter:   () => void,
 *   onFiltersOpChange: (op: import("../../utils/search").FiltersOp) => void,
 *   onUpdateFilter:    (id: string, patch: object) => void,
 *   onRemoveFilter:    (id: string) => void,
 *   onClearAll:        () => void,
 *   onSearch:          () => void,
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
  onFiltersOpChange,
  onUpdateFilter,
  onRemoveFilter,
  onClearAll,
  onSearch,
}) {
  const visibleFilters = filters.filter((filter) => !filter.hidden);
  const canSearch = hasFilters || hasAppliedFilters;
  const operatorToggleDisabled = globalDisabled || activeCount <= 1;

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
          {visibleFilters.map((filter) => {
            if (filter.type === FILTER_TYPE.FIELD) {
              return (
                <FieldFilterRow
                  key={filter.id}
                  filter={filter}
                  disabled={globalDisabled}
                  onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                  onRemove={() => onRemoveFilter(filter.id)}
                />
              );
            }
            if (filter.type === FILTER_TYPE.TEXT) {
              return (
                <TextFilterRow
                  key={filter.id}
                  filter={filter}
                  disabled={globalDisabled}
                  onUpdate={(patch) => onUpdateFilter(filter.id, patch)}
                  onRemove={() => onRemoveFilter(filter.id)}
                />
              );
            }
            return null;
          })}
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
        placeholder="header/header leaf key (e.g. eventTime)"
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
          placeholder="value in indexed header fields"
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
