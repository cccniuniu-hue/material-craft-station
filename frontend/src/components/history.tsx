"use client";
import type { Task } from "@/lib/types";
import { isActive } from "@/lib/poller";
import { Icon } from "./icon";
const stageNames: Record<string, string> = {
  WAITING: "等待开始",
  CHECKING: "素材检查",
  GENERATING: "模拟生成",
  FINALIZING: "结果整理",
  COMPLETED: "创作完成",
};
const statusNames = {
  QUEUED: "排队中",
  RUNNING: "创作中",
  SUCCEEDED: "已完成",
  FAILED: "生成失败",
};
type Props = {
  tasks: Task[];
  queryErrors: Record<string, string | null>;
  listError: string | null;
  loading: boolean;
  refresh: () => void;
  retry: (id: string) => void;
  hasIdentity: boolean;
  compact?: boolean;
};
export function History({
  tasks,
  queryErrors,
  listError,
  loading,
  refresh,
  retry,
  hasIdentity,
  compact,
}: Props) {
  const activeCount = tasks.filter(isActive).length;
  return (
    <section
      className={`history ${compact ? "compact" : ""}`}
      aria-labelledby="history-title"
    >
      <div className="section-header">
        <div>
          <h2 id="history-title">
            创作记录
            {activeCount > 0 && (
              <span className="live-count">{activeCount} 个进行中</span>
            )}
          </h2>
          <p className="muted">每一份灵感，都有迹可循。</p>
        </div>
        <button
          className="subtle-button"
          onClick={refresh}
          disabled={!hasIdentity || loading}
          title="刷新创作记录"
        >
          <Icon name="refresh" size={17} />
          <span>{loading ? "读取中" : "刷新"}</span>
        </button>
      </div>
      {listError && (
        <div className="error-message" role="alert">
          <Icon name="alert" size={18} />
          <div>
            记录暂时无法读取，已有任务状态已保留。<small>{listError}</small>
            <button className="text-button" onClick={refresh}>
              重新读取
            </button>
          </div>
        </div>
      )}
      {loading && tasks.length === 0 && (
        <div className="empty-state">
          <span className="spinner" />
          <p>正在恢复你的创作记录…</p>
        </div>
      )}
      {!loading && tasks.length === 0 && (
        <div className="empty-state">
          <div className="empty-symbol">
            <Icon name="image" size={32} />
          </div>
          <h3>
            {hasIdentity ? "下一件作品，从这里开始" : "为你的灵感留个位置"}
          </h3>
          <p>
            {hasIdentity
              ? "选一个喜欢的模板，上传图片。创作进度与结果都会保存在这里。"
              : "创建创作者身份后，你的任务和作品将独立保存。"}
          </p>
          <span className="empty-stages">
            素材检查 <Icon name="arrow" size={14} /> 模拟生成{" "}
            <Icon name="arrow" size={14} /> 结果整理
          </span>
        </div>
      )}
      <div className="task-grid">
        {tasks.map((task) => (
          <TaskCard
            key={task.id}
            task={task}
            queryError={queryErrors[task.id]}
            retry={() => retry(task.id)}
          />
        ))}
      </div>
    </section>
  );
}
function TaskCard({
  task,
  queryError,
  retry,
}: {
  task: Task;
  queryError?: string | null;
  retry: () => void;
}) {
  const active = isActive(task);
  const completed = task.steps.filter(
    (step) => step.status === "SUCCEEDED",
  ).length;
  return (
    <article className={`task-card ${task.status.toLowerCase()}`}>
      <div className="task-image">
        <img
          src={task.resultUrl || task.coverUrl}
          alt={`${task.templateTitle}${task.resultUrl ? "生成结果" : "模板封面"}`}
        />
        {active && (
          <div className="task-processing">
            <span className="spinner" />
            <strong>{stageNames[task.stage]}</strong>
            <span>正在让灵感成形</span>
          </div>
        )}
        <span className={`status-badge ${task.status.toLowerCase()}`}>
          {task.status === "SUCCEEDED" && <Icon name="check" size={13} />}
          <span className="status-dot" />
          {statusNames[task.status]}
        </span>
      </div>
      <div className="task-body">
        <div className="task-title">
          <h3>{task.templateTitle}</h3>
          <time dateTime={task.createdAt}>
            {new Date(task.createdAt).toLocaleString("zh-CN", {
              month: "2-digit",
              day: "2-digit",
              hour: "2-digit",
              minute: "2-digit",
            })}
          </time>
        </div>
        <div
          className="task-progress"
          aria-label={`已完成 ${completed} 个阶段，共 3 个阶段`}
        >
          {["CHECKING", "GENERATING", "FINALIZING"].map((stage) => {
            const step = task.steps.find((step) => step.stage === stage);
            return (
              <div
                className={`step ${step?.status.toLowerCase() ?? "pending"}`}
                key={stage}
              >
                <span className="step-line" />
                <span>
                  {step?.status === "SUCCEEDED" ? (
                    <Icon name="check" size={12} />
                  ) : step?.status === "FAILED" ? (
                    <Icon name="close" size={12} />
                  ) : null}
                  {stageNames[stage]}
                </span>
              </div>
            );
          })}
        </div>
        {task.status === "FAILED" && (
          <div className="task-failure">
            <Icon name="alert" size={16} />
            <p>
              <strong>{stageNames[task.stage]}未完成</strong>
              <span>
                {task.errorMessage || "本次生成未完成，请重新创建任务。"}
              </span>
            </p>
          </div>
        )}
        {queryError && (
          <div className="query-error" role="status">
            <p>状态查询暂时中断，保留上次进度。系统将自动重试。</p>
            <small>{queryError}</small>
            <button className="text-button" onClick={retry}>
              <Icon name="refresh" size={13} />
              立即重试查询
            </button>
          </div>
        )}
        <div className="task-footer">
          <span title={task.id}>任务 {task.id.slice(0, 8)}</span>
          {task.status === "SUCCEEDED" && task.resultUrl ? (
            <a href={task.resultUrl} target="_blank" rel="noopener noreferrer">
              查看作品
              <Icon name="arrow" size={15} />
            </a>
          ) : (
            <span>{active ? "进度自动更新" : "后续阶段已停止"}</span>
          )}
        </div>
      </div>
    </article>
  );
}
