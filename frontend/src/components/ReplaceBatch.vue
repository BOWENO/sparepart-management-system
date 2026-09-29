<template>
  <el-dialog v-model="visible" title="办理备品更换" width="80%" top="5vh" destroy-on-close>
    <div class="replace-toolbar">
      <el-checkbox v-model="isExceptionMode" @change="onExceptionChange">
        特殊出库（无旧件）
      </el-checkbox>
      <el-tag v-if="isExceptionMode" type="danger">退还旧品列已禁用，需填写遗失/损毁原因</el-tag>
      <el-button type="primary" size="small" :disabled="tableData.length >= 5 || submitting" @click="addRow">+ 添加行（最多5行）</el-button>
    </div>

    <el-alert title="同一行可批量更换：实际次数填写每件旧件的次数，不是整批总次数。次数不同请按相同次数分行登记。" type="info" :closable="false" />
    <el-table :data="tableData" border class="replace-table" :inert="submitting || undefined">
      <el-table-column label="新备品" min-width="220">
        <template #default="{ row }">
          <el-select v-model="row.newPartId" filterable remote clearable
            :remote-method="searchParts" placeholder="搜索新备品" style="width: 100%">
            <el-option v-for="p in partOptions" :key="p.id"
              :label="`${p.partNo} ${p.name}`" :value="p.id" />
            <div class="quick-add" @click="openQuickPart(row)">
              <el-link type="primary">+ 快速新增备品</el-link>
            </div>
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="数量" width="140">
        <template #default="{ row }">
          <el-input-number v-model="row.quantity" :min="1" :max="9999" :precision="0" @change="(value, old) => { if (row.oldQuantity === old) row.oldQuantity = value }" />
        </template>
      </el-table-column>

      <el-table-column label="产线" min-width="170">
        <template #default="{ row }">
          <el-select v-model="row.lineId" filterable clearable placeholder="先选择产线" style="width:100%" @change="row.deviceId = null">
            <el-option v-for="line in productionLines" :key="line.id"
              :label="`${line.lineCode} ${line.lineName || ''}`" :value="line.id" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="关联设备" min-width="200">
        <template #default="{ row }">
          <el-select v-model="row.deviceId" filterable clearable :disabled="!row.lineId" placeholder="选择产线内设备" style="width: 100%">
            <el-option v-for="d in devicesForLine(row.lineId)" :key="d.id"
              :label="`${d.deviceCode} ${d.deviceName}`" :value="d.id" />
            <div class="quick-add" @click="openQuickDevice(row)">
              <el-link type="primary">+ 快速新增设备</el-link>
            </div>
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="领用人" min-width="180">
        <template #default="{ row }">
          <el-select v-model="row.technicianId" filterable clearable placeholder="选择技术员" style="width:100%">
            <el-option v-for="t in technicians" :key="t.id" :label="`${t.employeeNo} ${t.name}`" :value="t.id" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="退回旧品" min-width="200">
        <template #default="{ row }">
          <el-select v-model="row.oldPartId" filterable clearable :disabled="isExceptionMode"
            placeholder="选择旧品" style="width: 100%">
            <el-option v-for="p in partOptions" :key="p.id"
              :label="`${p.partNo} ${p.name}`" :value="p.id" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="旧品数量" width="140">
        <template #default="{ row }">
          <el-input-number v-model="row.oldQuantity" :min="1" :disabled="isExceptionMode" />
        </template>
      </el-table-column>

      <el-table-column label="差异原因" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.differenceReason" placeholder="型号或数量不同时必填" :disabled="isExceptionMode" />
        </template>
      </el-table-column>

      <el-table-column label="更换原因" min-width="170">
        <template #default="{ row }">
          <el-select v-model="row.replacementReason" :disabled="isExceptionMode" placeholder="必选">
            <el-option label="故障报废更换" value="FAILURE" />
            <el-option label="寿命到期强制更换" value="LIFE_LIMIT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="每件实际使用次数" min-width="190">
        <template #default="{ row }">
          <el-input-number v-model="row.actualUsageCount" :min="0" :precision="0" :disabled="isExceptionMode" />
        </template>
      </el-table-column>

      <el-table-column label="故障/报废原因" min-width="180">
        <template #default="{ row }"><el-input v-model="row.scrapReason" :disabled="isExceptionMode"
          placeholder="故障报废或其他原因时必填" /></template>
      </el-table-column>

      <el-table-column label="备注（遗失/损毁原因）" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.exceptionRemark" :class="{ 'red-required': isExceptionMode }"
            :placeholder="isExceptionMode ? '必填：注明遗失/损毁原因' : '选填'" style="width: 100%" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="80" align="center">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">提交更换</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="quickPartVisible" title="快速新增备品" width="420px" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="备品编码"><el-input v-model="quickPart.partNo" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="quickPart.name" /></el-form-item>
      <el-form-item label="规格型号"><el-input v-model="quickPart.spec" /></el-form-item>
      <el-form-item label="库位编码"><el-input v-model="quickPart.locationCode" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="quickPartVisible = false">取消</el-button>
      <el-button type="primary" @click="saveQuickPart">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="quickDeviceVisible" title="快速新增设备" width="420px" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="所属产线"><el-select v-model="quickDevice.lineId" filterable style="width:100%"><el-option v-for="line in productionLines" :key="line.id" :label="`${line.lineCode} ${line.lineName || ''}`" :value="line.id" /></el-select></el-form-item>
      <el-form-item label="设备编号"><el-input v-model="quickDevice.localDeviceCode" placeholder="产线内编号" /></el-form-item>
      <el-form-item label="设备名称"><el-input v-model="quickDevice.deviceName" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="quickDeviceVisible = false">取消</el-button>
      <el-button type="primary" @click="saveQuickDevice">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { replaceBatch, getParts, getDevices, getTechnicians, savePart, saveDevice, getProductionLines } from '../api'

