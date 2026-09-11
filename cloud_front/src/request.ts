import axios from 'axios'
import { message } from 'ant-design-vue'

const PROD_BASE_URL = 'http://111.230.167.134'
// 开发环境走「同源」：/api 请求打到当前页面自己的域名+端口，再由 vite dev server 的
// proxy 转发到本机 8123（配置见 vite.config.ts 的 server.proxy）。
// 这样无论是 localhost、局域网 IP，还是 cpolar/ngrok 的公网域名，前后端都只用一个端口，
// 不需要为每种访问方式改地址，也不会出现 https 页面请求 http 接口被浏览器拦截的情况。
const DEV_BASE_URL = ''

// 创建 Axios 实例
// 如果不写这个 withCredentials 前端就不会携带 cookie ，会出现前端已经登录但是后端不认的情况
const myAxios = axios.create({
  baseURL: DEV_BASE_URL,
  // baseURL: PROD_BASE_URL,
  // 首次 RAG 检索 / AI 对话在本机要跑向量化，实测可达 20s 左右，10s 会误判超时
  timeout: 60000,
  withCredentials: true,
})
// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录,这部分的代码和后端绑定
    if (data.code === 40100) {
      // 这个地方比较关键，而且显然是后期更改的地方
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios
