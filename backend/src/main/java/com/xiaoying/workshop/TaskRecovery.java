package com.xiaoying.workshop;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Single backend instance: restart marks abandoned work failed rather than leaving it polling forever. */
@Component
public class TaskRecovery implements ApplicationRunner {
    private final WorkshopMapper mapper;
    private final TaskTransitions transitions;
    public TaskRecovery(WorkshopMapper mapper, TaskTransitions transitions) { this.mapper = mapper; this.transitions = transitions; }
    @Override public void run(ApplicationArguments args) {
        for (Models.Task task : mapper.activeTasks())
            transitions.fail(task.id, task.stage, "服务重启，未完成任务已中断，请重新提交");
    }
}
