import { tryBase64Decode } from "./base64";
import { BASE64_HEADER_KEYS } from "../constants";

/**
 * @typedef {Object} JsonlEntry
 * @property {number}      id      - Zero-based line index.
 * @property {string}      raw     - Original raw line text.
 * @property {Object|null} parsed  - Parsed JSON object, or null on error.
 * @property {string|null} error   - Parse error message, or null on success.
 */

/**
 * Parses a full JSONL text blob into an array of entries.
 * Empty lines are silently skipped.
 *
 * @param {string} text
 * @returns {JsonlEntry[]}
 */
export function parseJsonlText(text) {
  return text
    .split("\n")
    .reduce((acc, line, idx) => {
      const raw = line.trim();
      if (!raw) return acc;

      try {
        acc.push({ id: idx, raw, parsed: JSON.parse(raw), error: null });
      } catch (err) {
        acc.push({ id: idx, raw, parsed: null, error: err.message });
      }

      return acc;
    }, []);
}

/**
 * Recursively walks a parsed JSON object and annotates any string value
 * inside a "header-like" key with its base64-decoded counterpart.
 *
 * Annotated values are plain objects of the shape:
 *   { __original: string, __decoded: string }
 *
 * Non-decodable strings are left as-is.
 *
 * @param {*} node
 * @returns {*}
 */
export function enrichWithDecodedHeaders(node) {
  if (node === null || typeof node !== "object") return node;

  if (Array.isArray(node)) {
    return node.map(enrichWithDecodedHeaders);
  }

  const result = {};

  for (const [key, value] of Object.entries(node)) {
    const isHeaderKey = BASE64_HEADER_KEYS.has(key.toLowerCase());

    if (isHeaderKey) {
      result[key] = decodeHeaderValue(value);
    } else if (typeof value === "object" && value !== null) {
      result[key] = enrichWithDecodedHeaders(value);
    } else {
      result[key] = value;
    }
  }

  return result;
}

/* ── Internal helpers ───────────────────────────────────── */

/**
 * Decodes a single header value, which may be a raw string or an object
 * whose string values are individually decoded.
 *
 * @param {*} value
 * @returns {*}
 */
function decodeHeaderValue(value) {
  if (typeof value === "string") {
    return annotate(value);
  }

  if (typeof value === "object" && value !== null && !Array.isArray(value)) {
    const decoded = {};
    for (const [k, v] of Object.entries(value)) {
      decoded[k] = typeof v === "string" ? annotate(v) : v;
    }
    return decoded;
  }

  return value;
}

/**
 * Returns a decoded annotation object if the string is valid base64,
 * otherwise returns the original string unchanged.
 *
 * @param {string} str
 * @returns {string | { __original: string, __decoded: string }}
 */
function annotate(str) {
  const decoded = tryBase64Decode(str);
  return decoded ? { __original: str, __decoded: decoded } : str;
}
