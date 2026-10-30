package com.xiaoying.workshop;

import java.time.Instant;

/** Persistence rows are separate from public DTOs, so secrets and file paths never leak. */
public final class Models {
    private Models() {}
    public static class Identity {
        public String id, displayName, tokenHash;
        public Instant createdAt;
    }
    public static class Template {
        public String id, title, category, coverUrl, description;
        public int requiredImageCount, sortOrder;
        public long maxFileSizeBytes;
    }
    public static class Asset {
        public String id, userId, relativePath, originalName, mimeType;
        public long sizeBytes;
        public Instant createdAt;
    }
    public static class Task {
        public String id, userId, templateId, status, stage, resultUrl, errorMessage, failStage;
        public int delayMs;
        public Instant createdAt, updatedAt;
    }
    public static class Step {
        public String taskId, stage, status, errorMessage;
        public int position;
        public Instant startedAt, finishedAt;
    }
}
