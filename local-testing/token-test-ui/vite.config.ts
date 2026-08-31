import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/** Runs the isolated local test UI on a different origin from the production MSAL application. */
export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5174,
    strictPort: true,
  },
  preview: {
    host: "127.0.0.1",
    port: 4174,
    strictPort: true,
  },
});
