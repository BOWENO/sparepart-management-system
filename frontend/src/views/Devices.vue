<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="selectedLineId" clearable filterable placeholder="按产线筛选" style="width:220px">
        <el-option v-for="line in productionLines" :key="line.id"
          :label="`${line.lineCode} ${line.lineName || ''}`" :value="line.id" />
      </el-select>
      <el-button v-if="selectedLineId" @click="selectedLineId = null">清除筛选</el-button>
      <span class="filter-summary">共 {{ filteredDevices.length }} 台设备</span>
      <el-button v-permission="'ADMIN'" type="primary" @click="openAdd">新增设备</el-button>
      <el-button @click="downloadBlankTemplate">下载空白模板</el-button>
      <el-button @click="downloadLedger">导出当前台账</el-button>
      <el-button v-permission="'ADMIN'" @click="importRef?.click()">导入设备</el-button>
      <el-button v-if="lastBatchNo" v-permission="'ADMIN'" type="danger" plain @click="rollbackLastImport">回滚上次导入</el-button>
      <input ref="importRef" type="file" accept=".txt,.tsv,text/plain" style="display:none" @change="handleImport" />
    </div>

    <el-table :data="filteredDevices" border>
      <el-table-column prop="lineName" label="产线编号" width="120" />
      <el-table-column prop="localDeviceCode" label="产线内设备编号" width="150" />
      <el-table-column prop="deviceCode" label="唯一设备标识" width="180" />
      <el-table-column label="设备名称" min-width="140">
        <template #default="{ row }">
          <el-input v-if="isEditing(row, 'deviceName')" v-model="tempValue" size="small"
            @blur="saveInline(row, 'deviceName')" @keyup.enter="saveInline(row, 'deviceName')" />
          <span v-else class="editable" @dblclick="startEdit(row, 'deviceName')">{{ row.deviceName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch v-model="row.status" :active-value="1" :inactive-value="0"
            :disabled="!userStore.isAdmin" @change="changeStatus(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button v-permission="'ADMIN'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'ADMIN'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备' : '新增设备'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属产线">
          <el-select v-model="form.lineId" filterable placeholder="先选择产线" style="width:100%">
            <el-option v-for="line in productionLines" :key="line.id" :label="`${line.lineCode} ${line.lineName || ''}`" :value="line.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编号"><el-input v-model="form.localDeviceCode" placeholder="产线内编号，如 AT01" /></el-form-item>
        <el-form-item label="设备名称"><el-input v-model="form.deviceName" /></el-form-item>
        <el-form-item label="唯一标识"><el-input :model-value="compositeCode" disabled /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import { getDeviceLedger, saveDevice, deleteDevice, importDevices, previewDevicesImport, rollbackDevicesImport, getProductionLines } from '../api'

const userStore = useUserStore()
const devices = ref([])
const productionLines = ref([])
const selectedLineId = ref(null)
const dialogVisible = ref(false)
const importRef = ref(null)
const lastBatchNo = ref('')
const form = reactive({ id: null, deviceCode: '', localDeviceCode: '', deviceName: '', lineId: null, lineName: '' })
const compositeCode = computed(() => {
  const line = productionLines.value.find(item => item.id === form.lineId)
  return line && form.localDeviceCode ? `${line.lineCode}-${form.localDeviceCode.trim().toUpperCase()}` : ''
})
const filteredDevices = computed(() => selectedLineId.value
  ? devices.value.filter(device => device.lineId === selectedLineId.value)
  : devices.value)

// 双击内联编辑状态
const editing = ref({ id: null, field: '' })
const tempValue = ref('')

function startEdit(row, field) {
  editing.value = { id: row.id, field }
  tempValue.value = row[field]
}
function isEditing(row, field) {
  return editing.value.id === row.id && editing.value.field === field
}
async function saveInline(row, field) {
  const oldVal = row[field] || ''
  const newVal = tempValue.value || ''
  editing.value = { id: null, field: '' }
  if (String(newVal) === String(oldVal)) return
  try {
    await saveDevice({ ...row, [field]: newVal })
    row[field] = newVal
    ElMessage.success('已保存')
  } catch (e) {
    // 错误已由拦截器提示
  }
}

function downloadBlankTemplate() {
  window.open('/api/v1/devices/template', '_blank')
}
function downloadLedger() { window.open('/api/v1/devices/export', '_blank') }
async function changeStatus(row) {
  const newStatus = row.status
  try {
    await saveDevice({ ...row })
    ElMessage.success(newStatus === 1 ? '设备已启用' : '设备已停用')
  } catch (e) {
    row.status = newStatus === 1 ? 0 : 1
  }
}
async function handleImport(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  try {
    const preview = await previewDevicesImport(fd)
    await ElMessageBox.confirm(`校验通过，共${preview.totalCount}条；新增${preview.newCount}条，更新${preview.updateCount}条。确认导入？`, '导入预览', { type: 'warning' })
    const confirmFd = new FormData(); confirmFd.append('file', file)
    const res = await importDevices(confirmFd)
    lastBatchNo.value = res.batchNo
    ElMessage.success(`导入完成：新增 ${res.newCount} 条，更新 ${res.updateCount} 条`)
  } catch (err) {
    // 服务端的具体中文错误由请求拦截器统一显示
  } finally {
    e.target.value = ''
    await load()
  }
}
async function rollbackLastImport() {
  await ElMessageBox.confirm('确认回滚上一次设备TXT导入？', '回滚导入', { type: 'warning' })
  await rollbackDevicesImport(lastBatchNo.value)
  lastBatchNo.value = ''; ElMessage.success('导入批次已回滚'); await load()
}

async function load() {
  const [deviceRows, lineRows] = await Promise.all([getDeviceLedger(), getProductionLines(false)])
  devices.value = deviceRows
  productionLines.value = lineRows
}
function openAdd() {
  Object.assign(form, { id: null, deviceCode: '', localDeviceCode: '', deviceName: '', lineId: null, lineName: '' })
  dialogVisible.value = true
}
function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}
async function save() {
  if (!form.lineId || !form.localDeviceCode || !form.deviceName) { ElMessage.warning('产线、设备编号和名称必填'); return }
  await saveDevice({ ...form })
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除设备【${row.deviceName}】？删除后仅从当前清单隐藏，历史记录和操作日志仍保留。`, '提示', { type: 'warning' })
  await deleteDevice(row.id)
  ElMessage.success('已从清单删除，历史记录和日志已保留')
  load()
}
onMounted(load)
</script>

<style scoped>
.editable { cursor: pointer; display: inline-block; min-height: 20px; }
.editable:hover { color: #409eff; }
.filter-summary { color: var(--el-text-color-secondary); white-space: nowrap; margin-right: auto; }
</style>
