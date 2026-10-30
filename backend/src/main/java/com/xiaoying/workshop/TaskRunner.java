package com.xiaoying.workshop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class TaskRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);
    private final TaskTransitions transitions;
    private final WorkshopMapper mapper;
    private final FileStorage storage;
    private final StageDelay delay;
    public TaskRunner(TaskTransitions transitions, WorkshopMapper mapper, FileStorage storage, StageDelay delay) {
        this.transitions = transitions; this.mapper = mapper; this.storage = storage; this.delay = delay;
    }
    public void run(String taskId) {
        Models.Task task = transitions.claim(taskId);
        if (task == null) return; // Terminal tasks and duplicate deliveries never run twice.
        String[] resultUrl = new String[1];
        try {
            new StageWorkflow().execute(task.failStage,
                    stage -> transitions.start(taskId, stage),
                    stage -> {
                        delay.await(task.delayMs);
                        if ("CHECKING".equals(stage)) {
                            List<Models.Asset> assets = mapper.taskAssets(taskId);
                            if (assets.isEmpty()) throw new IllegalStateException("任务缺少素材");
                            for (Models.Asset asset : assets) storage.checkExists(asset.relativePath);
                        }
                        if ("FINALIZING".equals(stage) && !stage.equals(task.failStage))
                            resultUrl[0] = storage.createResult(taskId, mapper.taskAssets(taskId).get(0).relativePath);
                    },
                    stage -> transitions.finish(taskId, stage, resultUrl[0]));
        } catch (StageWorkflow.StageFailure failure) {
            String message = failure.stage().equals(task.failStage) ? "模拟失败：" + failure.stage()
                    : Thread.currentThread().isInterrupted() ? "服务停止，任务已中断，请重新提交" : "阶段执行失败，请重新提交任务";
            log.warn("Task {} failed at {}: {}", taskId, failure.stage(), failure.getMessage());
            // Clear interrupt temporarily so JDBC can persist the failure before this worker exits.
            boolean interrupted = Thread.interrupted();
            try { transitions.fail(taskId, failure.stage(), message); }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }
}
