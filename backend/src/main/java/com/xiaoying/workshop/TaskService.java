package com.xiaoying.workshop;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {
    private final WorkshopMapper mapper;
    private final TaskDispatcher dispatcher;
    public TaskService(WorkshopMapper mapper, TaskDispatcher dispatcher) { this.mapper = mapper; this.dispatcher = dispatcher; }
    @Transactional
    public ApiDtos.TaskResponse submit(String userId, ApiDtos.TaskRequest request) {
        Models.Template template = mapper.template(request.templateId());
        if (template == null) throw new ApiException(404, "模板不存在");
        List<String> ids = request.assetIds();
        if (ids.size() != template.requiredImageCount) throw new ApiException(400, "该模板需要 " + template.requiredImageCount + " 张图片");
        if (new HashSet<>(ids).size() != ids.size()) throw new ApiException(400, "不能重复选择同一份素材");
        for (String id : ids) {
            Models.Asset asset = mapper.asset(id);
            if (asset == null || !asset.userId.equals(userId)) throw new ApiException(400, "素材不存在或不属于当前用户");
            if (asset.sizeBytes > template.maxFileSizeBytes || !("image/png".equals(asset.mimeType) || "image/jpeg".equals(asset.mimeType)))
                throw new ApiException(400, "素材不符合模板要求");
        }
        Models.Task task = new Models.Task();
        task.id = UUID.randomUUID().toString(); task.userId = userId; task.templateId = template.id;
        task.status = "QUEUED"; task.stage = "WAITING";
        task.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); task.updatedAt = task.createdAt;
        task.delayMs = request.simulation() == null || request.simulation().delayMs() == null ? 1500 : request.simulation().delayMs();
        task.failStage = request.simulation() == null ? null : request.simulation().failStage();
        mapper.insertTask(task);
        for (int i = 0; i < ids.size(); i++) mapper.insertTaskAsset(task.id, ids.get(i), i);
        for (int i = 0; i < StageWorkflow.STAGES.size(); i++) mapper.insertStep(task.id, StageWorkflow.STAGES.get(i), i);
        ApiDtos.TaskResponse response = response(task);
        // No worker can see partial task/asset/step inserts, and the request never sleeps for generation.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatcher.dispatch(task.id); }
        });
        return response;
    }
    @Transactional(readOnly = true)
    public List<ApiDtos.TaskResponse> list(String userId) { return mapper.tasksForUser(userId).stream().map(this::response).toList(); }
    @Transactional(readOnly = true)
    public ApiDtos.TaskResponse get(String userId, String id) {
        Models.Task task = mapper.ownedTask(id, userId);
        if (task == null) throw new ApiException(404, "任务不存在");
        return response(task);
    }
    private ApiDtos.TaskResponse response(Models.Task task) {
        Models.Template template = mapper.template(task.templateId);
        return new ApiDtos.TaskResponse(task.id, template.id, template.title, template.coverUrl,
                task.status, task.stage, task.resultUrl, task.errorMessage, task.createdAt, task.updatedAt,
                mapper.steps(task.id).stream().map(ApiDtos.StepResponse::from).toList());
    }
}
