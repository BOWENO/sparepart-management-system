<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.operator" placeholder="操作人" clearable style="width: 160px" />
      <el-input v-model="query.module" placeholder="模块" clearable style="width: 140px" />
      <el-date-picker v-model="query.range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="选择时间范围" @change="onRangeChange" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="switchMode">按月份查询（归档）</el-button>
      <el-button v-if="archiveMonth" @click="exportArchive">导出归档月份</el-button>
    </div>

    <el-dialog v-model="archiveMode" title="冷数据·按月查询" width="420px">
      <el-form label-width="100px">
        <el-form-item label="归档月份">
          <el-date-picker v-model="archiveMonth" type="month" value-format="YYYY_MM" placeholder="选择月份" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="archiveMode = false">取消</el-button>
        <el-button type="primary" @click="queryArchive">查询</el-button>
      </template>
    </el-dialog>

    <el-table :data="list.records || []" border v-loading="loading">
      <el-table-column prop="operator" label="操作人" width="100" />
      <el-table-column prop="module" label="模块" width="100" />
      <el-table-column prop="action" label="操作" width="120" />
      <el-table-column prop="afterJson" label="变更数据" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>

    <el-pagination v-if="!showingArchive" class="pager" background layout="total, sizes, prev, pager, next, jumper"
      :total="Number(list.total) || 0" :current-page="page" :page-size="size"
      :page-sizes="[10, 20, 50, 100]" @current-change="onPage" @size-change="onSizeChange" />
    <div v-else class="archive-total">归档日志共 {{ list.total || 0 }} 条</div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { getLogs, getArchiveLogs } from '../api'

const list = ref({ records: [], total: 0 })
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const query = reactive({ operator: '', module: '', range: null })
const archiveMode = ref(false)
const archiveMonth = ref('')
const archiveList = ref([])
const showingArchive = ref(false)

function onRangeChange(val) {
  if (val && Array.isArray(val) && val.length === 2) {
    const start = new Date(val[0])
    const end = new Date(val[1])
    const months = (end.getFullYear() - start.getFullYear()) * 12 + (end.getMonth() - start.getMonth())
    if (months > 6) {
      ElMessageBox.alert('查询跨度超过6个月，请切换到"按月份查询"模式。', '提示')
      query.range = null
    }
  }
}

async function search() {
  page.value = 1
  showingArchive.value = false
  await loadPage()
}

async function loadPage() {
  loading.value = true
  try {
    list.value = await getLogs({
      page: page.value, size: size.value,
      operator: query.operator || undefined,
      module: query.module || undefined,
      start: query.range?.[0] || undefined,
      end: query.range?.[1] || undefined
    })
  } finally {
    loading.value = false
  }
}

function onPage(p) { page.value = p; loadPage() }
function onSizeChange(newSize) { size.value = newSize; page.value = 1; loadPage() }

function switchMode() {
  archiveMode.value = true
}

async function queryArchive() {
  if (!archiveMonth.value) { ElMessage.warning('请选择月份'); return }
  archiveMode.value = false
  const data = await getArchiveLogs({ month: archiveMonth.value })
  list.value = { records: data, total: data.length }
  showingArchive.value = true
}
function exportArchive() {
  window.open(`/api/v1/logs/archive/export?month=${encodeURIComponent(archiveMonth.value)}`, '_blank')
}

onMounted(search)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.archive-total { margin-top: 16px; text-align: right; color: var(--el-text-color-secondary); }
</style>
