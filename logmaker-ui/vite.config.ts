import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vueJsx from "@vitejs/plugin-vue-jsx";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), vueJsx()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  build: {
    outDir: "../core/src/main/resources/static",
  },
  server: {
    proxy: {
      "/api/v1": {
        target: "http://192.168.150.73:19999",
        changeOrigin: true,
        secure: false,
        ws: true,
      },
    },
  },
});
