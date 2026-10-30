import type { NextRequest } from "next/server";
import { proxy } from "@/lib/proxy";
export const runtime = "nodejs";
export async function GET(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> },
) {
  return proxy(request, "media", (await context.params).path);
}
