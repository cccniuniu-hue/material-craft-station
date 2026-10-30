export async function api<T>(
  path: string,
  token?: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  const response = await fetch(path, { ...init, headers, cache: "no-store" });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(
      error?.message ?? `请求未完成（${response.status}），请稍后重试`,
    );
  }
  return response.json() as Promise<T>;
}
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "连接暂时不可用，请重试";
}
