package com.xiaoying.workshop;

import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;

@Mapper
public interface WorkshopMapper {
    @Insert("INSERT INTO identities(id,display_name,token_hash,created_at) VALUES(#{id},#{displayName},#{tokenHash},#{createdAt})")
    void insertIdentity(Models.Identity identity);
    @Select("SELECT * FROM identities WHERE token_hash=#{hash}")
    Models.Identity identityByToken(String hash);
    @Select("<script>SELECT * FROM templates <if test='category != null and category != \"\"'>WHERE category=#{category}</if> ORDER BY sort_order</script>")
    List<Models.Template> templates(@Param("category") String category);
    @Select("SELECT * FROM templates WHERE id=#{id}")
    Models.Template template(String id);
    @Insert("INSERT INTO assets(id,user_id,relative_path,original_name,mime_type,size_bytes,created_at) VALUES(#{id},#{userId},#{relativePath},#{originalName},#{mimeType},#{sizeBytes},#{createdAt})")
    void insertAsset(Models.Asset asset);
    @Select("SELECT * FROM assets WHERE id=#{id}")
    Models.Asset asset(String id);
    @Insert("INSERT INTO generation_tasks(id,user_id,template_id,status,stage,delay_ms,fail_stage,created_at,updated_at) VALUES(#{id},#{userId},#{templateId},#{status},#{stage},#{delayMs},#{failStage},#{createdAt},#{updatedAt})")
    void insertTask(Models.Task task);
    @Insert("INSERT INTO task_assets(task_id,asset_id,position) VALUES(#{taskId},#{assetId},#{position})")
    void insertTaskAsset(@Param("taskId") String taskId, @Param("assetId") String assetId, @Param("position") int position);
    @Insert("INSERT INTO task_steps(task_id,stage,position,status) VALUES(#{taskId},#{stage},#{position},'PENDING')")
    void insertStep(@Param("taskId") String taskId, @Param("stage") String stage, @Param("position") int position);
    @Select("SELECT * FROM generation_tasks WHERE user_id=#{userId} ORDER BY created_at DESC,id DESC")
    List<Models.Task> tasksForUser(String userId);
    @Select("SELECT * FROM generation_tasks WHERE id=#{id} AND user_id=#{userId}")
    Models.Task ownedTask(@Param("id") String id, @Param("userId") String userId);
    @Select("SELECT * FROM generation_tasks WHERE id=#{id}")
    Models.Task task(String id);
    @Select("SELECT * FROM generation_tasks WHERE id=#{id} FOR UPDATE")
    Models.Task lockTask(String id);
    @Select("SELECT * FROM generation_tasks WHERE status IN ('QUEUED','RUNNING')")
    List<Models.Task> activeTasks();
    @Select("SELECT * FROM task_steps WHERE task_id=#{taskId} ORDER BY position")
    List<Models.Step> steps(String taskId);
    @Select("SELECT a.* FROM assets a JOIN task_assets ta ON ta.asset_id=a.id WHERE ta.task_id=#{taskId} ORDER BY ta.position")
    List<Models.Asset> taskAssets(String taskId);
    @Update("UPDATE generation_tasks SET status=#{status},stage=#{stage},result_url=#{resultUrl},error_message=#{errorMessage},updated_at=#{updatedAt} WHERE id=#{id}")
    void updateTask(Models.Task task);
    @Update("UPDATE task_steps SET status=#{status},started_at=#{startedAt},finished_at=#{finishedAt},error_message=#{errorMessage} WHERE task_id=#{taskId} AND stage=#{stage}")
    void updateStep(Models.Step step);
}