const visible = defineModel('visible', { type: Boolean, default: false })
const emit = defineEmits(['success'])

const tableData = ref([])
const partOptions = ref([])
const devices = ref([])
const productionLines = ref([])
const technicians = ref([])
const isExceptionMode = ref(false)
const submitting = ref(false)
let pendingRequestId = null
let pendingPayload = null

const quickPartVisible = ref(false)
const quickPart = reactive({ partNo: '', name: '', spec: '', locationCode: '' })
const currentRow = ref(null)

const quickDeviceVisible = ref(false)
const quickDevice = reactive({ localDeviceCode: '', deviceName: '', lineId: null })

watch(visible, async (val) => {
  if (val) {
    tableData.value = []
    isExceptionMode.value = false
    await loadDevices()
    productionLines.value = await getProductionLines(true)
    technicians.value = await getTechnicians(true)
    await searchParts('')
    addRow()
  }
}, { immediate: true })

function addRow() {
  if (tableData.value.length >= 5) return
  tableData.value.push({
    newPartId: null,
    oldPartId: null,
    deviceId: null,
    lineId: null,
    technicianId: null,
    quantity: 1,
    oldQuantity: 1,
    differenceReason: '',
    isException: isExceptionMode.value,
    exceptionRemark: '',
    replacementReason: null,
    actualUsageCount: null,
    counterStart: null,
    counterEnd: null,
    scrapReason: ''
  })
}

function removeRow(index) {
  tableData.value.splice(index, 1)
}

function onExceptionChange(val) {
  tableData.value.forEach((row) => {
    row.isException = val
    if (val) {
      row.oldPartId = null
      row.oldQuantity = null
    } else if (!row.oldQuantity) row.oldQuantity = row.quantity
  })
}

async function searchParts(kw) {
  const res = await getParts({ page: 1, size: 20, keyword: kw || '' })
  partOptions.value = res.records || []
}

async function loadDevices() {
  devices.value = await getDevices()
}

