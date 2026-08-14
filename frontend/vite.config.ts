import { defineConfig, type ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const apiProxy: ProxyOptions = {
  target: 'http://localhost:8090',
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => {
      // Localhost often accumulates large Cookie headers that exceed Tomcat defaults.
      proxyReq.removeHeader('cookie')
    })
  },
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': apiProxy,
      '/actuator': apiProxy,
    },
  },
})
