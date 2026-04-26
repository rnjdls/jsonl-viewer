import { useState, useMemo, useCallback } from "react";
import { entryMatchesFilters, isFilterActive } from "../utils/search";
import { FILTERS_OP, FILTER_TYPE } from "../constants";

let _nextId = 1;
const nextId = () => String(_nextId++);

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
  const addTextFilter = useCallback(
    () => setFilters((prev) => [...prev, makeTextFilter()]),
    []
  );

  const updateFilter = useCallback((id, patch) => {
    setFilters((prev) =>
      prev.map((f) => (f.id === id ? { ...f, ...patch } : f))
    );
  }, []);

  const removeFilter = useCallback((id) => {
    setFilters((prev) => prev.filter((filter) => filter.id !== id));
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
    filtersOp,
    activeFilters,
    appliedFilters,
    filtered,
    hasFilters: filters.length > 0,
    hasActiveFilters: activeFilters.length > 0,
    hasAppliedFilters: appliedFilters.length > 0,
    addTextFilter,
    setFiltersOp,
    updateFilter,
    removeFilter,
    clearAllFilters,
    applyFilters,
  };
}
