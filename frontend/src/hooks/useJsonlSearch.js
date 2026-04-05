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
 * Creates a blank full-text filter.
 * @returns {{ id: string, type: "text", query: string }}
 */
const makeTextFilter = () => ({
  id: nextId(),
  type: FILTER_TYPE.TEXT,
  query: "",
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
        : lines.filter((entry) => entryMatchesAllFilters(entry, appliedFilters)),
    [lines, appliedFilters]
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
    setAppliedFilterIds((prev) => prev.filter((filterId) => filterId !== id));
  }, []);

  const clearAllFilters = useCallback(() => {
    setFilters([]);
    setAppliedFilterIds([]);
  }, []);

  const applyFilters = useCallback(() => {
    setAppliedFilterIds(activeFilters.map((filter) => filter.id));
  }, [activeFilters]);

  return {
    filters,
    activeFilters,
    appliedFilters,
    filtered,
    hasFilters:       filters.length > 0,
    hasActiveFilters: activeFilters.length > 0,
    hasAppliedFilters: appliedFilters.length > 0,
    addFieldFilter,
    addTextFilter,
    addTimestampFilter,
    updateFilter,
    removeFilter,
    clearAllFilters,
    applyFilters,
  };
}
