package com.xiaoying.workshop;

import java.util.List;

/** Pure ordered workflow. Every started/finished callback persists its own transaction. */
public class StageWorkflow {
    public static final List<String> STAGES = List.of("CHECKING", "GENERATING", "FINALIZING");
    @FunctionalInterface public interface Action { void accept(String stage) throws Exception; }
    public void execute(String failStage, Action started, Action work, Action finished) {
        for (String stage : STAGES) {
            try {
                started.accept(stage);
                work.accept(stage);
                if (stage.equals(failStage)) throw new IllegalStateException("模拟失败：" + stage);
                finished.accept(stage);
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new StageFailure(stage, error);
            }
        }
    }
    public static class StageFailure extends RuntimeException {
        private final String stage;
        public StageFailure(String stage, Throwable cause) { super(cause.getMessage(), cause); this.stage = stage; }
        public String stage() { return stage; }
    }
}
