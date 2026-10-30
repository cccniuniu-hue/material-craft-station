import type { Task } from "./types";
import { isActive } from "./poller";
function compareTime(a: string, b: string) {
  const delta = Date.parse(a) - Date.parse(b);
  if (delta) return delta;
  // Java Instant may serialize varying sub-millisecond precision.
  const fraction = (value: string) =>
    (value.match(/\.(\d+)/)?.[1] ?? "").padEnd(9, "0");
  return fraction(a).localeCompare(fraction(b));
}
export function mergeTasks(previous: Task[], incoming: Task[]): Task[] {
  const merged = new Map(previous.map((task) => [task.id, task]));
  for (const task of incoming) {
    const existing = merged.get(task.id);
    if (existing && !isActive(existing) && isActive(task)) continue;
    if (!existing || compareTime(task.updatedAt, existing.updatedAt) >= 0)
      merged.set(task.id, task);
  }
  return [...merged.values()].sort((a, b) =>
    compareTime(b.createdAt, a.createdAt),
  );
}
