import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',  // sockjs-client uses Node's `global`; map it to the browser equivalent
  },
  server: {
    host: true,          // bind to 0.0.0.0 so other machines on the LAN can connect
    port: 5173,         // frontend dev server port
    proxy: {
      // REST API calls — change 8080 here if you change server.port in application.properties
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // WebSocket / SockJS handshake
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
    allowedHosts: [
      'thyself-unwrapped-chirpy.ngrok-free.dev', // Allow ngrok domain for testing on mobile devices
    ],
    hmr: process.env.NGROK ? { clientPort: 443 } : true,
  },
})
