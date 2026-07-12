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
});
