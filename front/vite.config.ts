import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 내비/장소/날씨/길찾기는 모두 Spring(/api/navigation/*, /api/weather)이 처리한다.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const proxy = {
    '/tunnel-source': {
      target: 'https://seoultunnel.co.kr',
      changeOrigin: true,
      rewrite: (path: string) => path.replace(/^\/tunnel-source/, '/usage'),
    },
    '/api': {
      target: env.BACKEND_URL || 'http://localhost:5555',
      changeOrigin: true,
      timeout: 0,
      proxyTimeout: 0,
    },
  }
  return {
    plugins: [vue()],
    server: { host: true, port: 8282, strictPort: true, proxy },
    preview: { host: true, port: 8282, strictPort: true, allowedHosts: true, proxy },
  }
})
