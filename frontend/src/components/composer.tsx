"use client";
import { useEffect, useRef, useState } from "react";
import { api, errorMessage } from "@/lib/api";
import { validateImage, validateSubmission } from "@/lib/validation";
import type { Asset, Task, Template } from "@/lib/types";
import { Icon } from "./icon";
type Slot = {
  asset?: Asset;
  preview?: string;
  name?: string;
  uploading?: boolean;
  error?: string;
};
export function Composer({
  template,
  token,
  onTask,
  onBack,
}: {
  template: Template;
  token?: string;
  onTask: (task: Task) => void;
  onBack: () => void;
}) {
  const [slots, setSlots] = useState<Slot[]>(() =>
    Array.from({ length: template.requiredImageCount }, () => ({})),
  );
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [delay, setDelay] = useState(1500);
  const [failStage, setFailStage] = useState("");
  const [examplesLoading, setExamplesLoading] = useState(false);
  const controllers = useRef(new Map<number, AbortController>());
  const previews = useRef(new Map<number, string>());
  const submitController = useRef<AbortController | null>(null);
  const demoController = useRef<AbortController | null>(null);
  const submittingRef = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    const uploads = controllers.current;
    const urls = previews.current;
    return () => {
      mounted.current = false;
      uploads.forEach((item) => item.abort());
      urls.forEach(URL.revokeObjectURL);
      submitController.current?.abort();
      demoController.current?.abort();
    };
  }, []);
  const uploading = slots.some((slot) => slot.uploading);
  const ready = slots.every((slot) => slot.asset) && !uploading;
  function patchSlot(index: number, slot: Slot) {
    setSlots((previous) =>
      previous.map((item, i) => (i === index ? slot : item)),
    );
  }
  function remove(index: number) {
    controllers.current.get(index)?.abort();
    controllers.current.delete(index);
    const url = previews.current.get(index);
    if (url) URL.revokeObjectURL(url);
    previews.current.delete(index);
    patchSlot(index, {});
    setNotice("");
  }
  async function upload(index: number, file: File) {
    if (!token || submittingRef.current) return;
    remove(index);
    const invalid = validateImage(file, template);
    if (invalid) {
      patchSlot(index, { error: invalid });
      return;
    }
    const controller = new AbortController();
    controllers.current.set(index, controller);
    const preview = URL.createObjectURL(file);
    previews.current.set(index, preview);
    patchSlot(index, { preview, name: file.name, uploading: true });
    setError("");
    try {
      const body = new FormData();
      body.append("file", file);
      const asset = await api<Asset>("/api/assets", token, {
        method: "POST",
        body,
        signal: controller.signal,
      });
      if (
        mounted.current &&
        controllers.current.get(index) === controller &&
        !controller.signal.aborted
      )
        patchSlot(index, { asset, preview, name: file.name });
    } catch (e) {
      if (mounted.current && !controller.signal.aborted)
        patchSlot(index, { preview, name: file.name, error: errorMessage(e) });
    }
  }
  async function useExamples() {
    if (!token || examplesLoading || submittingRef.current) return;
    const controller = new AbortController();
    demoController.current?.abort();
    demoController.current = controller;
    setExamplesLoading(true);
    setError("");
    try {
      const paths = [
        template.coverUrl,
        "/demo/botanical.jpg",
        "/demo/city.jpg",
      ];
      await Promise.all(
        slots.map(async (_, index) => {
          const response = await fetch(paths[index], {
            signal: controller.signal,
          });
          if (!response.ok) throw new Error("示例图片加载失败，请选择本地图片");
          const blob = await response.blob();
          if (!controller.signal.aborted && mounted.current)
            await upload(
              index,
              new File([blob], `示例素材-${index + 1}.jpg`, {
                type: blob.type || "image/jpeg",
              }),
            );
        }),
      );
    } catch (e) {
      if (mounted.current && !controller.signal.aborted)
        setError(errorMessage(e));
      controller.abort();
    } finally {
      if (mounted.current && demoController.current === controller)
        setExamplesLoading(false);
    }
  }
  async function submit() {
    if (submittingRef.current || !token) return;
    const invalid = validateSubmission(
      slots.flatMap((slot) => (slot.asset ? [slot.asset.id] : [])),
      template,
    );
    if (uploading || invalid) {
      setError(invalid || "请等待素材上传完成");
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    setError("");
    setNotice("");
    const controller = new AbortController();
    submitController.current = controller;
    try {
      const task = await api<Task>("/api/tasks", token, {
        method: "POST",
        signal: controller.signal,
        body: JSON.stringify({
          templateId: template.id,
          assetIds: slots.map((slot) => slot.asset!.id),
          simulation: { delayMs: delay, failStage: failStage || null },
        }),
      });
      if (!mounted.current || controller.signal.aborted) return;
      onTask(task);
      previews.current.forEach(URL.revokeObjectURL);
      previews.current.clear();
      setSlots(Array.from({ length: template.requiredImageCount }, () => ({})));
      setNotice("任务已加入创作记录。你可以继续上传，开始下一件作品。");
    } catch (e) {
      if (mounted.current && !controller.signal.aborted)
        setError(errorMessage(e));
    } finally {
      submittingRef.current = false;
      if (mounted.current && !controller.signal.aborted) setSubmitting(false);
    }
  }
  return (
    <section className="composer panel" aria-labelledby="template-title">
      <button className="back-link" onClick={onBack}>
        <Icon name="back" size={17} /> 返回模板
      </button>
      <div className="composer-cover">
        <img src={template.coverUrl} alt={template.title} />
        <span className="cover-category">{template.category}</span>
      </div>
      <h2 id="template-title">{template.title}</h2>
      <p className="muted template-description">{template.description}</p>
      <div className="upload-heading">
        <h3>
          上传你的素材{" "}
          <span>
            {slots.filter((slot) => slot.asset).length}/
            {template.requiredImageCount}
          </span>
        </h3>
        <button
          className="text-button"
          onClick={useExamples}
          disabled={!token || uploading || examplesLoading || submitting}
        >
          {examplesLoading ? "加载示例中…" : "使用示例图片"}
        </button>
      </div>
      <p className="upload-help">
        需要 {template.requiredImageCount} 张图片 ·{" "}
        {template.acceptedMimeTypes
          .map((type) => type.split("/")[1].toUpperCase())
          .join(" / ")}{" "}
        · 每张最多 {Math.round(template.maxFileSizeBytes / 1024 / 1024)} MB
      </p>
      {!token && (
        <p className="inline-note">
          在右上角创建创作者身份，即可上传和生成作品。
        </p>
      )}
      <div className={`upload-slots ${slots.length > 1 ? "multiple" : ""}`}>
        {slots.map((slot, index) => (
          <div
            className={`upload-slot ${slot.error ? "invalid" : ""}`}
            key={index}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => {
              event.preventDefault();
              if (submitting || examplesLoading || !token) return;
              if (event.dataTransfer.files.length !== 1) {
                patchSlot(index, { ...slot, error: "每个位置请放入一张图片" });
                return;
              }
              void upload(index, event.dataTransfer.files[0]);
            }}
          >
            <label className="upload-label">
              <input
                type="file"
                accept={template.acceptedMimeTypes.join(",")}
                aria-label={`上传图片 ${index + 1}`}
                disabled={!token || submitting || examplesLoading}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void upload(index, file);
                  event.target.value = "";
                }}
              />
              {slot.preview ? (
                <img src={slot.preview} alt={`素材 ${index + 1} 预览`} />
              ) : (
                <span className="upload-placeholder">
                  <Icon name="upload" size={24} />
                  <strong>添加图片 {slots.length > 1 ? index + 1 : ""}</strong>
                  <span>点击选择或拖入图片</span>
                </span>
              )}
              {slot.preview && (
                <span className="replace-hint">
                  <Icon name="refresh" size={15} /> 点击替换
                </span>
              )}
              {slot.uploading && (
                <span className="upload-overlay">
                  <span className="spinner" /> 正在上传…
                </span>
              )}
            </label>
            {slot.preview && (
              <button
                className="remove-image"
                aria-label={`删除图片 ${index + 1}`}
                title="移除图片"
                disabled={submitting || examplesLoading}
                onClick={() => remove(index)}
              >
                <Icon name="close" size={15} />
              </button>
            )}
            {slot.name && (
              <div className="file-name">
                {slot.asset && <Icon name="check" size={13} />}
                <span title={slot.name}>{slot.name}</span>
              </div>
            )}
            {slot.error && (
              <p className="slot-error" role="alert">
                {slot.error}，请重新选择。
              </p>
            )}
          </div>
        ))}
      </div>
      <details className="demo-controls">
        <summary>
          演示设置 <span>可复现不同任务状态</span>
        </summary>
        <div className="demo-fields">
          <label>
            阶段耗时
            <select
              value={delay}
              onChange={(event) => setDelay(Number(event.target.value))}
              disabled={submitting}
            >
              <option value={500}>0.5 秒 / 阶段</option>
              <option value={1500}>1.5 秒 / 阶段</option>
              <option value={5000}>5 秒 / 阶段</option>
            </select>
          </label>
          <label>
            模拟结果
            <select
              value={failStage}
              onChange={(event) => setFailStage(event.target.value)}
              disabled={submitting}
            >
              <option value="">全部成功</option>
              <option value="CHECKING">素材检查失败</option>
              <option value="GENERATING">模拟生成失败</option>
              <option value="FINALIZING">结果整理失败</option>
            </select>
          </label>
        </div>
      </details>
      {error && (
        <p className="error-message" role="alert">
          <Icon name="alert" size={17} />
          {error}
        </p>
      )}
      {notice && (
        <p className="success-message" role="status">
          <Icon name="check" size={17} />
          {notice}
        </p>
      )}
      <button
        className="primary create-button"
        onClick={submit}
        disabled={!token || !ready || submitting || examplesLoading}
      >
        {submitting ? (
          <>
            <span className="spinner" />
            正在创建任务…
          </>
        ) : (
          <>
            <Icon name="spark" />
            开始创作
            <Icon name="arrow" size={18} />
          </>
        )}
      </button>
      <p className="generation-note">
        模拟生成流程 · 关闭页面后，任务仍在服务端继续
      </p>
    </section>
  );
}
