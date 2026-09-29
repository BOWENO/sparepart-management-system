<template><div class="page-card">
  <el-alert title="密码采用不可逆加密，无法查询原密码。重置后用户下次登录必须修改密码，旧会话会立即失效。" type="info" show-icon :closable="false" />
  <el-table :data="rows" v-loading="loading" style="margin-top:16px">
    <el-table-column prop="username" label="账号/工号" min-width="140"/><el-table-column prop="realName" label="姓名" min-width="150"/>
    <el-table-column label="身份" width="110"><template #default="s">{{ s.row.role === 'ROLE_VIEWER' ? '主管' : '仓管员' }}</template></el-table-column>
    <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="s.row.status === 1 ? 'success' : 'danger'">{{ s.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
    <el-table-column label="操作" width="130" fixed="right"><template #default="s"><el-button type="primary" link @click="openReset(s.row)">重置密码</el-button></template></el-table-column>
  </el-table>
  <el-dialog v-model="visible" title="重置密码" width="420px"><p style="margin-bottom:14px">账号：{{ selected?.username }}（{{ selected?.realName }}）</p>
    <el-input v-model="temporaryPassword" type="password" show-password placeholder="临时密码（至少6位，含字母和数字）"/>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">确认重置</el-button></template>
  </el-dialog>
</div></template>
<script setup>
import { onMounted, ref } from 'vue'; import { ElMessage, ElMessageBox } from 'element-plus'; import { getUserAccounts, resetUserPassword } from '../api'
const rows=ref([]),loading=ref(false),visible=ref(false),saving=ref(false),selected=ref(null),temporaryPassword=ref('')
async function load(){loading.value=true;try{rows.value=await getUserAccounts()}finally{loading.value=false}}
function openReset(row){selected.value=row;temporaryPassword.value='';visible.value=true}
async function submit(){if(!/^(?=.*[A-Za-z])(?=.*\d).{6,}$/.test(temporaryPassword.value)){ElMessage.warning('临时密码至少6位，且必须同时包含字母和数字');return}
 await ElMessageBox.confirm(`确认重置账号 ${selected.value.username} 的密码？`,'安全确认',{type:'warning'});saving.value=true
 try{await resetUserPassword(selected.value.id,{temporaryPassword:temporaryPassword.value});ElMessage.success('密码已重置');visible.value=false;await load()}finally{saving.value=false}}
onMounted(load)
</script>
