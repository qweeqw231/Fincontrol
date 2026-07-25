import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 1b.4 PR9 envfix：绑 0.0.0.0 让 IPv4 客户端也能访问
    // (之前 host: 'localhost' 在 Windows 上只解析为 IPv6 ::1，导致 127.0.0.1:5173 不可达)
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/tests/setup.js'],
    css: false,
  },
})