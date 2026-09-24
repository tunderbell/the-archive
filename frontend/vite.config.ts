import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Vite Configuration for The Archive - APEX Console
 * Sets up the dev server on port 3000 and reverse-proxies /api and /ws (WebSocket STOMP)
 * directly to the Spring Boot backend on port 61069.
 *
 * Defines global: 'window' to polyfill the Node.js global object for older CommonJS
 * libraries such as sockjs-client.
 */
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'window',
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:61069',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:61069',
        ws: true,
        changeOrigin: true
      }
    }
  }
});
