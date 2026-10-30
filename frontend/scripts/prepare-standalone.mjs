import { cpSync } from "node:fs";
// Next's standalone bundle needs static assets alongside server.js for local `npm start`.
cpSync(".next/static", ".next/standalone/.next/static", { recursive: true });
