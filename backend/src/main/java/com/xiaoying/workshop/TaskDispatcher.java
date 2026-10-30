package com.xiaoying.workshop;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class TaskDispatcher {
    private final ThreadPoolTaskExecutor executor;
    private final TaskRunner runner;
    private final TaskTransitions transitions;
    public TaskDispatcher(@Qualifier("generationExecutor") ThreadPoolTaskExecutor executor,
            TaskRunner runner, TaskTransitions transitions) {
        this.executor = executor; this.runner = runner; this.transitions = transitions;
    }
    public void dispatch(String taskId) {
        try { executor.execute(() -> runner.run(taskId)); }
        catch (TaskRejectedException e) {
            // Explicit failure on saturation; never run slow work on the HTTP request thread.
            transitions.fail(taskId, "CHECKING", "任务队列已满，请稍后重新提交");
        }
    }
}
