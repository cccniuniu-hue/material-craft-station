import { expect, it } from "vitest";
import { mergeTasks } from "./task-state";
import type { Task } from "./types";
const snapshot = (status: Task["status"], updatedAt: string): Task => ({
  id: "a",
  status,
  updatedAt,
  createdAt: "2026-10-30T10:00:00Z",
  templateId: "coast",
  templateTitle: "海岸",
  coverUrl: "/demo/coast.jpg",
  stage: status === "SUCCEEDED" ? "COMPLETED" : "GENERATING",
  steps: [],
  resultUrl: null,
  errorMessage: null,
});
it("does not let a delayed in-flight query replace a terminal list snapshot", () => {
  const done = snapshot("SUCCEEDED", "2026-10-30T10:00:12.500Z");
  expect(
    mergeTasks([done], [snapshot("RUNNING", "2026-10-30T10:00:12.400Z")]),
  ).toEqual([done]);
});
it("compares variable-precision server timestamps and keeps newly submitted tasks", () => {
  const old = snapshot("RUNNING", "2026-10-30T10:00:12.500Z");
  const recent = {
    ...snapshot("RUNNING", "2026-10-30T10:00:12.500001Z"),
    stage: "FINALIZING" as const,
  };
  const submitted = {
    ...snapshot("QUEUED", "2026-10-30T10:00:13Z"),
    id: "new",
  };
  expect(
    mergeTasks([old, submitted], [recent]).find((task) => task.id === "a"),
  ).toEqual(recent);
  expect(mergeTasks([old, submitted], [recent])).toContainEqual(submitted);
});
