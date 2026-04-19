import { FILTERS_OP, FILTER_TYPE } from "../constants";

/**
 * @typedef {Object} TextFilter
 * @property {string} id
 * @property {"text"} type
 * @property {string} query
 */

/** @typedef {TextFilter} Filter */
/** @typedef {"and" | "or"} FiltersOp */

function toSearchableText(value) {
  if (typeof value === "string") return value;
  if (value === undefined) return "";
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function normalizeFiltersOp(rawOp) {
  const normalized = String(rawOp ?? "")
    .trim()
    .toLowerCase();
  return normalized === FILTERS_OP.OR ? FILTERS_OP.OR : FILTERS_OP.AND;
}

/**
 * Returns true if a single entry satisfies a single filter.
 *
 * @param {import("./jsonl").JsonlEntry} entry
 * @param {Filter} filter
 * @returns {boolean}
 */
function entryMatchesFilter(entry, filter) {
  if (!entry.parsed) return true; // Always show parse errors.

  if (filter.type === FILTER_TYPE.TEXT) {
    const query = (filter.query ?? "").trim().toLowerCase();
    if (!query) return true;
    return toSearchableText(entry.parsed).toLowerCase().includes(query);
  }

  return true;
}

/**
 * Returns true when the entry satisfies active filters based on the provided
 * boolean operator.
 *
 * @param {import("./jsonl").JsonlEntry} entry
 * @param {Filter[]} filters
 * @param {FiltersOp} filtersOp
 * @returns {boolean}
 */
export function entryMatchesFilters(entry, filters, filtersOp = FILTERS_OP.AND) {
  if (filters.length === 0) return true;
  const normalizedOp = normalizeFiltersOp(filtersOp);
  if (normalizedOp === FILTERS_OP.OR) {
    return filters.some((f) => entryMatchesFilter(entry, f));
  }
  return filters.every((f) => entryMatchesFilter(entry, f));
}

export function entryMatchesAllFilters(entry, filters) {
  return entryMatchesFilters(entry, filters, FILTERS_OP.AND);
}

/**
 * Returns true when a filter has enough information to actually constrain
 * results (i.e. it isn't an empty/blank placeholder).
 *
 * @param {Filter} filter
 * @returns {boolean}
 */
export function isFilterActive(filter) {
  if (filter.hidden) {
    return false;
  }
  if (filter.type === FILTER_TYPE.TEXT) {
    return (filter.query ?? "").trim().length > 0;
  }
  return false;
}
