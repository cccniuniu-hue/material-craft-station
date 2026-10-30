"use client";
import { FormEvent, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { api, errorMessage } from "@/lib/api";
import type { Identity, Template } from "@/lib/types";
import { useTasks } from "@/lib/use-tasks";
import { isActive } from "@/lib/poller";
import { Composer } from "./composer";
import { History } from "./history";
import { Icon } from "./icon";
type Saved = { profiles: Identity[]; activeId: string };
const storageKey = "yingzuo-identities-v1";
export function Studio() {
  const [saved, setSaved] = useState<Saved>({ profiles: [], activeId: "" });
  const [hydrated, setHydrated] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const creation = useRef<AbortController | null>(null);
  const active = saved.profiles.find(
    (profile) => profile.id === saved.activeId,
  );
  useEffect(() => {
    // Browser storage is external state; restore only after hydration to avoid SSR mismatch.
    try {
      const data = JSON.parse(
        localStorage.getItem(storageKey) || "null",
      ) as Saved | null;
      if (data && Array.isArray(data.profiles)) {
        const profiles = data.profiles.filter(
          (profile) =>
            typeof profile.id === "string" &&
            typeof profile.token === "string" &&
            typeof profile.displayName === "string",
        );
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setSaved({
          profiles,
          activeId: profiles.some((profile) => profile.id === data.activeId)
            ? data.activeId
            : (profiles[0]?.id ?? ""),
        });
      }
    } catch {
      setError("浏览器身份记录无法读取，请重新创建身份。");
    }
    setHydrated(true);
    return () => creation.current?.abort();
  }, []);
  function persist(next: Saved) {
    setSaved(next);
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
    } catch {
      setError("当前浏览器无法保存身份，刷新后可能需要重新创建。");
    }
  }
  async function createIdentity(event: FormEvent) {
    event.preventDefault();
    if (creation.current || !name.trim()) return;
    const controller = new AbortController();
    creation.current = controller;
    setCreating(true);
    setError("");
    try {
      const identity = await api<Identity>("/api/identities", undefined, {
        method: "POST",
        body: JSON.stringify({ displayName: name.trim() }),
        signal: controller.signal,
      });
      if (controller.signal.aborted) return;
      persist({
        profiles: [...saved.profiles, identity],
        activeId: identity.id,
      });
      setShowCreate(false);
      setName("");
    } catch (e) {
      if (!controller.signal.aborted) setError(errorMessage(e));
    } finally {
      creation.current = null;
      if (!controller.signal.aborted) setCreating(false);
    }
  }
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" href="/" aria-label="映作 Studio 首页">
          <span className="brand-mark">
            <Icon name="spark" size={22} />
          </span>
          <strong>
            映作 <span>Studio</span>
          </strong>
        </Link>
        <span className="topbar-subtitle">让每一份灵感，都成为作品</span>
        <div className="identity-controls">
          {active && (
            <label className="identity-select">
              <span className="avatar">{active.displayName.slice(0, 1)}</span>
              <select
                aria-label="切换创作者"
                value={saved.activeId}
                disabled={creating}
                onChange={(event) =>
                  persist({ ...saved, activeId: event.target.value })
                }
              >
                {saved.profiles.map((profile) => (
                  <option value={profile.id} key={profile.id}>
                    {profile.displayName}
                  </option>
                ))}
              </select>
            </label>
          )}
          <button
            className={active ? "icon-button" : "primary small"}
            onClick={() => setShowCreate((value) => !value)}
            disabled={!hydrated || creating}
            aria-label="创建新身份"
            title="创建新身份"
          >
            <Icon name="plus" size={17} />
            {!active && "创建创作者身份"}
          </button>
        </div>
      </header>
      {(showCreate || (hydrated && !active)) && (
        <form className="identity-form" onSubmit={createIdentity}>
          <div>
            <strong>{active ? "新建创作者" : "你好，未来的创作者"}</strong>
            <p>输入昵称创建演示身份。作品按身份独立保存，可随时切换。</p>
          </div>
          <label>
            <span className="sr-only">创作者昵称</span>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={40}
              placeholder="你的创作者昵称"
              disabled={creating}
              required
              autoComplete="nickname"
            />
          </label>
          <button className="primary small" disabled={creating || !name.trim()}>
            {creating ? "正在创建…" : "开始创作"}
            <Icon name="arrow" size={16} />
          </button>
          {active && (
            <button
              type="button"
              className="icon-button"
              onClick={() => setShowCreate(false)}
              aria-label="关闭新建身份"
            >
              <Icon name="close" size={17} />
            </button>
          )}
        </form>
      )}
      {error && (
        <div className="global-error error-message" role="alert">
          <Icon name="alert" size={18} />
          {error}
          <button className="text-button" onClick={() => setError("")}>
            收起
          </button>
        </div>
      )}
      {hydrated ? (
        <Workspace key={active?.id ?? "guest"} identity={active} />
      ) : (
        <main className="startup">
          <span className="spinner" />
          正在打开创作空间…
        </main>
      )}
    </div>
  );
}
function Workspace({ identity }: { identity?: Identity }) {
  const [view, setView] = useState<"templates" | "history">("templates");
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<Template | null>(null);
  const [detailError, setDetailError] = useState("");
  const [detailVersion, setDetailVersion] = useState(0);
  const board = useTasks(identity?.token);
  const activeCount = board.tasks.filter(isActive).length;
  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    api<Template>(`/api/templates/${selected}`, identity?.token, {
      signal: controller.signal,
    })
      .then((template) => {
        if (!controller.signal.aborted) setDetail(template);
      })
      .catch((error) => {
        if (!controller.signal.aborted) setDetailError(errorMessage(error));
      });
    return () => controller.abort();
  }, [selected, identity?.token, detailVersion]);
  function navigate(next: "templates" | "history") {
    setView(next);
    setSelected(null);
    setDetail(null);
    setDetailError("");
  }
  function choose(template: Template) {
    setSelected(template.id);
    setDetail(null);
    setDetailError("");
  }
  const history = (
    <History {...board} hasIdentity={!!identity} compact={!!selected} />
  );
  return (
    <div className="workspace">
      <aside className="sidebar">
        <div className="workspace-label">我的工作台</div>
        <nav aria-label="主导航">
          <button
            className={
              view === "templates" ? "nav-button selected" : "nav-button"
            }
            onClick={() => navigate("templates")}
          >
            <Icon name="grid" />
            探索模板
            <Icon name="arrow" size={14} />
          </button>
          <button
            className={
              view === "history" ? "nav-button selected" : "nav-button"
            }
            onClick={() => navigate("history")}
          >
            <Icon name="history" />
            创作记录
            {activeCount > 0 && (
              <span className="nav-count">{activeCount}</span>
            )}
          </button>
        </nav>
        <div className="sidebar-bottom">
          <div className="studio-note">
            <Icon name="spark" size={23} />
            <strong>小小灵感，大有可能。</strong>
            <p>
              从一张照片出发，
              <br />
              看看创意能走多远。
            </p>
          </div>
          <span className="demo-label">
            演示工作空间 <span />
          </span>
        </div>
      </aside>
      <main className={selected ? "main detail-main" : "main"}>
        {selected ? (
          <div className="detail-layout">
            {detail?.id === selected ? (
              <Composer
                key={selected}
                template={detail}
                token={identity?.token}
                onTask={board.addTask}
                onBack={() => navigate("templates")}
              />
            ) : (
              <section className="panel detail-loading">
                <button
                  className="back-link"
                  onClick={() => navigate("templates")}
                >
                  <Icon name="back" />
                  返回模板
                </button>
                {detailError ? (
                  <div className="error-message" role="alert">
                    <p>{detailError}</p>
                    <button
                      className="text-button"
                      onClick={() => {
                        setDetailError("");
                        setDetailVersion((value) => value + 1);
                      }}
                    >
                      重试加载
                    </button>
                  </div>
                ) : (
                  <div className="empty-state">
                    <span className="spinner" />
                    正在加载模板…
                  </div>
                )}
              </section>
            )}
            {history}
          </div>
        ) : view === "history" ? (
          <div className="full-history">
            <div className="page-title">
              <h1>你的创作，持续发生。</h1>
              <p>从第一步到最后一帧，所有作品都在这里。</p>
            </div>
            {history}
          </div>
        ) : (
          <Gallery token={identity?.token} onSelect={choose} />
        )}
      </main>
    </div>
  );
}
function Gallery({
  token,
  onSelect,
}: {
  token?: string;
  onSelect: (template: Template) => void;
}) {
  const [category, setCategory] = useState("");
  const [categories, setCategories] = useState<string[]>([]);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [error, setError] = useState("");
  const [loadedCategory, setLoadedCategory] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const loading = loadedCategory !== category;
  useEffect(() => {
    const controller = new AbortController();
    api<Template[]>(
      `/api/templates${category ? `?category=${encodeURIComponent(category)}` : ""}`,
      token,
      { signal: controller.signal },
    )
      .then((data) => {
        if (controller.signal.aborted) return;
        setTemplates(data);
        setLoadedCategory(category);
        if (!category)
          setCategories([
            ...new Set(data.map((template) => template.category)),
          ]);
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setError(errorMessage(e));
          setLoadedCategory(category);
        }
      });
    return () => controller.abort();
  }, [category, token, reload]);
  function filter(next: string) {
    if (next === category) {
      if (error) {
        setLoadedCategory(null);
        setReload((value) => value + 1);
        setError("");
      }
      return;
    }
    setCategory(next);
    setError("");
  }
  return (
    <section className="gallery">
      <div className="gallery-heading">
        <div className="page-title">
          <h1>给灵感，一个新模样。</h1>
          <p>选一种风格，放入你的故事。剩下的，交给创作。</p>
        </div>
        <div className="gallery-caption">
          <span className="tiny-spark">
            <Icon name="spark" size={17} />
          </span>
          为你的下一件作品
        </div>
      </div>
      <div className="gallery-toolbar">
        <div className="category-list" aria-label="模板分类">
          <button
            className={!category ? "category active" : "category"}
            onClick={() => filter("")}
          >
            全部模板
          </button>
          {categories.map((item) => (
            <button
              key={item}
              className={category === item ? "category active" : "category"}
              onClick={() => filter(item)}
            >
              {item}
            </button>
          ))}
        </div>
        <span className="template-count">
          {loading ? "正在加载" : `${templates.length} 个模板`}
        </span>
      </div>
      {error ? (
        <div className="gallery-error empty-state">
          <Icon name="alert" size={30} />
          <h3>模板暂时没有加载出来</h3>
          <p>{error}</p>
          <button
            className="subtle-button"
            onClick={() => {
              setError("");
              setLoadedCategory(null);
              setReload((value) => value + 1);
            }}
          >
            <Icon name="refresh" size={17} />
            重新加载
          </button>
        </div>
      ) : loading ? (
        <div className="template-grid" aria-label="正在加载模板">
          {Array.from({ length: 6 }, (_, index) => (
            <div className="template-skeleton" key={index} />
          ))}
        </div>
      ) : templates.length === 0 ? (
        <div className="empty-state">
          <Icon name="image" size={30} />
          <h3>这个分类还没有模板</h3>
          <button className="text-button" onClick={() => filter("")}>
            浏览全部模板
          </button>
        </div>
      ) : (
        <div className="template-grid">
          {templates.map((template, index) => (
            <button
              className={`template-card card-${index}`}
              key={template.id}
              onClick={() => onSelect(template)}
            >
              <div className="template-art">
                <img
                  src={template.coverUrl}
                  alt={template.title}
                  loading={index < 3 ? "eager" : "lazy"}
                />
                <span className="template-category">{template.category}</span>
                <span className="template-open">
                  <Icon name="arrow" size={22} />
                </span>
                <div className="template-overlay">
                  <span>从你的视角，重新创作</span>
                </div>
              </div>
              <div className="template-meta">
                <div>
                  <h2>{template.title}</h2>
                  <p>{template.description}</p>
                </div>
                <span className="image-count">
                  <Icon name="image" size={15} />
                  {template.requiredImageCount} 张
                </span>
              </div>
            </button>
          ))}
        </div>
      )}
      <footer className="gallery-footer">
        <span>好作品，始于一次尝试。</span>
        <span>YINGZUO STUDIO</span>
      </footer>
    </section>
  );
}
