import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      // Proxy API calls to Java backend during development
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Proxy file download endpoint (/d/**) so the browser fetches real bytes,
      // not the SPA index.html fallback.
      '/d': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Proxy share download endpoint (/sd/**) for the public share page.
      '/sd': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
