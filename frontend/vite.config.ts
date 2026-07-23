import { defineConfig } from "vite";
import viteReact from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import viteTsConfigPaths from "vite-tsconfig-paths";
import { tanstackRouter } from "@tanstack/router-plugin/vite";

// Plain client-side SPA build (no SSR / TanStack Start / nitro server —
// this frontend is a static bundle served independently of the Spring Boot
// backend, exactly like the rest of this repo's previous frontend).
export default defineConfig({
  plugins: [
    viteTsConfigPaths({ projects: ["./tsconfig.json"] }),
    tailwindcss(),
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    viteReact(),
  ],
  server: {
    port: 5173,
  },
  // sockjs-client (frontend/src/ws/stompClient.ts's WebSocket fallback
  // transport) references the Node global `global` at module load time.
  // Browsers don't have `global`, and unlike Webpack, Vite doesn't shim it
  // automatically — without this, the app throws `ReferenceError: global
  // is not defined` before any code of ours even runs, since it happens
  // during sockjs-client's own module evaluation.
  //
  // This `define` is a straight textual substitution applied by esbuild
  // (dev) and Rollup/esbuild (production build) alike, so it fixes both
  // `npm run dev` and `npm run build` with no extra dependency and no
  // runtime cost — `globalThis` is the real, standard, always-available
  // browser equivalent of Node's `global`, so this isn't a workaround so
  // much as pointing `global` at the object that should have been used.
  define: {
    global: "globalThis",
  },
});
