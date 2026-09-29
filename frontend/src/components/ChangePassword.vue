<template>
  <el-dialog :model-value="visible" :title="title" width="420px"
    :close-on-click-modal="false" :close-on-press-escape="false" :show-close="!force" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="原密码">
        <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="至少6位" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="!force" @click="cancel">取消</el-button>
      <el-button v-if="force" @click="logout">退出登录</el-button>
      <el-button type="primary" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { changePassword } from '../api'

const props = defineProps({ visible: Boolean, force: { type: Boolean, default: false } })
const emit = defineEmits(['update:visible', 'done'])
const router = useRouter()
const userStore = useUserStore()
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const title = computed(() => props.force ? '首次登录，请修改密码' : '修改密码')

watch(() => props.visible, (v) => {
  if (v) Object.assign(form, { oldPassword: '', newPassword: '', confirm: '' })
})

async function submit() {
  if (!form.oldPassword || !form.newPassword) { ElMessage.warning('请输入密码'); return }
  if (form.newPassword !== form.confirm) { ElMessage.warning('两次输入的新密码不一致'); return }
  if (form.newPassword === form.oldPassword) { ElMessage.warning('新密码不能与原密码相同'); return }
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功')
    emit('done')
    emit('update:visible', false)
  } catch (e) { /* 错误已提示 */ }
}
function cancel() {
  emit('update:visible', false)
}
async function logout() {
  await userStore.logout()
  router.push('/login')
}
</script>