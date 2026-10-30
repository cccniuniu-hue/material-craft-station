package com.xiaoying.workshop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class WorkflowTest {
    @Test void runsThreeOrderedStagesAndPublishesResultOnlyAtTheEnd() {
        List<String> events = new ArrayList<>();
        StageWorkflow workflow = new StageWorkflow();
        workflow.execute(null, stage -> events.add("start:" + stage), stage -> events.add("work:" + stage),
                stage -> events.add("finish:" + stage));
        assertThat(events).containsExactly("start:CHECKING", "work:CHECKING", "finish:CHECKING",
                "start:GENERATING", "work:GENERATING", "finish:GENERATING",
                "start:FINALIZING", "work:FINALIZING", "finish:FINALIZING");
    }

    @ParameterizedTest @ValueSource(strings = {"CHECKING", "GENERATING", "FINALIZING"})
    void failureStopsTheRemainingStages(String failureStage) {
        List<String> started = new ArrayList<>();
        List<String> completed = new ArrayList<>();
        assertThatThrownBy(() -> new StageWorkflow().execute(failureStage, started::add, stage -> {}, completed::add))
                .isInstanceOf(StageWorkflow.StageFailure.class)
                .satisfies(error -> assertThat(((StageWorkflow.StageFailure) error).stage()).isEqualTo(failureStage));
        int index = StageWorkflow.STAGES.indexOf(failureStage);
        assertThat(started).containsExactlyElementsOf(StageWorkflow.STAGES.subList(0, index + 1));
        assertThat(completed).containsExactlyElementsOf(StageWorkflow.STAGES.subList(0, index));
    }

    @Test void workExceptionRetainsStageAndStops() {
        List<String> started = new ArrayList<>();
        assertThatThrownBy(() -> new StageWorkflow().execute(null, started::add,
                stage -> { throw new IllegalStateException("disk unavailable"); }, stage -> {}))
                .isInstanceOf(StageWorkflow.StageFailure.class)
                .satisfies(error -> assertThat(((StageWorkflow.StageFailure) error).stage()).isEqualTo("CHECKING"));
        assertThat(started).containsExactly("CHECKING");
    }
}
