import { FILTER_TYPE } from "../constants";

/**
 * @typedef {Object} FieldFilter
 * @property {string} id
 * @property {"field"} type
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

    const values = getValuesByKeyAnywhere(entry.parsed, fieldKey);
    if (values.length === 0) return false;

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
 * Returns true only when the entry satisfies ALL active filters (AND logic).
 *
 * @param {import("./jsonl").JsonlEntry} entry
 * @param {Filter[]} filters
 * @returns {boolean}
 */
export function entryMatchesAllFilters(entry, filters) {
  return filters.every((f) => entryMatchesFilter(entry, f));
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
