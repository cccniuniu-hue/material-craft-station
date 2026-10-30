import { describe, it, expect } from "vitest";
import { validateImage, validateSubmission } from "./validation";
const template = {
  id: "coast",
  title: "海岸",
  category: "旅行",
  coverUrl: "/demo/coast.svg",
  description: "",
  requiredImageCount: 2,
  acceptedMimeTypes: ["image/png", "image/jpeg"],
  maxFileSizeBytes: 1024,
};
describe("upload constraints", () => {
  it("rejects unsupported file content type, oversize files and empty files before upload", () => {
    expect(
      validateImage({ type: "image/svg+xml", size: 500 }, template),
    ).toContain("格式");
    expect(
      validateImage({ type: "image/png", size: 1025 }, template),
    ).toContain("超过");
    expect(validateImage({ type: "image/png", size: 0 }, template)).toContain(
      "空",
    );
    expect(
      validateImage({ type: "image/png", size: 1024 }, template),
    ).toBeNull();
  });
  it("requires every slot to finish and rejects duplicate asset ids", () => {
    expect(validateSubmission(["a"], template)).toContain("2");
    expect(validateSubmission(["a", "a"], template)).toContain("重复");
    expect(validateSubmission(["a", "b"], template)).toBeNull();
  });
});
