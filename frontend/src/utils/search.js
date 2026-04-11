import { FIELD_FILTER_OP, FILTERS_OP, FILTER_TYPE } from "../constants";

/**
 * @typedef {"contains" | "null" | "not_null" | "empty" | "not_empty"} FieldFilterOp
 */

/**
 * @typedef {Object} FieldFilter
 * @property {string} id
 * @property {"field"} type
 * @property {FieldFilterOp} op
 * @property {string} field   - JSON key name to search anywhere in the tree.
 * @property {string} value   - Partial match string.
 */

/**
 * @typedef {Object} TimestampFilter
 * @property {string} id
 * @property {"timestamp"} type
 * @property {string} field   - Dot-notation path to the timestamp field.
 * @property {string} from    - ISO date string (inclusive lower bound).
 * @property {string} to      - ISO date string (inclusive upper bound).
 */

/**
 * @typedef {Object} TextFilter
 * @property {string} id
 * @property {"text"} type
 * @property {string} query
 */

/** @typedef {FieldFilter | TimestampFilter | TextFilter} Filter */
/** @typedef {"and" | "or"} FiltersOp */

/**
 * Reads a value from a nested object using a dot-notation path string.
 * e.g. getByPath({ a: { b: 1 } }, "a.b") => 1
 *
 * @param {Object} obj
 * @param {string} path
 * @returns {*}
 */
export function getByPath(obj, path) {
  return path.split(".").reduce((cur, key) => cur?.[key], obj);
}

function getValuesByKeyAnywhere(node, key, out = []) {
  if (node === null || node === undefined) return out;

  if (Array.isArray(node)) {
    for (const item of node) {
      getValuesByKeyAnywhere(item, key, out);
    }
    return out;
  }

  if (typeof node !== "object") return out;

  for (const [entryKey, entryValue] of Object.entries(node)) {
    if (entryKey === key) {
      out.push(entryValue);
    }
    getValuesByKeyAnywhere(entryValue, key, out);
  }
  return out;
}

function toSearchableText(value) {
  if (typeof value === "string") return value;
  if (value === undefined) return "";
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function normalizeFieldFilterOp(rawOp) {
  const normalized = String(rawOp ?? "")
    .trim()
    .toLowerCase()
    .replace(/[-\s]+/g, "_");

  if (normalized === FIELD_FILTER_OP.NULL) return FIELD_FILTER_OP.NULL;
  if (normalized === FIELD_FILTER_OP.NOT_NULL) return FIELD_FILTER_OP.NOT_NULL;
  if (normalized === FIELD_FILTER_OP.EMPTY) return FIELD_FILTER_OP.EMPTY;
  if (normalized === FIELD_FILTER_OP.NOT_EMPTY) return FIELD_FILTER_OP.NOT_EMPTY;
  return FIELD_FILTER_OP.CONTAINS;
}

function normalizeFiltersOp(rawOp) {
  const normalized = String(rawOp ?? "")
    .trim()
    .toLowerCase();
  return normalized === FILTERS_OP.OR ? FILTERS_OP.OR : FILTERS_OP.AND;
}

function isEmptyFieldValue(value) {
  if (value === "") return true;
  if (Array.isArray(value)) return value.length === 0;
  if (value && typeof value === "object") return Object.keys(value).length === 0;
  return false;
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

  if (filter.type === FILTER_TYPE.FIELD) {
    const { field, value } = filter;
    const fieldKey = field.trim();
    if (!fieldKey) return true;
    const op = normalizeFieldFilterOp(filter.op);

    const values = getValuesByKeyAnywhere(entry.parsed, fieldKey);
    if (values.length === 0) return false;

    if (op === FIELD_FILTER_OP.NULL) {
      return values.some((entryValue) => entryValue === null);
    }

    if (op === FIELD_FILTER_OP.NOT_NULL) {
      return values.some((entryValue) => entryValue !== null);
    }

    if (op === FIELD_FILTER_OP.EMPTY) {
      return values.some((entryValue) => isEmptyFieldValue(entryValue));
    }

    if (op === FIELD_FILTER_OP.NOT_EMPTY) {
      return values.some((entryValue) => entryValue !== null && !isEmptyFieldValue(entryValue));
    }

    const needle = (value ?? "").toLowerCase();
    return values.some((entryValue) =>
      toSearchableText(entryValue).toLowerCase().includes(needle)
    );
  }

  if (filter.type === FILTER_TYPE.TIMESTAMP) {
    const { field, from, to } = filter;
    if (!field.trim()) return true;

    const raw = getByPath(entry.parsed, field.trim());
    if (raw === undefined || raw === null) return false;

    const ts = new Date(raw).getTime();
    if (Number.isNaN(ts)) return false;

    const fromMs = from ? new Date(from).getTime() : -Infinity;
    const toMs   = to   ? new Date(to).getTime()   : +Infinity;

    return ts >= fromMs && ts <= toMs;
  }

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
  if (filter.type === FILTER_TYPE.FIELD) {
    return filter.field.trim().length > 0;
  }
  if (filter.type === FILTER_TYPE.TIMESTAMP) {
    return filter.field.trim().length > 0 && (!!filter.from || !!filter.to);
  }
  if (filter.type === FILTER_TYPE.TEXT) {
    return (filter.query ?? "").trim().length > 0;
  }
  return false;
}
