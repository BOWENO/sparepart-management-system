<template>
  <div class="page-card">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="良品档案" name="parts" />
      <el-tab-pane label="在使用备品" name="inuse" />
      <el-tab-pane label="报废品档案" name="scrap" />
    </el-tabs>
    <div v-if="activeTab === 'parts'">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索编码/名称/规格" clearable style="width: 240px"
        @keyup.enter="search" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button v-permission="'ADMIN'" type="success" @click="openAdd">新增备品</el-button>
      <el-button @click="downloadBlankTemplate">下载空白模板</el-button>
      <el-button @click="downloadLedger">导出当前台账</el-button>
      <el-button v-permission="'ADMIN'" @click="importRef?.click()">导入备品</el-button>
      <el-button v-if="lastBatchNo" v-permission="'ADMIN'" type="danger" plain @click="rollbackLastImport">回滚上次导入</el-button>
      <input ref="importRef" type="file" accept=".txt,.tsv,text/plain" style="display:none" @change="handleImport" />
    </div>

    <el-table :data="list.records || []" border v-loading="loading">
      <el-table-column prop="partNo" label="编码" width="110" />
      <el-table-column label="名称" min-width="120">
        <template #default="{ row }">
          <el-input v-if="isEditing(row, 'name')" v-model="tempValue" size="small"
            @blur="saveInline(row, 'name')" @keyup.enter="saveInline(row, 'name')" />
          <span v-else class="editable" @dblclick="startEdit(row, 'name')">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="规格型号" min-width="120">
        <template #default="{ row }">
          <el-input v-if="isEditing(row, 'spec')" v-model="tempValue" size="small"
            @blur="saveInline(row, 'spec')" @keyup.enter="saveInline(row, 'spec')" />
          <span v-else class="editable" @dblclick="startEdit(row, 'spec')">{{ row.spec }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单位" width="70">
        <template #default="{ row }">
          <el-input v-if="isEditing(row, 'unit')" v-model="tempValue" size="small"
            @blur="saveInline(row, 'unit')" @keyup.enter="saveInline(row, 'unit')" />
          <span v-else class="editable" @dblclick="startEdit(row, 'unit')">{{ row.unit }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库位" width="90">
        <template #default="{ row }">
          <el-input v-if="isEditing(row, 'locationCode')" v-model="tempValue" size="small"
            @blur="saveInline(row, 'locationCode')" @keyup.enter="saveInline(row, 'locationCode')" />
          <span v-else class="editable" @dblclick="startEdit(row, 'locationCode')">{{ row.locationCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="goodStock" label="良品库存" width="90" />
      <el-table-column prop="scrapStock" label="报废库存" width="90" />
      <el-table-column label="最低水位" width="90">
        <template #default="{ row }">
          <span v-if="isEditing(row, 'minStock')"><el-input v-model="tempValue" size="small"
            @blur="saveInline(row, 'minStock')" @keyup.enter="saveInline(row, 'minStock')" /></span>
          <span v-else class="editable" @dblclick="startEdit(row, 'minStock')">{{ row.minStock }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ratedLifeCount" label="额定寿命次数" width="120" />
      <el-table-column label="状态" width="85"><template #default="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
      </template></el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button v-permission="'ADMIN'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'ADMIN'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next"
      :total="list.total || 0" :current-page="page" :page-size="size" @current-change="onPage" />
    </div>

    <div v-else-if="activeTab === 'scrap'">
      <div class="toolbar">
        <el-input v-model="scrapKeyword" placeholder="搜索报废编号/备品" clearable style="width:240px" @keyup.enter="loadScraps" />
        <el-select v-model="scrapStatus" clearable placeholder="处理状态" style="width:150px" @change="loadScraps">
          <el-option label="报废区待处理" value="PENDING" /><el-option label="部分清运" value="PARTIAL" />
          <el-option label="已清运" value="CLEARED" /><el-option label="已冲正" value="REVERSED" />
        </el-select>
        <el-button type="primary" @click="loadScraps">查询</el-button>
      </div>
      <el-table :data="scrapList.records || []" border v-loading="scrapLoading">
        <el-table-column prop="scrapNo" label="报废编号" width="210" />
        <el-table-column prop="partNo" label="备品编码" width="110" />
        <el-table-column prop="partName" label="备品名称" min-width="120" />
        <el-table-column prop="deviceName" label="来源设备" min-width="120" />
        <el-table-column prop="quantity" label="入废数量" width="90" />
        <el-table-column prop="remainingQuantity" label="待清运" width="90" />
        <el-table-column prop="actualUsageCount" label="实际次数" width="105" />
        <el-table-column prop="ratedLifeCount" label="额定次数" width="105" />
        <el-table-column label="寿命达成率" width="110"><template #default="{ row }">
          {{ row.ratedLifeCount ? Math.round((row.actualUsageCount || 0) * 100 / row.ratedLifeCount) + '%' : '-' }}
        </template></el-table-column>
        <el-table-column label="更换类型" width="120"><template #default="{ row }">{{ reasonText[row.replacementReason] || row.replacementReason }}</template></el-table-column>
        <el-table-column prop="scrapReason" label="报废原因" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="95"><template #default="{ row }"><el-tag :type="statusType[row.status] || 'info'">{{ statusText[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="disposalBatch" label="清运批次" width="180" />
        <el-table-column prop="receivedAt" label="拆回时间" width="170" />
        <el-table-column prop="clearedAt" label="清运时间" width="170" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="scrapList.total || 0"
        :current-page="scrapPage" :page-size="10" @current-change="p => { scrapPage = p; loadScraps() }" />
    </div>

    <div v-else>
      <div class="toolbar"><el-input v-model="inUseKeyword" placeholder="搜索备品/设备/领用人" clearable style="width:260px" @keyup.enter="loadInUse" /><el-button type="primary" @click="loadInUse">查询</el-button></div>
      <el-table :data="inUseList.records || []" row-key="deviceId" border v-loading="inUseLoading" @expand-change="loadDeviceDetails">
        <el-table-column type="expand" width="52"><template #default="{row}">
          <div class="detail-panel" v-loading="row.detailLoading">
            <div class="detail-title">{{ row.deviceCode }} 的备品领用与变动明细</div>
            <el-table :data="row.details || []" size="small" border empty-text="暂无在用备品明细">
              <el-table-column prop="partNo" label="备品编码" width="115" />
              <el-table-column prop="partName" label="备品名称" min-width="130" />
              <el-table-column prop="partSpec" label="规格型号" min-width="120" />
              <el-table-column prop="quantity" label="原领用数量" width="105" />
              <el-table-column prop="inUseQuantity" label="当前在用" width="95" />
              <el-table-column prop="flowNo" label="换新单号" width="190" show-overflow-tooltip />
              <el-table-column prop="issuedAt" label="领用日期" width="170" />
              <el-table-column label="领用人" width="160"><template #default="{row:detail}">{{detail.employeeNo}} {{detail.technicianName}}</template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{row:detail}"><el-tag :type="detail.status === 'IN_USE' ? 'success' : 'info'">{{ installedStatusText[detail.status] || detail.status }}</el-tag></template></el-table-column>
              <el-table-column prop="removedAt" label="拆回时间" width="170" />
            </el-table>
          </div>
        </template></el-table-column>
        <el-table-column prop="deviceCode" label="设备编号" width="180" />
        <el-table-column prop="deviceName" label="设备名称" min-width="180" />
        <el-table-column prop="partTypeCount" label="在用备品种类" width="125" />
        <el-table-column prop="totalInUseQuantity" label="在用总数量" width="115" />
        <el-table-column prop="latestIssuedAt" label="最近领用时间" width="175" />
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="inUseList.total || 0" :current-page="inUsePage" :page-size="10" @current-change="p=>{inUsePage=p;loadInUse()}" />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑备品' : '新增备品'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="备品编码"><el-input v-model="form.partNo" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="form.spec" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="库位编码"><el-input v-model="form.locationCode" /></el-form-item>
        <el-form-item label="最低水位线"><el-input-number v-model="form.minStock" :min="0" /></el-form-item>
        <el-form-item label="额定寿命次数"><el-input-number v-model="form.ratedLifeCount" :min="1" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getParts, getScrapRecords, getInstalledPartDevices, getInstalledPartDeviceHistory, savePart, deletePart, importParts, previewPartsImport, rollbackPartsImport } from '../api'

const list = ref({ records: [], total: 0 })
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const dialogVisible = ref(false)
const importRef = ref(null)
const lastBatchNo = ref('')
const activeTab = ref('parts')
const scrapList = ref({ records: [], total: 0 })
const scrapPage = ref(1)
const scrapKeyword = ref('')
const scrapStatus = ref('')
const scrapLoading = ref(false)
const inUseList = ref({ records: [], total: 0 })
const inUsePage = ref(1)
const inUseKeyword = ref('')
const inUseLoading = ref(false)
const reasonText = { FAILURE: '故障报废', LIFE_LIMIT: '寿命到期强换', OTHER: '其他', WAREHOUSE_DAMAGE: '库内损坏' }
const statusText = { PENDING: '报废区待处理', PARTIAL: '部分清运', CLEARED: '已清运', REVERSED: '已冲正' }
const statusType = { PENDING: 'danger', PARTIAL: 'warning', CLEARED: 'success', REVERSED: 'info' }
const installedStatusText = { IN_USE: '在使用', REMOVED: '已拆回', CANCELLED: '已撤销' }
const form = reactive({ id: null, partNo: '', name: '', spec: '', unit: '个', locationCode: '', minStock: 0, ratedLifeCount: null, status: 1 })

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
  const oldVal = row[field]
  let newVal = tempValue.value
  // 数字字段转换
  if (field === 'minStock') {
    newVal = Number(newVal)
    if (isNaN(newVal) || newVal < 0) {
      ElMessage.warning('请输入不小于 0 的数字')
      editing.value = { id: null, field: '' }
      return
    }
  }
  editing.value = { id: null, field: '' }
  if (String(newVal) === String(oldVal)) return
  try {
    await savePart({ ...row, [field]: newVal })
    row[field] = newVal
    ElMessage.success('已保存')
  } catch (e) {
    // 错误已由拦截器提示
  }
}

async function load() {
  loading.value = true
  try {
    list.value = await getParts({ page: page.value, size: size.value, keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function onTabChange(name) { if (name === 'scrap') loadScraps(); if (name === 'inuse') loadInUse() }
async function loadInUse() {
  inUseLoading.value = true
  try { inUseList.value = await getInstalledPartDevices({ page: inUsePage.value, size: 10, keyword: inUseKeyword.value }) }
  finally { inUseLoading.value = false }
}
async function loadDeviceDetails(row, expandedRows) {
  if (!expandedRows.some(item => item.deviceId === row.deviceId) || row.details) return
  row.detailLoading = true
  try {
    row.details = await getInstalledPartDeviceHistory(row.deviceId)
  } finally { row.detailLoading = false }
}
async function loadScraps() {
  scrapLoading.value = true
  try { scrapList.value = await getScrapRecords({ page: scrapPage.value, size: 10, keyword: scrapKeyword.value, status: scrapStatus.value }) }
  finally { scrapLoading.value = false }
}
function onPage(p) { page.value = p; load() }
function openAdd() {
  Object.assign(form, { id: null, partNo: '', name: '', spec: '', unit: '个', locationCode: '', minStock: 0, ratedLifeCount: null, status: 1 })
  dialogVisible.value = true
}
function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}
async function save() {
  if (!form.partNo || !form.name) { ElMessage.warning('编码和名称必填'); return }
  await savePart({ ...form })
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除备品【${row.name}】？删除后仅从当前清单隐藏，历史记录和操作日志仍保留。`, '提示', { type: 'warning' })
  await deletePart(row.id)
  ElMessage.success('已从清单删除，历史记录和日志已保留')
  load()
}
function downloadBlankTemplate() {
  window.open('/api/v1/parts/template', '_blank')
}
function downloadLedger() { window.open('/api/v1/parts/export', '_blank') }
async function handleImport(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  try {
    const preview = await previewPartsImport(fd)
    await ElMessageBox.confirm(`校验通过，共${preview.totalCount}条；新增${preview.newCount}条，更新${preview.updateCount}条。确认导入？`, '导入预览', { type: 'warning' })
    const confirmFd = new FormData(); confirmFd.append('file', file)
    const res = await importParts(confirmFd)
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
  await ElMessageBox.confirm('确认回滚上一次TXT导入？已发生后续库存业务的新增备品不能回滚。', '回滚导入', { type: 'warning' })
  await rollbackPartsImport(lastBatchNo.value)
  lastBatchNo.value = ''; ElMessage.success('导入批次已回滚'); await load()
}
onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.editable { cursor: pointer; display: inline-block; min-height: 20px; }
.editable:hover { color: #409eff; }
.detail-panel { padding: 12px 24px 18px; background: var(--el-fill-color-lighter); }
.detail-title { margin-bottom: 10px; font-weight: 600; color: var(--el-text-color-primary); }
</style>
