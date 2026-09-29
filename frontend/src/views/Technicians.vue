<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button v-permission="'ADMIN'" type="primary" @click="open()">新增技术员</el-button>
      <el-button @click="downloadLedger">导出当前台账</el-button>
      <el-button v-permission="'ADMIN'" @click="importRef?.click()">导入技术员</el-button>
      <el-button v-if="lastBatchNo" v-permission="'ADMIN'" type="danger" plain @click="rollbackImport">回滚上次导入</el-button>
      <input ref="importRef" type="file" accept=".txt,.tsv,text/plain" style="display:none" @change="handleImport" />
    </div>
    <el-table :data="rows" border>
      <el-table-column prop="employeeNo" label="工号" width="180" />
      <el-table-column prop="name" label="姓名" min-width="180" />
      <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'停用'}}</el-tag></template></el-table-column>
      <el-table-column v-if="user.isAdmin" label="操作" width="160"><template #default="{row}">
        <el-button link type="primary" @click="open(row)">编辑</el-button>
        <el-button v-if="row.status===1" link type="danger" @click="disable(row)">停用</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id?'编辑技术员':'新增技术员'" width="420px">
      <el-form label-width="80px"><el-form-item label="工号"><el-input v-model="form.employeeNo" /></el-form-item><el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item><el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item></el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import {ref,reactive,onMounted} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {getTechnicians,saveTechnician,disableTechnician,previewTechniciansImport,importTechnicians,rollbackTechniciansImport} from '../api'
import {useUserStore} from '../store/user'
const user=useUserStore(),rows=ref([]),visible=ref(false),form=reactive({id:null,employeeNo:'',name:'',status:1}),importRef=ref(null),lastBatchNo=ref('')
async function load(){rows.value=await getTechnicians(false)}
function open(row){Object.assign(form,row||{id:null,employeeNo:'',name:'',status:1});visible.value=true}
async function save(){if(!form.employeeNo||!form.name){ElMessage.warning('工号和姓名不能为空');return}await saveTechnician({...form});visible.value=false;ElMessage.success('保存成功');load()}
async function disable(row){await ElMessageBox.confirm(`确认停用技术员【${row.name}】？`,'提示',{type:'warning'});await disableTechnician(row.id);ElMessage.success('已停用');load()}
function downloadLedger(){window.open('/api/v1/technicians/export','_blank')}
async function handleImport(e){const file=e.target.files[0];if(!file)return;const previewData=new FormData();previewData.append('file',file);try{const p=await previewTechniciansImport(previewData);await ElMessageBox.confirm(`校验通过，共${p.totalCount}条；新增${p.newCount}条，更新${p.updateCount}条。未出现在文件中的技术员不会删除，空白字段不会覆盖原值。确认导入？`,'导入预览',{type:'warning'});const data=new FormData();data.append('file',file);const r=await importTechnicians(data);lastBatchNo.value=r.batchNo;ElMessage.success(`导入完成：新增 ${r.newCount} 条，更新 ${r.updateCount} 条`);await load()}finally{e.target.value=''}}
async function rollbackImport(){await ElMessageBox.confirm('确认回滚上一次技术员导入？导入后又被人工修改的数据不会被覆盖。','回滚导入',{type:'warning'});await rollbackTechniciansImport(lastBatchNo.value);lastBatchNo.value='';ElMessage.success('导入批次已回滚');await load()}
onMounted(load)
</script>
