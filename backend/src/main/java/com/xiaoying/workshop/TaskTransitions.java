package com.xiaoying.workshop;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class TaskTransitions {
    private final WorkshopMapper mapper;
    public TaskTransitions(WorkshopMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Models.Task claim(String id) {
        Models.Task task = mapper.lockTask(id);
        if (task == null || !"QUEUED".equals(task.status)) return null;
        task.status = "RUNNING"; task.updatedAt = Instant.now();
        mapper.updateTask(task);
        return task;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(String id, String stage) {
        Models.Task task = active(id);
        List<Models.Step> steps = mapper.steps(id);
        int index = StageWorkflow.STAGES.indexOf(stage);
        if (index < 0 || !"PENDING".equals(steps.get(index).status)
                || (index > 0 && !"SUCCEEDED".equals(steps.get(index - 1).status)))
            throw new IllegalStateException("步骤顺序不合法");
        Models.Step step = steps.get(index);
        step.status = "RUNNING"; step.startedAt = Instant.now();
        mapper.updateStep(step);
        task.stage = stage; task.updatedAt = step.startedAt;
        mapper.updateTask(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(String id, String stage, String resultUrl) {
        Models.Task task = active(id);
        Models.Step step = mapper.steps(id).get(StageWorkflow.STAGES.indexOf(stage));
        if (!stage.equals(task.stage) || !"RUNNING".equals(step.status)) throw new IllegalStateException("步骤状态不合法");
        step.status = "SUCCEEDED"; step.finishedAt = Instant.now();
        mapper.updateStep(step);
        task.updatedAt = step.finishedAt;
        if ("FINALIZING".equals(stage)) {
            if (resultUrl == null) throw new IllegalStateException("结果文件尚未生成");
            task.status = "SUCCEEDED"; task.stage = "COMPLETED"; task.resultUrl = resultUrl;
        }
        mapper.updateTask(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String id, String requestedStage, String message) {
        Models.Task task = mapper.lockTask(id);
        if (task == null || terminal(task)) return;
        String stage = "WAITING".equals(task.stage) ? "CHECKING" : task.stage;
        Models.Step step = mapper.steps(id).get(StageWorkflow.STAGES.indexOf(stage));
        Instant now = Instant.now();
        // A failure between stages is attached to the next unfinished step, preserving prior success.
        if ("SUCCEEDED".equals(step.status)) {
            int next = StageWorkflow.STAGES.indexOf(stage) + 1;
            if (next < StageWorkflow.STAGES.size()) step = mapper.steps(id).get(next);
            stage = step.stage;
        }
        step.status = "FAILED";
        if (step.startedAt == null) step.startedAt = now;
        step.finishedAt = now; step.errorMessage = message;
        mapper.updateStep(step);
        task.status = "FAILED"; task.stage = stage; task.errorMessage = message;
        task.resultUrl = null; task.updatedAt = now;
        mapper.updateTask(task);
    }
    private Models.Task active(String id) {
        Models.Task task = mapper.lockTask(id);
        if (task == null || !"RUNNING".equals(task.status)) throw new IllegalStateException("任务不在运行状态");
        return task;
    }
    private boolean terminal(Models.Task task) { return "SUCCEEDED".equals(task.status) || "FAILED".equals(task.status); }
}
