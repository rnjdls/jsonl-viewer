import { useState, useMemo, useCallback } from "react";
import { entryMatchesAllFilters, isFilterActive } from "../utils/search";
import { FILTER_TYPE } from "../constants";

let _nextId = 1;
const nextId = () => String(_nextId++);

/**
 * Creates a blank field filter.
 * @returns {import("../utils/search").FieldFilter}
 */
const makeFieldFilter = () => ({
  id:    nextId(),
  type:  FILTER_TYPE.FIELD,
  field: "",
  value: "",
});

/**
 * Manages the collection of active filters and derives a filtered entry list.
 *
 * All filters are combined with AND logic: an entry must satisfy every active
 * filter to appear in the results.
 *
 * @param {import("../utils/jsonl").JsonlEntry[]} lines
 * @param {{ timestampField?: string }} options
 */
export function useJsonlSearch(lines, options = {}) {
  const defaultTimestampField = options.timestampField || "timestamp";
  const [filters, setFilters] = useState([]);

  /* ── Derived state ───────────────────────────────────── */
  const activeFilters = useMemo(() => filters.filter(isFilterActive), [filters]);

  const filtered = useMemo(
    () =>
      activeFilters.length === 0
        ? lines
        : lines.filter((entry) => entryMatchesAllFilters(entry, activeFilters)),
    [lines, activeFilters]
  );

  /* ── Mutators ────────────────────────────────────────── */
  const addFieldFilter = useCallback(
    () => setFilters((prev) => [...prev, makeFieldFilter()]),
    []
  );

  const addTimestampFilter = useCallback(
    () =>
      setFilters((prev) => [
        ...prev,
        {
          id: nextId(),
          type: FILTER_TYPE.TIMESTAMP,
          field: defaultTimestampField,
          from: "",
          to: "",
        },
      ]),
    [defaultTimestampField]
  );

  const updateFilter = useCallback((id, patch) => {
    setFilters((prev) =>
      prev.map((f) => (f.id === id ? { ...f, ...patch } : f))
    );
  }, []);

  const removeFilter = useCallback((id) => {
    setFilters((prev) => prev.filter((f) => f.id !== id));
  }, []);

  const clearAllFilters = useCallback(() => setFilters([]), []);

  return {
    filters,
    activeFilters,
    filtered,
    hasFilters:       filters.length > 0,
    hasActiveFilters: activeFilters.length > 0,
    addFieldFilter,
    addTimestampFilter,
    updateFilter,
    removeFilter,
    clearAllFilters,
  };
}
