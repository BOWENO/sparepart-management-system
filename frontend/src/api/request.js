import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  withCredentials: true
})
let authRedirecting = false

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || '操作失败'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      if (!authRedirecting) {
        authRedirecting = true
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('user')
        router.push('/login').finally(() => setTimeout(() => { authRedirecting = false }, 500))
      }
    } else if (status === 403) {
      ElMessage.error('无权限执行该操作')
    } else {
      ElMessage.error(error.response?.data?.msg || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
