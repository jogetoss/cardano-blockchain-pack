import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import wasm from "vite-plugin-wasm";
import topLevelAwait from "vite-plugin-top-level-await";

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react(), wasm(), topLevelAwait()],
    build: {
        /**
        * See https://rollupjs.org/configuration-options/
        *
        * input -> Define your JS file(s) to be compiled. Object 'name' will be its output file name.
        *
        * output.dir -> Define where to output your compiled file(s) to. 
        *               For this maven project, output to build path for maven to pack into jar later.
        *               NOTE: Don't point to your working directory, to avoid cluttering your workspace.
        * output.entryFileNames -> [name] will follow the entry name in 'input'
        * output.assetFileNames -> Rename misc files emitted from build
        */
        rollupOptions: {
            input: {
                loginButton: "src/main/frontend/LoginButton.tsx",
            },
            output: {
                dir: "target/npm-build-output",
                entryFileNames: "[name].js",
                assetFileNames: "[name]-[hash][extname]",
            },
        },
    },
    /**
    * See https://vitejs.dev/config/shared-options.html#base
    * Define path for on-demand-loading or loading external resources (e.g.: webassembly modules .wasm). 
    * Otherwise, defaults to server path (e.g.: http//localhost:8080/).
    */
    base: "/jw/plugin/org.joget.cardano.lib.CardanoExplorerLinkFormElement",
    optimizeDeps: {
        esbuildOptions: {
            // Node.js global to browser globalThis
            define: {
                global: "globalThis",
            },
        },
    },
});
