import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import * as api from "./utils/api";

vi.mock("./utils/api", () => ({
  getCountStatus: vi.fn(),
  getCounts: vi.fn(),
  getEntry: vi.fn(),
  getEntryRaw: vi.fn(),
  getPreview: vi.fn(),
  getStats: vi.fn(),
  pauseIngestion: vi.fn(),
  reloadData: vi.fn(),
  resetData: vi.fn(),
  resumeIngestion: vi.fn(),
}));

const BASE_STATS = {
  filePath: "/tmp/sample.jsonl",
  totalCount: 12,
  parsedCount: 12,
  errorCount: 0,
  searchStatus: "ready",
  ingestPaused: false,
  lastIngestedAt: "2026-04-10T10:15:30.000Z",
  sourceRevision: 2,
  exactCountAvailable: true,
  ingestedBytes: null,
  targetBytes: null,
};

const READY_COUNTS = {
  totalCount: 12,
  matchCount: 12,
  status: "ready",
  requestHash: "",
  sourceRevision: 2,
  computedRevision: 2,
  lastComputedAt: "2026-04-10T10:15:30.000Z",
};
const EMPTY_STATS = {
  ...BASE_STATS,
  totalCount: 0,
};
const EMPTY_COUNTS = {
  ...READY_COUNTS,
  totalCount: 0,
  matchCount: 0,
};
const PREVIEW_PAGE_1 = {
  rows: [
    {
      id: 101,
      lineNo: 101,
      ts: null,
      key: null,
      headers: null,
      error: null,
      rawSnippet: "{\"line\":101}",
      rawTruncated: false,
    },
  ],
  nextCursor: "cursor-page-2",
};
const PREVIEW_PAGE_2 = {
  rows: [
    {
      id: 202,
      lineNo: 202,
      ts: null,
      key: null,
      headers: null,
      error: null,
      rawSnippet: "{\"line\":202}",
      rawTruncated: false,
    },
  ],
  nextCursor: null,
};

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function waitForInitialLoad() {
  await waitFor(() => {
    expect(api.getStats).toHaveBeenCalled();
  });
  await waitFor(() => {
    expect(api.getCounts).toHaveBeenCalled();
  });
}

async function openAdminMenu(user) {
  await user.click(screen.getByRole("button", { name: "Open admin menu" }));
}

