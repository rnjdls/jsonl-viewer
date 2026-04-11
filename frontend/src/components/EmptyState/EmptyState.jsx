import "./EmptyState.css";

/**
 * Displayed when the backend is offline, no file is configured, or no results.
 *
 * @param {{
 *   variant: "backend-offline" | "no-file" | "empty-file" | "no-results",
 * }} props
 */
export function EmptyState({ variant }) {
  const config = {
    "backend-offline": {
      icon: "⊗",
      title: "Backend offline",
      lines: [
        "Unable to reach the Spring backend.",
        "Check that the backend container is running and /api is reachable.",
      ],
    },
    "no-file": {
      icon: "⬡",
      title: "No source configured",
      lines: [
        "Set JSONL_FILE_PATH for file mode, or set KAFKA_BOOTSTRAP_SERVERS + KAFKA_TOPIC for kafka mode.",
        "Choose mode with INGEST_MODE=file or INGEST_MODE=kafka.",
      ],
    },
    "empty-file": {
      icon: "◌",
      title: "No data yet",
      lines: ["Waiting for new lines to be written to the file..."],
    },
    "no-results": {
      icon: "∅",
      title: "No matches",
      lines: ["Try adjusting your filters or clearing them to see data."],
    },
  };

  const { icon, title, lines } = config[variant];

  return (
    <div className="empty-state">
      <span className="empty-state-icon">{icon}</span>
      <h2 className="empty-state-title">{title}</h2>
      <ul className="empty-state-lines">
        {lines.map((line, i) => (
          <li key={i}>{line}</li>
        ))}
      </ul>
    </div>
  );
}
