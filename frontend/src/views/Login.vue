<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">备品备件管理系统</h2>
      <el-form :model="form" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
        <p class="forgot-tip">忘记密码请联系主管重置，系统无法查询原密码。</p>
      </el-form>
    </el-card>
    <ChangePassword :visible="forcePwdVisible" force @done="onPwdDone" @update:visible="forcePwdVisible = $event" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import ChangePassword from '../components/ChangePassword.vue'

const router = useRouter()
const userStore = useUserStore()
const form = ref({ username: '', password: '' })
const loading = ref(false)
const forcePwdVisible = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await userStore.login(form.value.username, form.value.password)
    if (data && data.needChangePassword) {
      // 首次登录需强制修改密码
      forcePwdVisible.value = true
    } else {
      router.push('/dashboard')
    }
  } catch (e) {
    // 错误已提示
  } finally {
    loading.value = false
  }
}
function onPwdDone() {
  router.push('/dashboard')
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3b73 0%, #2f6f9f 100%);
}
.login-card {
  width: 380px;
  padding: 20px 10px;
  border-radius: 10px;
}
.login-title {
  text-align: center;
  margin-bottom: 24px;
  color: #1f3b73;
  letter-spacing: 2px;
}
.forgot-tip { margin-top: 16px; text-align: center; color: #7a8796; font-size: 13px; }
</style>