function openQuickPart(row) {
  currentRow.value = row
  Object.assign(quickPart, { partNo: '', name: '', spec: '', locationCode: '' })
  quickPartVisible.value = true
}

function openQuickDevice(row) {
  currentRow.value = row
  Object.assign(quickDevice, { localDeviceCode: '', deviceName: '', lineId: row.lineId || null })
  quickDeviceVisible.value = true
}

async function saveQuickPart() {
  if (!quickPart.partNo || !quickPart.name) {
    ElMessage.warning('备品编码和名称必填')
    return
  }
  const part = await savePart({ ...quickPart, status: 1, goodStock: 0, scrapStock: 0, minStock: 0 })
  await searchParts(quickPart.name)
  if (currentRow.value) currentRow.value.newPartId = part.id
  quickPartVisible.value = false
  ElMessage.success('备品已新增')
}

async function saveQuickDevice() {
  if (!quickDevice.lineId || !quickDevice.localDeviceCode || !quickDevice.deviceName) {
    ElMessage.warning('产线、设备编号和名称必填')
    return
  }
  const device = await saveDevice({ ...quickDevice, status: 1 })
  await loadDevices()
  if (currentRow.value) {
    currentRow.value.lineId = device.lineId
    currentRow.value.deviceId = device.id
  }
  quickDeviceVisible.value = false
  ElMessage.success('设备已新增')
}

async function submit() {
  if (submitting.value) return
  if (tableData.value.length === 0) {
    ElMessage.warning('请至少添加一行')
    return
  }
  for (let i = 0; i < tableData.value.length; i++) {
    const row = tableData.value[i]
    if (!row.newPartId || !row.lineId || !row.deviceId || !row.technicianId || !row.quantity) {
      ElMessage.warning(`第${i + 1}行：新备品、产线、设备、领用人和数量不能为空`)
      return
    }
    if (isExceptionMode.value && !row.exceptionRemark) {
      ElMessage.warning(`第${i + 1}行：特殊出库需填写原因`)
      return
    }
    if (!isExceptionMode.value && !row.oldPartId) {
      ElMessage.warning(`第${i + 1}行：正常换新需选择退回旧品`)
      return
    }
    if (!isExceptionMode.value && !row.replacementReason) {
      ElMessage.warning(`第${i + 1}行：请选择故障报废或寿命到期强制更换`)
      return
    }
    if (!isExceptionMode.value && row.actualUsageCount == null) {
      ElMessage.warning(`第${i + 1}行：请填写旧件实际使用次数`)
      return
    }
    if (!isExceptionMode.value && ['FAILURE', 'OTHER'].includes(row.replacementReason) && !row.scrapReason) {
      ElMessage.warning(`第${i + 1}行：请填写故障或报废原因`)
      return
    }
    const oldQty = row.oldQuantity || row.quantity
    if (!isExceptionMode.value && (row.oldPartId !== row.newPartId || oldQty !== row.quantity) && !row.differenceReason) {
      ElMessage.warning(`第${i + 1}行：新旧备品或数量不同时必须填写差异原因`)
      return
    }
  }
  try {
    submitting.value = true
    const payload = JSON.stringify(tableData.value)
    if (!pendingRequestId || pendingPayload !== payload) {
      pendingRequestId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
      pendingPayload = payload
    }
    await replaceBatch({ requestId: pendingRequestId, items: JSON.parse(payload) })
    pendingRequestId = null
    pendingPayload = null
    ElMessage.success('换新成功')
    visible.value = false
    emit('success')
  } catch (e) {
    // 行级错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function devicesForLine(lineId) {
  return lineId ? devices.value.filter(device => device.lineId === lineId) : []
}
</script>

<style scoped>
.replace-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.replace-table { margin-bottom: 8px; }
.quick-add { padding: 4px 12px; text-align: center; }
.red-required :deep(.el-input__inner) { color: #f56c6c; }
.red-required :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #f56c6c inset; }
</style>
