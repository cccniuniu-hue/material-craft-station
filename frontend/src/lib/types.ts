export type Identity = { id: string; displayName: string; token: string };
export type Template = {
  id: string;
  title: string;
  category: string;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};
export type Asset = {
  id: string;
  url: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
};
export type Stage =
  | "WAITING"
  | "CHECKING"
  | "GENERATING"
  | "FINALIZING"
  | "COMPLETED";
export type Step = {
  stage: Stage;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
  startedAt: string | null;
  finishedAt: string | null;
  errorMessage: string | null;
};
export type Task = {
  id: string;
  templateId: string;
  templateTitle: string;
  coverUrl: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  stage: Stage;
  resultUrl: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  steps: Step[];
};
