/** How often (ms) the file is re-read in live mode. */
export const POLL_INTERVAL_MS = 1000;

/** Field name fragments that trigger base64 decoding of their values. */
export const BASE64_HEADER_KEYS = new Set([
  "header",
  "headers",
  "authorization",
  "auth",
]);

/** HTTP status → colour mapping. */
export const STATUS_COLOR = (status) => {
  if (status >= 500) return "var(--warn)";
  if (status >= 400) return "var(--yellow)";
  if (status >= 200) return "var(--accent)";
  return "var(--text)";
};

/** Log level → badge styling. */
export const LEVEL_STYLES = {
  INFO: {
    background: "rgba(77,159,255,.12)",
    color: "#4d9fff",
    border: "1px solid rgba(77,159,255,.3)",
  },
  WARN: {
    background: "rgba(253,224,71,.10)",
    color: "#fde047",
    border: "1px solid rgba(253,224,71,.3)",
  },
  ERROR: {
    background: "rgba(255,107,107,.10)",
    color: "#ff6b6b",
    border: "1px solid rgba(255,107,107,.3)",
  },
  DEBUG: {
    background: "rgba(0,229,160,.08)",
    color: "#00e5a0",
    border: "1px solid rgba(0,229,160,.25)",
  },
};

export const FILTER_TYPE = {
  FIELD:     "field",
  TEXT:      "text",
  TIMESTAMP: "timestamp",
};

export const FILTERS_OP = {
  AND: "and",
  OR:  "or",
};

export const FIELD_FILTER_OP = {
  CONTAINS:  "contains",
  NULL:      "null",
  NOT_NULL:  "not_null",
  EMPTY:     "empty",
  NOT_EMPTY: "not_empty",
};

export const FIELD_FILTER_OP_OPTIONS = [
  { value: FIELD_FILTER_OP.CONTAINS, label: "CONTAINS" },
  { value: FIELD_FILTER_OP.NULL, label: "NULL" },
  { value: FIELD_FILTER_OP.NOT_NULL, label: "NOT NULL" },
  { value: FIELD_FILTER_OP.EMPTY, label: "EMPTY" },
  { value: FIELD_FILTER_OP.NOT_EMPTY, label: "NOT EMPTY" },
];

export const COMMON_TIMESTAMP_FIELDS = [
  "timestamp", "time", "created_at", "updated_at", "date", "datetime", "ts",
];
