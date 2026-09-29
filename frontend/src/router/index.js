import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '首页工作台' } },
      { path: 'parts', name: 'Parts', component: () => import('../views/Parts.vue'), meta: { title: '备品档案' } },
      { path: 'devices', name: 'Devices', component: () => import('../views/Devices.vue'), meta: { title: '设备台账' } },
      { path: 'production-lines', name: 'ProductionLines', component: () => import('../views/ProductionLines.vue'), meta: { title: '产线档案' } },
      { path: 'technicians', name: 'Technicians', component: () => import('../views/Technicians.vue'), meta: { title: '技术员档案' } },
      { path: 'flows/out', redirect: '/flows/in' },
      { path: 'flows/in', name: 'PartInbound', component: () => import('../views/PartInbound.vue'), meta: { title: '备品入库', adminOnly: true, desktopOnly: true } },
      { path: 'flows/replace', name: 'Replace', component: () => import('../views/Replace.vue'), meta: { title: '备品更换', adminOnly: true, desktopOnly: true } },
      { path: 'scrap-processing', name: 'ScrapProcessing', component: () => import('../views/ScrapProcessing.vue'), meta: { title: '报废品处理', adminOnly: true, desktopOnly: true } },
      { path: 'inventory-flows', name: 'InventoryFlows', component: () => import('../views/InventoryFlows.vue'), meta: { title: '库存流水' } },
      { path: 'checks', name: 'Checks', component: () => import('../views/Checks.vue'), meta: { title: '盘点记录' } },
      { path: 'analysis', name: 'Analysis', component: () => import('../views/Analysis.vue'), meta: { title: '分析看板' } },
      { path: 'logs', name: 'Logs', component: () => import('../views/Logs.vue'), meta: { title: '日志中心' } },
      { path: 'messages', name: 'Messages', component: () => import('../views/Messages.vue'), meta: { title: '消息中心' } },
      { path: 'user-accounts', name: 'UserAccounts', component: () => import('../views/UserAccounts.vue'), meta: { title: '账号管理', supervisorOnly: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const loggedIn = !!localStorage.getItem('user')
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (to.meta.public) {
    next()
  } else if (!loggedIn) {
    next('/login')
  } else if (to.meta.adminOnly && !String(user?.role || '').includes('ADMIN')) {
    next('/dashboard')
  } else if (to.meta.supervisorOnly && !String(user?.role || '').includes('VIEWER')) {
    next('/dashboard')
  } else if (to.meta.desktopOnly && window.innerWidth < 768) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
