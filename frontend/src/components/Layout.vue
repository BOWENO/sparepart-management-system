<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">备品管理系统</div>
      <el-menu :default-active="$route.path" router background-color="#001529"
        text-color="#ffffff" active-text-color="#409eff">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon>首页工作台</el-menu-item>
        <el-menu-item index="/parts"><el-icon><Box /></el-icon>备品档案</el-menu-item>
        <el-menu-item index="/devices"><el-icon><Cpu /></el-icon>设备台账</el-menu-item>
        <el-menu-item index="/production-lines"><el-icon><Operation /></el-icon>产线档案</el-menu-item>
        <el-menu-item index="/technicians"><el-icon><User /></el-icon>技术员档案</el-menu-item>
        <el-menu-item v-permission="'ADMIN'" index="/flows/in"><el-icon><Switch /></el-icon>备品入库</el-menu-item>
        <el-menu-item v-permission="'ADMIN'" index="/flows/replace"><el-icon><RefreshRight /></el-icon>备品更换</el-menu-item>
        <el-menu-item v-permission="'ADMIN'" index="/scrap-processing"><el-icon><Delete /></el-icon>报废品处理</el-menu-item>
        <el-menu-item index="/inventory-flows"><el-icon><Tickets /></el-icon>库存流水</el-menu-item>
        <el-menu-item index="/checks"><el-icon><List /></el-icon>盘点记录</el-menu-item>
        <el-menu-item index="/analysis"><el-icon><TrendCharts /></el-icon>分析看板</el-menu-item>
        <el-menu-item index="/messages"><el-icon><Bell /></el-icon>消息中心</el-menu-item>
        <el-menu-item v-if="!userStore.isAdmin" index="/user-accounts"><el-icon><UserFilled /></el-icon>账号管理</el-menu-item>
        <el-menu-item index="/logs"><el-icon><Document /></el-icon>日志中心</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ $route.meta.title }}</span>
        <div class="header-right">
          <el-tag v-if="userStore.isAdmin" type="success">仓管员</el-tag>
          <el-tag v-else type="info">主管</el-tag>
          <span class="username">{{ userStore.user?.realName || userStore.user?.username }}</span>
          <el-button link @click="pwdVisible = true">修改密码</el-button>
          <el-button link @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
  <ChangePassword v-model:visible="pwdVisible" @done="onPwdChanged" />
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { useUserStore } from '../store/user'
import ChangePassword from './ChangePassword.vue'

const router = useRouter()
const userStore = useUserStore()
const pwdVisible = ref(false)

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
function onPwdChanged() {
  // 改密成功后提示无需特殊处理
}
</script>

<style scoped>
.layout { height: 100%; }
.aside { background-color: #001529; }
.logo { color: #fff; font-size: 18px; font-weight: 600; text-align: center; padding: 18px 0; letter-spacing: 2px; }
.aside :deep(.el-menu) { border-right: none; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.page-title { font-size: 16px; font-weight: 600; }
.header-right { display: flex; align-items: center; gap: 12px; }
.username { color: #333; }
.main { background: #f0f2f5; padding: 16px; }
@media (max-width: 767px) {
  .aside { display: none; }
  .header { padding: 0 10px; }
  .header-right { gap: 5px; font-size: 12px; }
  .username, .page-title { max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .main { padding: 0; }
}
</style>
