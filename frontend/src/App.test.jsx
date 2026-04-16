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

function createPreviewRows(count, startId = 1) {
  return Array.from({ length: count }, (_, index) => ({ id: startId + index }));
}

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

describe("App initial preview auto-refresh", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.clearAllMocks();
    api.getStats.mockResolvedValue(BASE_STATS);
    api.getCounts.mockResolvedValue(READY_COUNTS);
    api.getCountStatus.mockResolvedValue(READY_COUNTS);
    api.getPreview.mockResolvedValue({ rows: createPreviewRows(10), nextCursor: null });
    api.getEntry.mockResolvedValue({ parsed: { foo: "bar" }, error: null });
    api.getEntryRaw.mockResolvedValue("{\"foo\":\"bar\"}");
    api.pauseIngestion.mockResolvedValue(null);
    api.resumeIngestion.mockResolvedValue(null);
    api.reloadData.mockResolvedValue(null);
    api.resetData.mockResolvedValue(null);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("retries initial page 1 until the rendered rows match stats total count", async () => {
    api.getStats.mockResolvedValue({ ...BASE_STATS, totalCount: 5 });
    api.getCounts.mockResolvedValue({ ...READY_COUNTS, totalCount: 5, matchCount: 5 });
    api.getPreview
      .mockResolvedValueOnce({ rows: createPreviewRows(4), nextCursor: null })
      .mockResolvedValue({ rows: createPreviewRows(5), nextCursor: null });

    render(<App />);

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await vi.advanceTimersByTimeAsync(3000);
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });

    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getPreview).toHaveBeenCalledTimes(2);
  });

  it("stops retrying once page 1 reaches min(linesPerPage, stats.totalCount)", async () => {
    api.getStats.mockResolvedValue({ ...BASE_STATS, totalCount: 20 });
    api.getCounts.mockResolvedValue({ ...READY_COUNTS, totalCount: 20, matchCount: 20 });
    api.getPreview.mockResolvedValue({ rows: createPreviewRows(10), nextCursor: "cursor-2" });

    render(<App />);

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await vi.advanceTimersByTimeAsync(9000);
    expect(api.getPreview).toHaveBeenCalledTimes(1);
  });

  it("re-arms retrying when stats polling raises total count above rendered page 1 rows", async () => {
    api.getStats
      .mockResolvedValueOnce({ ...BASE_STATS, totalCount: 8, sourceRevision: 2 })
      .mockResolvedValueOnce({ ...BASE_STATS, totalCount: 9, sourceRevision: 3 })
      .mockResolvedValue({ ...BASE_STATS, totalCount: 9, sourceRevision: 3 });
    api.getCounts.mockResolvedValue({ ...READY_COUNTS, totalCount: 8, matchCount: 8 });
    api.getPreview
      .mockResolvedValueOnce({ rows: createPreviewRows(8), nextCursor: null })
      .mockResolvedValue({ rows: createPreviewRows(9), nextCursor: null });

    render(<App />);

    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await vi.advanceTimersByTimeAsync(3000);
    await waitFor(() => {
      expect(api.getStats).toHaveBeenCalledTimes(2);
    });
    expect(api.getPreview).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(3000);
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
  });

  it("auto-loads preview when stats total grows from zero even if counts remains stale", async () => {
    api.getStats
      .mockResolvedValueOnce({ ...BASE_STATS, totalCount: 0, sourceRevision: 2 })
      .mockResolvedValue({ ...BASE_STATS, totalCount: 1, sourceRevision: 3 });
    api.getCounts.mockResolvedValue({ ...READY_COUNTS, totalCount: 0, matchCount: 0, sourceRevision: 2 });
    api.getPreview.mockResolvedValue({ rows: createPreviewRows(1), nextCursor: null });

    render(<App />);

    await waitFor(() => {
      expect(api.getCounts).toHaveBeenCalled();
    });
    expect(api.getPreview).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(3000);
    await waitFor(() => {
      expect(api.getStats).toHaveBeenCalledTimes(2);
    });
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(screen.queryByText("No data yet")).not.toBeInTheDocument();
    });
    expect(screen.getByText("LINE 1")).toBeInTheDocument();
  });

  it("does not use the retry loop for manual reload, search, direction, lines/page, or paging", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    api.getStats.mockResolvedValue({ ...BASE_STATS, totalCount: 20 });
    api.getCounts.mockResolvedValue({ ...READY_COUNTS, totalCount: 20, matchCount: 20 });
    api.getPreview
      .mockResolvedValueOnce({ rows: createPreviewRows(10), nextCursor: "cursor-2" })
      .mockResolvedValueOnce({ rows: createPreviewRows(10, 11), nextCursor: "cursor-2" })
      .mockResolvedValueOnce({ rows: createPreviewRows(10), nextCursor: "cursor-2" })
      .mockResolvedValueOnce({ rows: createPreviewRows(10), nextCursor: "cursor-2" })
      .mockResolvedValueOnce({ rows: createPreviewRows(10, 21), nextCursor: null })
      .mockResolvedValueOnce({ rows: createPreviewRows(20), nextCursor: null });

    render(<App />);
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole("button", { name: "Reload Preview" }));
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getPreview).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole("button", { name: "+ Field" }));
    await user.type(screen.getByLabelText("Field key"), "level");
    await user.type(screen.getByLabelText("Match value"), "error");
    await user.click(screen.getByRole("button", { name: "Search" }));
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(3);
    });
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getPreview).toHaveBeenCalledTimes(3);

    await user.selectOptions(screen.getByRole("combobox", { name: "Direction" }), "asc");
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(4);
    });
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getPreview).toHaveBeenCalledTimes(4);

    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(5);
    });
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getPreview).toHaveBeenCalledTimes(5);

    await user.selectOptions(screen.getByRole("combobox", { name: "Lines/page" }), "25");
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(6);
    });
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.getPreview).toHaveBeenCalledTimes(6);
  });

  it("stops retrying once the user leaves initial page-1 auto-preview context", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    api.getStats.mockResolvedValue({ ...BASE_STATS, totalCount: 5 });
    api.getCounts.mockResolvedValue({ ...READY_COUNTS, totalCount: 5, matchCount: 5 });
    api.getPreview
      .mockResolvedValueOnce({ rows: createPreviewRows(4), nextCursor: null })
      .mockResolvedValue({ rows: createPreviewRows(5), nextCursor: null });

    render(<App />);
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(1);
    });

    await vi.advanceTimersByTimeAsync(1000);
    await user.click(screen.getByRole("button", { name: "Reload Preview" }));
    await waitFor(() => {
      expect(api.getPreview).toHaveBeenCalledTimes(2);
    });

    await vi.advanceTimersByTimeAsync(5000);
    expect(api.getPreview).toHaveBeenCalledTimes(2);
  });
});
