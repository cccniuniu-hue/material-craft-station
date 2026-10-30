import type { Task } from "./types";
type Entry = {
  timer?: ReturnType<typeof setTimeout>;
  controller?: AbortController;
};
export const isActive = (task: Task) =>
  task.status === "QUEUED" || task.status === "RUNNING";
// One recursive timeout per task: slow requests never overlap, terminal jobs release timers.
export class TaskPoller {
  private entries = new Map<string, Entry>();
  private disposed = false;
  constructor(
    private fetchTask: (id: string, signal: AbortSignal) => Promise<Task>,
    private onTask: (task: Task) => void,
    private onError: (id: string, message: string | null) => void,
    private interval = 1800,
  ) {}
  track(task: Task) {
    if (this.disposed) return;
    if (!isActive(task)) {
      this.remove(task.id);
      return;
    }
    if (this.entries.has(task.id)) return;
    const entry: Entry = {};
    this.entries.set(task.id, entry);
    this.schedule(task.id, entry, 0);
  }
  retry(id: string) {
    const entry = this.entries.get(id);
    if (!entry || entry.controller || this.disposed) return;
    clearTimeout(entry.timer);
    this.schedule(id, entry, 0);
  }
  private schedule(id: string, entry: Entry, delay: number) {
    entry.timer = setTimeout(() => {
      void this.poll(id, entry);
    }, delay);
  }
  private async poll(id: string, entry: Entry) {
    entry.timer = undefined;
    const controller = new AbortController();
    entry.controller = controller;
    const current = () =>
      !this.disposed &&
      this.entries.get(id) === entry &&
      !controller.signal.aborted;
    try {
      const task = await this.fetchTask(id, controller.signal);
      if (!current()) return;
      this.onError(id, null);
      this.onTask(task);
      if (!isActive(task)) {
        this.remove(id);
        return;
      }
    } catch (error) {
      if (!current()) return;
      this.onError(
        id,
        error instanceof Error ? error.message : "任务状态暂时无法查询",
      );
    } finally {
      entry.controller = undefined;
      if (current()) this.schedule(id, entry, this.interval);
    }
  }
  private remove(id: string) {
    const entry = this.entries.get(id);
    clearTimeout(entry?.timer);
    entry?.controller?.abort();
    this.entries.delete(id);
  }
  dispose() {
    this.disposed = true;
    for (const id of this.entries.keys()) this.remove(id);
  }
}
