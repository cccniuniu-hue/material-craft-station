import { afterEach, describe, expect, it, vi } from "vitest";
import { TaskPoller } from "./poller";
import type { Task } from "./types";
const task = (id: string, status: Task["status"] = "RUNNING"): Task => ({
  id,
  status,
  templateId: "coast",
  templateTitle: "海岸",
  coverUrl: "/demo/coast.svg",
  stage: status === "SUCCEEDED" ? "COMPLETED" : "CHECKING",
  createdAt: "2026-10-30T00:00:00Z",
  updatedAt: "2026-10-30T00:00:00Z",
  steps: [],
  resultUrl: null,
  errorMessage: null,
});
afterEach(() => vi.useRealTimers());
describe("independent task polling", () => {
  it("keeps another job moving when one query fails, retries, then stops all terminal jobs", async () => {
    vi.useFakeTimers();
    let attemptA = 0;
    const requests: string[] = [];
    const results: Task[] = [];
    const errors: [string, string | null][] = [];
    const poller = new TaskPoller(
      async (id) => {
        requests.push(id);
        if (id === "a" && attemptA++ === 0) throw new Error("network offline");
        return task(id, id === "a" ? "FAILED" : "SUCCEEDED");
      },
      (value) => results.push(value),
      (id, message) => errors.push([id, message]),
      1000,
    );
    poller.track(task("a"));
    poller.track(task("b"));
    poller.track(task("done", "SUCCEEDED"));
    await vi.advanceTimersByTimeAsync(0);
    expect(results).toEqual([task("b", "SUCCEEDED")]);
    expect(errors).toContainEqual(["a", "network offline"]);
    expect(requests).not.toContain("done");
    await vi.advanceTimersByTimeAsync(1000);
    expect(results.at(-1)?.status).toBe("FAILED");
    expect(errors).toContainEqual(["a", null]);
    const completedCount = requests.length;
    await vi.advanceTimersByTimeAsync(10000);
    expect(requests.length).toBe(completedCount);
    expect(vi.getTimerCount()).toBe(0);
  });
  it("aborts pending queries and ignores stale responses after identity switch/unmount", async () => {
    vi.useFakeTimers();
    let finish!: (task: Task) => void;
    let signal!: AbortSignal;
    const results: Task[] = [];
    const poller = new TaskPoller(
      async (_id, querySignal) => {
        signal = querySignal;
        return new Promise<Task>((resolve) => {
          finish = resolve;
        });
      },
      (value) => results.push(value),
      () => {},
      1000,
    );
    poller.track(task("old-user"));
    await vi.advanceTimersByTimeAsync(0);
    poller.dispose();
    expect(signal.aborted).toBe(true);
    finish(task("old-user", "SUCCEEDED"));
    await vi.advanceTimersByTimeAsync(10000);
    expect(results).toEqual([]);
    expect(vi.getTimerCount()).toBe(0);
  });
  it("does not overlap requests for one task and manual retry recovers a query", async () => {
    vi.useFakeTimers();
    let resolve!: (task: Task) => void;
    let count = 0;
    const poller = new TaskPoller(
      async () => {
        count++;
        return new Promise<Task>((r) => {
          resolve = r;
        });
      },
      () => {},
      () => {},
      1000,
    );
    poller.track(task("a"));
    poller.track(task("a"));
    await vi.advanceTimersByTimeAsync(0);
    poller.retry("a");
    await vi.advanceTimersByTimeAsync(5000);
    expect(count).toBe(1);
    resolve(task("a"));
    await vi.advanceTimersByTimeAsync(0);
    poller.retry("a");
    await vi.advanceTimersByTimeAsync(0);
    expect(count).toBe(2);
    poller.dispose();
  });
});
