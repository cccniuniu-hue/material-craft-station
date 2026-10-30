import type { Template } from "./types";
export function validateImage(
  file: Pick<File, "type" | "size">,
  template: Template,
): string | null {
  if (!template.acceptedMimeTypes.includes(file.type))
    return "图片格式不支持，请选择 JPEG、PNG 或模板支持的格式";
  if (file.size === 0) return "图片文件为空，请重新选择";
  if (file.size > template.maxFileSizeBytes)
    return `图片超过 ${(template.maxFileSizeBytes / 1024 / 1024).toFixed(0)} MB，请压缩后上传`;
  return null;
}
export function validateSubmission(
  ids: string[],
  template: Template,
): string | null {
  if (ids.length !== template.requiredImageCount || ids.some((id) => !id))
    return `请先完成 ${template.requiredImageCount} 张图片上传`;
  if (new Set(ids).size !== ids.length) return "素材不能重复，请替换重复图片";
  return null;
}
