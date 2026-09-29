import { defineStore } from 'pinia'
import { loginApi, logoutApi, meApi } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    isLoggedIn: !!localStorage.getItem('user')
  }),
  getters: {
    role: (state) => (state.user ? state.user.role : ''),
    isAdmin: (state) => state.user && String(state.user.role).includes('ADMIN')
  },
  actions: {
    async login(username, password) {
      const data = await loginApi({ username, password })
      this.user = data
      this.isLoggedIn = true
      localStorage.setItem('user', JSON.stringify(data))
      return data
    },
    async refresh() {
      try {
        const data = await meApi()
        this.user = data
        this.isLoggedIn = true
        localStorage.setItem('user', JSON.stringify(data))
      } catch (e) {
        this.logoutLocal()
      }
    },
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // ignore
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.user = null
      this.isLoggedIn = false
      localStorage.removeItem('user')
    }
  }
})
