// SVELTEKIT-BACKEND-PRESERVED: moved out of svelte/ during the cljs migration; not wired.
//
// Originally `appview/drive-app-component/svelte/test/drive.test.ts`, run by
// `svelte/package.json`'s `vitest run` (config: `svelte/vitest.config.ts`).
// It imports `../src/app.js` and asserts on `app.fetch(req, env)` for
// /health, XRPC proxy, invalid-JSON, and the ASSETS-less 404 case — that is
// coverage of the Worker facade at `appview/drive-app-component/src/app.ts`
// (compare its `/health` actor literal, its `NSID_PREFIX` dispatch, and its
// final `env.ASSETS` fallthrough — all four assertions here match that file
// line for line), not of the deleted Svelte UI. It is preserved rather than
// deleted so this coverage of live code is not lost.
//
// It did NOT run as committed: `../src/app.js` resolved relative to
// `svelte/test/` to `svelte/src/app.js`, which never existed in this repo
// (the worker has always lived at the component root, `src/app.ts`, one
// directory above `svelte/`, not inside it). The import below is corrected
// now that the file sits next to that worker, but this file is still not
// wired into any test runner: `svelte/` (and its `vitest`/`vitest.config.ts`/
// `package.json`) is deleted by this migration, and no equivalent Vitest
// harness exists at the component root or under `cljs/` (whose `npm test`
// runs `shadow-cljs compile test` + Node, a ClojureScript/re-frame suite,
// not this Vitest+TypeScript one). Reviving Worker-facade testing here would
// need a new `package.json` + `vitest` (or a `.cljc`/nbb equivalent) at the
// component root — out of scope for a frontend migration.
import { describe, it, expect, vi } from "vitest";
import app from "../src/app.js";

global.fetch = vi.fn();

describe("drive appview facade", () => {
  it("returns health check on /health", async () => {
    const req = new Request("https://drive.etzhayyim.com/health");
    const res = await app.fetch(req, {});
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.ok).toBe(true);
    expect(data.actor).toBe("did:web:drive.etzhayyim.com");
  });

  it("handles invalid json in POST gracefully", async () => {
    const req = new Request("https://drive.etzhayyim.com/xrpc/com.etzhayyim.apps.drive.test", {
      method: "POST",
      body: "{ bad json",
    });
    const res = await app.fetch(req, {});
    expect(res.status).toBe(400);
    const data = await res.json() as any;
    expect(data.error).toBe("InvalidJson");
  });

  it("proxies valid XRPC to dispatcher", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(JSON.stringify({ success: true })));
    const req = new Request("https://drive.etzhayyim.com/xrpc/com.etzhayyim.apps.drive.ping");
    const res = await app.fetch(req, {});
    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.success).toBe(true);
  });

  it("returns 404 for unknown path without ASSETS", async () => {
    const req = new Request("https://drive.etzhayyim.com/unknown");
    const res = await app.fetch(req, {});
    expect(res.status).toBe(404);
  });
});
