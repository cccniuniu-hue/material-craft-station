"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, errorMessage } from "./api";
import { TaskPoller } from "./poller";
import { mergeTasks } from "./task-state";
import type { Task } from "./types";
export function useTasks(token?: string) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [queryErrors, setQueryErrors] = useState<Record<string, string | null>>(
    {},
  );
  const [listError, setListError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const pollerRef = useRef<TaskPoller | null>(null);
  const listController = useRef<AbortController | null>(null);
  const loadRef = useRef<() => void>(() => {});
  const put = useCallback(
    (task: Task) => setTasks((previous) => mergeTasks(previous, [task])),
    [],
  );
  useEffect(() => {
    if (!token) return;
    let active = true;
    const poller = new TaskPoller(
      (id, signal) => api<Task>(`/api/tasks/${id}`, token, { signal }),
      put,
      (id, message) =>
        setQueryErrors((previous) => ({ ...previous, [id]: message })),
    );
    pollerRef.current = poller;
    loadRef.current = () => {
      listController.current?.abort();
      const controller = new AbortController();
      listController.current = controller;
      setLoading(true);
      setListError(null);
      api<Task[]>("/api/tasks", token, { signal: controller.signal })
        .then((incoming) => {
          if (!active || controller.signal.aborted) return;
          // Keep fresher responses and tasks submitted while this list request was in flight.
          setTasks((previous) => mergeTasks(previous, incoming));
          setQueryErrors((previous) => {
            const recovered = { ...previous };
            for (const task of incoming) delete recovered[task.id];
            return recovered;
          });
        })
        .catch((error) => {
          if (active && !controller.signal.aborted)
            setListError(errorMessage(error));
        })
        .finally(() => {
          if (active && !controller.signal.aborted) setLoading(false);
        });
    };
    loadRef.current();
    return () => {
      active = false;
      listController.current?.abort();
      poller.dispose();
      pollerRef.current = null;
      loadRef.current = () => {};
    };
  }, [token, put]);
  useEffect(() => {
    tasks.forEach((task) => pollerRef.current?.track(task));
  }, [tasks]);
  const addTask = useCallback(
    (task: Task) => {
      put(task);
      pollerRef.current?.track(task);
    },
    [put],
  );
  return {
    tasks,
    addTask,
    queryErrors,
    listError,
    loading,
    refresh: () => loadRef.current(),
    retry: (id: string) => pollerRef.current?.retry(id),
  };
}