describe("App admin confirmations and lock", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getStats.mockResolvedValue(BASE_STATS);
    api.getCounts.mockResolvedValue(READY_COUNTS);
    api.getCountStatus.mockResolvedValue(READY_COUNTS);
    api.getPreview.mockResolvedValue({ rows: [], nextCursor: null });
    api.getEntry.mockResolvedValue({ parsed: { foo: "bar" }, error: null });
    api.getEntryRaw.mockResolvedValue("{\"foo\":\"bar\"}");
    api.pauseIngestion.mockResolvedValue(null);
    api.resumeIngestion.mockResolvedValue(null);
    api.reloadData.mockResolvedValue(null);
    api.resetData.mockResolvedValue(null);
  });

  afterEach(() => {
    cleanup();
  });

  it("opens reload confirmation and cancel does not call reload API", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);
    await user.click(screen.getByRole("menuitem", { name: "Reload File" }));

    expect(screen.getByRole("dialog", { name: "Reload File" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(api.reloadData).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("opens delete confirmation with distinct destructive copy", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);
    await user.click(screen.getByRole("menuitem", { name: "Delete All" }));

    expect(screen.getByRole("dialog", { name: "Delete All" })).toBeInTheDocument();
    expect(
      screen.getByText(/source position will move to the end\/newest point/i)
    ).toBeInTheDocument();
  });

  it("keeps UI locked until admin API and immediate stats/count refreshes resolve", async () => {
    const user = userEvent.setup();
    const reloadRequest = deferred();
    const statsRefresh = deferred();
    const countRefresh = deferred();

    api.getStats
      .mockResolvedValueOnce(BASE_STATS)
      .mockImplementationOnce(() => statsRefresh.promise)
      .mockResolvedValue(BASE_STATS);
    api.getCounts
      .mockResolvedValueOnce(READY_COUNTS)
      .mockImplementationOnce(() => countRefresh.promise)
      .mockResolvedValue(READY_COUNTS);
    api.reloadData.mockImplementation(() => reloadRequest.promise);

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);
    await user.click(screen.getByRole("menuitem", { name: "Reload File" }));
    await user.click(screen.getByRole("button", { name: "Yes, Reload" }));

    expect(screen.getByText("Reloading source...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pause" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "+ Field" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Open admin menu" })).toBeDisabled();

    reloadRequest.resolve(null);
    await Promise.resolve();
    expect(screen.getByText("Reloading source...")).toBeInTheDocument();

    statsRefresh.resolve(BASE_STATS);
    await Promise.resolve();
    expect(screen.getByText("Reloading source...")).toBeInTheDocument();

    countRefresh.resolve(READY_COUNTS);
    await waitFor(() => {
      expect(screen.queryByText("Reloading source...")).not.toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "Pause" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "+ Field" })).toBeEnabled();
  });

  it("unlocks after first pending count response and keeps pending polling active", async () => {
    const user = userEvent.setup();
    const pendingCounts = {
      ...READY_COUNTS,
      status: "pending",
      requestHash: "pending-req-1",
      computedRevision: 1,
    };

    api.getCounts.mockResolvedValueOnce(READY_COUNTS).mockResolvedValue(pendingCounts);
    api.getCountStatus.mockImplementation(() => new Promise(() => {}));

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);
    await user.click(screen.getByRole("menuitem", { name: "Delete All" }));
    await user.click(screen.getByRole("button", { name: "Yes, Delete All" }));

    await waitFor(() => {
      expect(screen.queryByText("Deleting rows...")).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(api.getCountStatus).toHaveBeenCalledWith("pending-req-1");
    });
  });

  it("clears lock and surfaces error when admin action fails", async () => {
    const user = userEvent.setup();
    api.reloadData.mockRejectedValue(new Error("reload failed"));

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);
    await user.click(screen.getByRole("menuitem", { name: "Reload File" }));
    await user.click(screen.getByRole("button", { name: "Yes, Reload" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("reload failed");
    });
    expect(screen.queryByText("Reloading source...")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pause" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "+ Field" })).toBeEnabled();
  });

  it("does not render the parsed chip in the topbar", async () => {
    render(<App />);
    await waitForInitialLoad();

    expect(screen.queryByText(/parsed/i)).not.toBeInTheDocument();
  });

  it("shows ingest size row in MB mode with mismatch colors", async () => {
    const user = userEvent.setup();
    api.getStats.mockResolvedValue({
      ...BASE_STATS,
      ingestedBytes: 512_000_000,
      targetBytes: 999_000_000,
    });

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);

    expect(screen.getByText("ingested:")).toBeInTheDocument();
    const currentValue = screen.getByText("512 MB");
    const targetValue = screen.getByText("999 MB");
    expect(currentValue).toHaveClass("topbar-menu-status-value--current");
    expect(targetValue).toHaveClass("topbar-menu-status-value--target");
    expect(screen.getAllByRole("menuitem")).toHaveLength(2);
  });

  it("shows ingest size row in GB mode with fixed two decimals", async () => {
    const user = userEvent.setup();
    api.getStats.mockResolvedValue({
      ...BASE_STATS,
      ingestedBytes: 1_250_000_000,
      targetBytes: 2_000_000_000,
    });

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);

    expect(screen.getByText("1.25 GB")).toBeInTheDocument();
    expect(screen.getByText("2.00 GB")).toBeInTheDocument();
  });

  it("switches both ingest size values to success color on exact byte match", async () => {
    const user = userEvent.setup();
    api.getStats.mockResolvedValue({
      ...BASE_STATS,
      ingestedBytes: 2_000_000_000,
      targetBytes: 2_000_000_000,
    });

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);

    const values = screen.getAllByText("2.00 GB");
    expect(values).toHaveLength(2);
    values.forEach((valueNode) => {
      expect(valueNode).toHaveClass("topbar-menu-status-value--success");
    });
  });

  it("hides ingest size row when progress size fields are unavailable", async () => {
    const user = userEvent.setup();
    api.getStats.mockResolvedValue({
      ...BASE_STATS,
      ingestedBytes: null,
      targetBytes: null,
    });

    render(<App />);
    await waitForInitialLoad();

    await openAdminMenu(user);

    expect(screen.queryByText("ingested:")).not.toBeInTheDocument();
  });

  it("does not render sort-by control and omits sortBy from preview requests", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();

    expect(screen.queryByLabelText("Sort by")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Reload Preview" }));

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });

    api.getPreview.mock.calls.forEach(([previewPayload]) => {
      expect(Object.prototype.hasOwnProperty.call(previewPayload, "sortBy")).toBe(false);
      expect(previewPayload.sortDir).toBe("desc");
    });
  });

  it("auto-loads preview on initial render when the file has rows", async () => {
    render(<App />);
    await waitForInitialLoad();

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });
    expect(api.getPreview).toHaveBeenCalledWith(
      expect.objectContaining({
        filtersOp: "and",
        filters: [],
        cursor: null,
        sortDir: "desc",
        limit: 10,
      })
    );
  });

  it("removes page dropdown and keeps sequential Prev/Next navigation", async () => {
    const user = userEvent.setup();
    api.getPreview
      .mockResolvedValueOnce(PREVIEW_PAGE_1)
      .mockResolvedValueOnce(PREVIEW_PAGE_2)
      .mockResolvedValueOnce(PREVIEW_PAGE_1);

    render(<App />);
    await waitForInitialLoad();

    await waitFor(() => {
      expect(screen.getByText("LINE 101")).toBeInTheDocument();
    });
    expect(screen.queryByRole("combobox", { name: "Page" })).not.toBeInTheDocument();
    expect(screen.getByText("of 2")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => {
      expect(screen.getByText("LINE 202")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Prev" }));
    await waitFor(() => {
      expect(screen.getByText("LINE 101")).toBeInTheDocument();
    });

    expect(api.getPreview).toHaveBeenNthCalledWith(1, expect.objectContaining({ cursor: null }));
    expect(api.getPreview).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ cursor: "cursor-page-2" })
    );
    expect(api.getPreview).toHaveBeenNthCalledWith(3, expect.objectContaining({ cursor: null }));
  });

  it("auto-loads preview after Search using the active filter payload", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "level");
    await user.type(screen.getByLabelText("Match value"), "error");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    expect(api.getPreview).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        filtersOp: "and",
        filters: [
          expect.objectContaining({
            type: "field",
            fieldPath: "level",
            op: "contains",
            valueContains: "error",
          }),
        ],
        cursor: null,
        sortDir: "desc",
        limit: 10,
      })
    );
  });

  it("reloads page 1 preview with empty payload when clearing applied filters", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "level");
    await user.type(screen.getByLabelText("Match value"), "error");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    expect(api.getPreview).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        filters: [
          expect.objectContaining({
            type: "field",
            fieldPath: "level",
            op: "contains",
            valueContains: "error",
          }),
        ],
      })
    );

    await user.click(screen.getByRole("button", { name: "✕ Clear all" }));

    await waitFor(() => {
      expect(api.getCounts).toHaveBeenCalledTimes(3);
    });
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(3);
    });
    expect(api.getCounts).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        filtersOp: "and",
        filters: [],
      })
    );
    expect(api.getPreview).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        filtersOp: "and",
        filters: [],
        cursor: null,
        sortDir: "desc",
        limit: 10,
      })
    );
  });

  it("reloads page 1 preview with empty payload when clearing draft-only filters", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "level");
    await user.type(screen.getByLabelText("Match value"), "error");

    await user.click(screen.getByRole("button", { name: "✕ Clear all" }));

    await waitFor(() => {
      expect(api.getCounts).toHaveBeenCalledTimes(2);
    });
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    expect(api.getCounts).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        filtersOp: "and",
        filters: [],
      })
    );
    expect(api.getPreview).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        filtersOp: "and",
        filters: [],
        cursor: null,
        sortDir: "desc",
        limit: 10,
      })
    );
  });

  it("keeps filtered preview working and defers exact counts while ingest is behind", async () => {
    const user = userEvent.setup();
    api.getStats.mockResolvedValue({
      ...BASE_STATS,
      exactCountAvailable: false,
      ingestedBytes: 100,
      targetBytes: 500,
    });
    api.getPreview.mockResolvedValue(PREVIEW_PAGE_1);

    render(<App />);

    await waitFor(() => {
      expect(api.getStats).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });
    expect(api.getCounts).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "level");
    await user.type(screen.getByLabelText("Match value"), "error");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    expect(api.getCounts).not.toHaveBeenCalled();
    expect(
      screen.getByText("Preview is available. Exact filtered count will resume when ingest catches up.")
    ).toBeInTheDocument();
    expect(screen.queryByText(/^of \d+/i)).not.toBeInTheDocument();
  });

  it("requests exact counts once availability flips back to true and moves deferred -> pending -> ready", async () => {
    const user = userEvent.setup();
    const pendingCounts = {
      ...READY_COUNTS,
      matchCount: null,
      status: "pending",
      requestHash: "req-after-catch-up",
      computedRevision: 1,
    };
    const readyCounts = {
      ...READY_COUNTS,
      matchCount: 3,
    };

    api.getStats
      .mockResolvedValueOnce({
        ...BASE_STATS,
        exactCountAvailable: false,
        ingestedBytes: 120,
        targetBytes: 500,
      })
      .mockResolvedValue({
        ...BASE_STATS,
        exactCountAvailable: true,
        ingestedBytes: 500,
        targetBytes: 500,
      });
    api.getCounts.mockResolvedValue(pendingCounts);
    api.getCountStatus.mockResolvedValue(readyCounts);
    api.getPreview.mockResolvedValue(PREVIEW_PAGE_1);

    render(<App />);
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });
    expect(api.getCounts).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "level");
    await user.type(screen.getByLabelText("Match value"), "error");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      expect(
        screen.getByText("Preview is available. Exact filtered count will resume when ingest catches up.")
      ).toBeInTheDocument();
    });
    expect(api.getCounts).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Pause" }));

    await waitFor(() => {
      expect(api.getCounts).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(api.getCountStatus).toHaveBeenCalledWith("req-after-catch-up");
    });
    await waitFor(() => {
      expect(document.querySelector(".sb-count")).toHaveTextContent(/3\s*\/\s*12 lines/i);
    });
  });

  it("shows only current page while exact count is pending", async () => {
    const pendingCounts = {
      ...READY_COUNTS,
      status: "pending",
      requestHash: "pending-req-2",
      computedRevision: 1,
    };

    api.getCounts.mockResolvedValue(pendingCounts);
    api.getCountStatus.mockImplementation(() => new Promise(() => {}));
    api.getPreview.mockResolvedValue(PREVIEW_PAGE_1);

    render(<App />);
    await waitForInitialLoad();

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalled();
    });
    const pageLabel = document.querySelector(".preview-page-label");
    expect(pageLabel).not.toBeNull();
    expect(pageLabel).toHaveTextContent("Page 1");
    expect(screen.queryByText("of 2")).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Page" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Prev" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeInTheDocument();
    expect(
      screen.getByText("Exact count is still pending. Preview pagination remains available with Prev/Next.")
    ).toBeInTheDocument();
  });

  it("does not trigger extra preview loads when stats refresh returns the same payload", async () => {
    const user = userEvent.setup();
    api.getPreview.mockResolvedValue(PREVIEW_PAGE_1);

    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole("button", { name: "Pause" }));
    await waitFor(() => {
      expect(api.getStats).toHaveBeenCalledTimes(2);
    });
    expect(api.getPreview).toHaveBeenCalledTimes(1);
  });

  it("hides timestamp controls and sends only field/text filters", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    expect(screen.queryByRole("button", { name: "+ Timestamp Range" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "eventTime");
    await user.type(screen.getByLabelText("Match value"), "2026");
    await user.click(screen.getByRole("button", { name: "+ Text" }));
    await user.type(screen.getByLabelText("Full text query"), "worker");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });

    const searchPayload = api.getPreview.mock.calls[1][0];
    expect(searchPayload.filters).toEqual([
      expect.objectContaining({
        type: "field",
        fieldPath: "eventTime",
        op: "contains",
        valueContains: "2026",
      }),
      expect.objectContaining({
        type: "text",
        query: "worker",
      }),
    ]);
    expect(searchPayload.filters.every((filter) => !("from" in filter) && !("to" in filter))).toBe(true);
  });

  it("auto-reloads preview when lines per page changes", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.selectOptions(screen.getByRole("combobox", { name: "Lines/page" }), "25");

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    expect(api.getPreview).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        cursor: null,
        sortDir: "desc",
        limit: 25,
      })
    );
  });

  it("auto-reloads preview when direction changes", async () => {
    const user = userEvent.setup();
    render(<App />);
    await waitForInitialLoad();
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.selectOptions(screen.getByRole("combobox", { name: "Direction" }), "asc");

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    expect(api.getPreview).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        cursor: null,
        sortDir: "asc",
        limit: 10,
      })
    );
  });

  it("does not auto-load preview when total count is zero", async () => {
    api.getStats.mockResolvedValue(EMPTY_STATS);
    api.getCounts.mockResolvedValue(EMPTY_COUNTS);

    render(<App />);
    await waitForInitialLoad();

    await waitFor(() => {
      expect(api.getPreview).not.toHaveBeenCalled();
    });
  });
});
