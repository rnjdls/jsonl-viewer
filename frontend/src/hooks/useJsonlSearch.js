import { useState, useMemo, useCallback } from "react";
import { entryMatchesFilters, isFilterActive } from "../utils/search";
import { FIELD_FILTER_OP, FILTERS_OP, FILTER_TYPE } from "../constants";

let _nextId = 1;
const nextId = () => String(_nextId++);
const DEFAULT_TIMESTAMP_FIELD = "timestamp";

/**
 * Creates a blank field filter.
 * @returns {import("../utils/search").FieldFilter}
 */
const makeFieldFilter = () => ({
  id:    nextId(),
  type:  FILTER_TYPE.FIELD,
  op:    FIELD_FILTER_OP.CONTAINS,
  field: "",
  value: "",
});

/**
 * Creates a blank full-text filter.
 * @returns {{ id: string, type: "text", query: string }}
 */
const makeTextFilter = () => ({
  id: nextId(),
  type: FILTER_TYPE.TEXT,
  query: "",
});

/**
 * Creates a blank timestamp range filter.
 * @returns {{ id: string, type: "timestamp", field: string, from: string, to: string }}
 */
const makeTimestampFilter = (id = nextId(), hidden = false, field = DEFAULT_TIMESTAMP_FIELD) => ({
  id,
  type: FILTER_TYPE.TIMESTAMP,
  field,
  from: "",
  to: "",
  hidden,
});

/**
 * Manages the collection of active filters and derives a filtered entry list.
 *
 * @param {import("../utils/jsonl").JsonlEntry[]} lines
 */
export function useJsonlSearch(lines) {
  const [filters, setFilters] = useState([]);
  const [filtersOp, setFiltersOp] = useState(FILTERS_OP.AND);
  const [appliedFilterIds, setAppliedFilterIds] = useState([]);

  /* ── Derived state ───────────────────────────────────── */
  const activeFilters = useMemo(() => filters.filter(isFilterActive), [filters]);
  const appliedFilters = useMemo(
    () => activeFilters.filter((filter) => appliedFilterIds.includes(filter.id)),
    [activeFilters, appliedFilterIds]
  );

  const filtered = useMemo(
    () =>
      appliedFilters.length === 0
        ? lines
        : lines.filter((entry) => entryMatchesFilters(entry, appliedFilters, filtersOp)),
    [lines, appliedFilters, filtersOp]
  );

  /* ── Mutators ────────────────────────────────────────── */
  const addFieldFilter = useCallback(
    () => setFilters((prev) => [...prev, makeFieldFilter()]),
    []
  );

  const addTextFilter = useCallback(
    () => setFilters((prev) => [...prev, makeTextFilter()]),
    []
  );

  const addTimestampFilter = useCallback(
    () =>
      setFilters((prev) => {
        const existingTimestampFilter = prev.find(
          (filter) => filter.type === FILTER_TYPE.TIMESTAMP
        );
        if (existingTimestampFilter) {
          if (existingTimestampFilter.hidden) {
            return prev.map((filter) =>
              filter.id === existingTimestampFilter.id
                ? { ...filter, hidden: false, field: "" }
                : filter
            );
          }
          return prev;
        }
        return [...prev, makeTimestampFilter(nextId(), false, "")];
      }),
    []
  );

  const updateFilter = useCallback((id, patch) => {
    setFilters((prev) =>
      prev.map((f) => (f.id === id ? { ...f, ...patch } : f))
    );
  }, []);

  const removeFilter = useCallback((id) => {
    setFilters((prev) => {
      const target = prev.find((filter) => filter.id === id);
      if (target?.type === FILTER_TYPE.TIMESTAMP) {
        return prev.map((filter) =>
          filter.id === id ? makeTimestampFilter(id, true) : filter
        );
      }
      return prev.filter((filter) => filter.id !== id);
    });
    setAppliedFilterIds((prev) => prev.filter((filterId) => filterId !== id));
  }, []);

  const clearAllFilters = useCallback(() => {
    setFilters((prev) => {
      const timestampFilter = prev.find((filter) => filter.type === FILTER_TYPE.TIMESTAMP);
      if (!timestampFilter) {
        return [];
      }
      return [makeTimestampFilter(timestampFilter.id, true)];
    });
    setAppliedFilterIds([]);
  }, []);

  const applyFilters = useCallback(() => {
    setAppliedFilterIds(activeFilters.map((filter) => filter.id));
  }, [activeFilters]);

  return {
    filters,
    filtersOp,
    activeFilters,
    appliedFilters,
    filtered,
    hasFilters:       filters.length > 0,
    hasActiveFilters: activeFilters.length > 0,
    hasAppliedFilters: appliedFilters.length > 0,
    addFieldFilter,
    addTextFilter,
    addTimestampFilter,
    setFiltersOp,
    updateFilter,
    removeFilter,
    clearAllFilters,
    applyFilters,
  };
}
