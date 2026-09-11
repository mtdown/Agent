import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    // 监听所有网卡：手机与电脑处于同一 WiFi 时，可用 http://<本机局域网IP>:3000 访问
    host: '0.0.0.0',
    port: 3000,
    // Vite 6 起默认只放行 localhost / 纯 IP 的 Host，cpolar、ngrok 这类公网域名会被
    // 拦成 "Blocked request. This host is not allowed."，所以公网穿透时必须打开这项。
    allowedHosts: true,
    proxy: {
      // 后端接口做成同源：页面和接口都走 3000 这一个端口，由 dev server 转发到后端 8123。
      // 好处：① 局域网访问、公网穿透都只需转发一个端口；
      //      ② 穿透给的是 https 域名时，不会出现「https 页面请求 http 接口」被浏览器拦截。
      // 注意：后端 context-path 本身就是 /api，这里不要 rewrite 掉前缀。
      // 附带影响：https 穿透域名下 HMR 的 ws 会连不上（控制台报错、热更新失效），
      // 属于预期现象，手动刷新页面即可，不影响功能。
      '/api': {
        target: 'http://127.0.0.1:8123',
        changeOrigin: false,
      },
    },
  },
})
