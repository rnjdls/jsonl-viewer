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
});
