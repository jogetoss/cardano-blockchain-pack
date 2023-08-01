import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import wasm from "vite-plugin-wasm";
import topLevelAwait from "vite-plugin-top-level-await";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react(), wasm(), topLevelAwait()],
    define: { "process.env.NODE_ENV": '"production"' },
    /**
     * See https://vitejs.dev/config/shared-options.html#base
     * Define path for on-demand-loading or loading external resources (e.g.: webassembly modules .wasm).
     * Otherwise, defaults to server path (e.g.: http//localhost:8080/).
     */
    base: "/jw/plugin/org.joget.cardano.lib.CardanoExplorerLinkFormElement",
    build: {
        // sourcemap: true,
        emptyOutDir: false,
        outDir: "target/npm-build-output",
        // Library mode since this is not building a web app
        lib: {
            entry: [
                "src/main/frontend/CardanoExplorerButton.tsx",
                "src/main/frontend/wallet/CardanoWalletHandler.ts",
            ],
            formats: ["es"],
        },
    },
    optimizeDeps: {
        esbuildOptions: {
            // Node.js global to browser globalThis
            define: {
                global: "globalThis",
            },
        },
    },
});
