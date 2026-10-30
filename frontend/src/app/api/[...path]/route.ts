import type { NextRequest } from "next/server";
import { proxy } from "@/lib/proxy";
export const runtime = "nodejs";
async function handler(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> },
) {
  return proxy(request, "api", (await context.params).path);
}
export { handler as GET, handler as POST };
