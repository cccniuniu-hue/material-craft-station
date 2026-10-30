package com.xiaoying.workshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskRunner runner;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TaskTransitions transitions;
    @MockitoBean TaskDispatcher dispatcher;
    @MockitoBean StageDelay delay;
    String token;
    String assetId;

    @BeforeEach void identityAndAsset() throws Exception {
        token = newIdentity();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assetId = json.readTree(mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray()))
                .header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    String newIdentity() throws Exception {
        return json.readTree(mvc.perform(post("/api/identities").contentType("application/json").content("{\"displayName\":\"Test user\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    JsonNode submit(String failStage) throws Exception {
        Map<String, Object> simulation = failStage == null ? Map.of("delayMs", 500) : Map.of("delayMs", 500, "failStage", failStage);
        return json.readTree(mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("templateId", "coast", "assetIds", List.of(assetId), "simulation", simulation))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
    }

    JsonNode task(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void queuedQueriesDoNotAdvanceAndSuccessfulTerminalStateIsStable() throws Exception {
        JsonNode initial = submit(null);
        String id = initial.get("id").asText();
        assertThat(initial.get("status").asText()).isEqualTo("QUEUED");
        verify(dispatcher).dispatch(id);
        assertThat(task(id)).isEqualTo(initial);
        assertThat(task(id)).isEqualTo(initial);
        runner.run(id);
        JsonNode result = task(id);
        assertThat(result.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.get("stage").asText()).isEqualTo("COMPLETED");
        assertThat(result.get("resultUrl").asText()).startsWith("/media/results/");
        assertThat(result.get("steps")).hasSize(3);
        for (JsonNode step : result.get("steps")) {
            assertThat(step.get("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(step.get("startedAt").isNull()).isFalse();
            assertThat(step.get("finishedAt").isNull()).isFalse();
        }
        mvc.perform(get(result.get("resultUrl").asText())).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        runner.run(id);
        assertThat(task(id)).isEqualTo(result);
    }

    @ParameterizedTest @ValueSource(strings = {"CHECKING", "GENERATING", "FINALIZING"})
    void deterministicFailuresArePersistedAndShortCircuit(String stage) throws Exception {
        String id = submit(stage).get("id").asText();
        runner.run(id);
        JsonNode result = task(id);
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("stage").asText()).isEqualTo(stage);
        assertThat(result.get("resultUrl").isNull()).isTrue();
        int failedIndex = StageWorkflow.STAGES.indexOf(stage);
        for (int i = 0; i < 3; i++) {
            assertThat(result.get("steps").get(i).get("status").asText())
                    .isEqualTo(i < failedIndex ? "SUCCEEDED" : i == failedIndex ? "FAILED" : "PENDING");
        }
        runner.run(id);
        assertThat(task(id)).isEqualTo(result);
    }

    @Test void enforcesOwnershipAndRejectsMissingDuplicateAndUnknownAssets() throws Exception {
        String id = submit(null).get("id").asText();
        String other = newIdentity();
        mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + other)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + other).contentType("application/json")
                .content(json.writeValueAsString(Map.of("templateId", "coast", "assetIds", List.of(assetId)))))
                .andExpect(status().isBadRequest());
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("templateId", "city", "assetIds", List.of(assetId)),
                Map.of("templateId", "city", "assetIds", List.of(assetId, assetId)),
                Map.of("templateId", "coast", "assetIds", List.of("missing")))) {
            mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token).contentType("application/json")
                    .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsInvalidSimulationBeforeCreatingTask() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM generation_tasks", Integer.class);
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("templateId", "coast", "assetIds", List.of(assetId), "simulation", Map.of("delayMs", 0)))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generation_tasks", Integer.class)).isEqualTo(before);
    }

    @Test void dispatcherSeesCommittedTaskAndAllStepsFromIndependentConnection() throws Exception {
        doAnswer(invocation -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement("SELECT COUNT(*) FROM task_steps WHERE task_id=?")) {
                statement.setString(1, invocation.getArgument(0));
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(3);
                }
            }
            return null;
        }).when(dispatcher).dispatch(anyString());
        submit(null);
    }

    @Test void eachRunningStageIsCommittedAndObservableBeforeItsDelayFinishes() throws Exception {
        String id = submit(null).get("id").asText();
        List<String> observed = new ArrayList<>();
        doAnswer(invocation -> {
            JsonNode snapshot = task(id);
            assertThat(snapshot.get("status").asText()).isEqualTo("RUNNING");
            String stage = snapshot.get("stage").asText();
            int index = StageWorkflow.STAGES.indexOf(stage);
            assertThat(snapshot.get("steps").get(index).get("status").asText()).isEqualTo("RUNNING");
            observed.add(stage);
            return null;
        }).when(delay).await(anyInt());
        runner.run(id);
        assertThat(observed).containsExactly("CHECKING", "GENERATING", "FINALIZING");
    }

    @Test void saturatedExecutorFailsTaskWithoutExecutingItOnRequestThread() throws Exception {
        String id = submit(null).get("id").asText();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        ThreadPoolTaskExecutor saturated = new ThreadPoolTaskExecutor();
        saturated.setCorePoolSize(1); saturated.setMaxPoolSize(1); saturated.setQueueCapacity(0);
        saturated.initialize();
        try {
            saturated.execute(() -> {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            new TaskDispatcher(saturated, runner, transitions).dispatch(id);
            JsonNode result = task(id);
            assertThat(result.get("status").asText()).isEqualTo("FAILED");
            assertThat(result.get("errorMessage").asText()).contains("队列已满");
            verifyNoInteractions(delay);
        } finally { release.countDown(); saturated.shutdown(); }
    }
}
