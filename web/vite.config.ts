import { defineConfig } from 'vite'
import preact from '@preact/preset-vite'

// Relative base: the build works under whatever path it is deployed to
// (…/pwa/ next to the API, or the root of a front domain). No host is ever
// written into the output.
export default defineConfig({
  base: './',
  plugins: [preact()],
  build: {
    outDir: '../backend/Faoxima-1.0.0/pwa',
    emptyOutDir: true,
    sourcemap: false,
    target: 'es2019',
    assetsInlineLimit: 0,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/app-[hash].js',
        chunkFileNames: 'assets/chunk-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]'
      }
    }
  },
  server: {
    proxy: {
      '/api': { target: process.env.GHAJAR_API_ORIGIN || 'http://127.0.0.1:8080', changeOrigin: true }
    }
  }
})
