/**
 * v-permission 指令：不满足权限时移除 DOM 元素。
 * 用法：v-permission="'ADMIN'"
 */
export default {
  mounted(el, binding) {
    let user = null
    try {
      user = JSON.parse(localStorage.getItem('user') || 'null')
    } catch (e) {
      user = null
    }
    const role = binding.value // 期望角色，如 'ADMIN'
    const userRole = user ? String(user.role) : ''
    if (!user || !userRole.includes(role)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}