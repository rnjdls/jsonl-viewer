import { useState } from "react";
import "./JsonValue.css";

/**
 * Recursive JSON tree renderer.
 *
 * Understands annotated base64 values produced by `enrichWithDecodedHeaders`
 * (objects with `__original` / `__decoded` keys) and renders them with a
 * colour-coded badge and the decoded text inline.
 *
 * @param {{ val: *, depth?: number }} props
 */
export function JsonValue({ val, depth = 0 }) {
  const [collapsed, setCollapsed] = useState(depth > 3);

  /* ── Primitives ──────────────────────────────────────── */
  if (val === null)             return <span className="jv-null">null</span>;
  if (typeof val === "boolean") return <span className="jv-bool">{String(val)}</span>;
  if (typeof val === "number")  return <span className="jv-num">{val}</span>;
  if (typeof val === "string")  return <span className="jv-str">"{val}"</span>;

  /* ── Base64 annotation ───────────────────────────────── */
  if (typeof val === "object" && val !== null && "__decoded" in val) {
    return (
      <span className="jv-b64-wrap">
        <span className="jv-str jv-faded">"{val.__original}"</span>
        <span className="jv-b64-badge">base64</span>
        <span className="jv-b64-decoded">→ "{val.__decoded}"</span>
      </span>
    );
  }

  /* ── Arrays ──────────────────────────────────────────── */
  if (Array.isArray(val)) {
    if (val.length === 0) return <span className="jv-bracket">[]</span>;
    return (
      <span>
        <button className="jv-toggle" onClick={() => setCollapsed((c) => !c)}>
          {collapsed ? "▶" : "▼"}
        </button>
        <span className="jv-bracket">[</span>
        {collapsed ? (
          <button className="jv-collapsed" onClick={() => setCollapsed(false)}>
            {val.length} item{val.length !== 1 ? "s" : ""}
          </button>
        ) : (
          <div className="jv-indent">
            {val.map((item, i) => (
              <div key={i} className="jv-row">
                <JsonValue val={item} depth={depth + 1} />
                {i < val.length - 1 && <span className="jv-comma">,</span>}
              </div>
            ))}
          </div>
        )}
        <span className="jv-bracket">]</span>
      </span>
    );
  }

  /* ── Objects ─────────────────────────────────────────── */
  const entries = Object.entries(val).filter(([k]) => !k.startsWith("__"));
  if (entries.length === 0) return <span className="jv-bracket">{"{}"}</span>;

  return (
    <span>
      <button className="jv-toggle" onClick={() => setCollapsed((c) => !c)}>
        {collapsed ? "▶" : "▼"}
      </button>
      <span className="jv-bracket">{"{"}</span>
      {collapsed ? (
        <button className="jv-collapsed" onClick={() => setCollapsed(false)}>
          {entries.length} field{entries.length !== 1 ? "s" : ""}
        </button>
      ) : (
        <div className="jv-indent">
          {entries.map(([k, v], i) => (
            <div key={k} className="jv-row">
              <span className="jv-key">"{k}"</span>
              <span className="jv-colon">: </span>
              <JsonValue val={v} depth={depth + 1} />
              {i < entries.length - 1 && <span className="jv-comma">,</span>}
            </div>
          ))}
        </div>
      )}
      <span className="jv-bracket">{"}"}</span>
    </span>
  );
}
