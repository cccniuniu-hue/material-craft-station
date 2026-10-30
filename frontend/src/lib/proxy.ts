import { NextRequest } from "next/server";
export async function proxy(
  request: NextRequest,
  namespace: string,
  path: string[],
) {
  const base = process.env.JAVA_API_URL ?? "http://localhost:8080";
  const target = `${base.replace(/\/$/, "")}/${namespace}/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;
  const headers = new Headers();
  for (const name of ["authorization", "content-type", "accept"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  try {
    const response = await fetch(target, {
      method: request.method,
      headers,
      cache: "no-store",
      redirect: "manual",
      body: ["GET", "HEAD"].includes(request.method)
        ? undefined
        : await request.arrayBuffer(),
      signal: AbortSignal.any([request.signal, AbortSignal.timeout(30000)]),
    });
    const outgoing = new Headers({ "X-Content-Type-Options": "nosniff" });
    for (const name of [
      "content-type",
      "content-disposition",
      "cache-control",
    ]) {
      const value = response.headers.get(name);
      if (value) outgoing.set(name, value);
    }
    if (namespace === "api") outgoing.set("cache-control", "no-store");
    return new Response(response.body, {
      status: response.status,
      headers: outgoing,
    });
  } catch {
    return Response.json(
      { message: "创作服务暂时无法连接，请确认服务已启动后重试" },
      { status: 503 },
    );
  }
}
