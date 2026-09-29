<template>
  <div class="page-card">
    <div v-if="userStore.isAdmin" class="toolbar">
      <el-select v-model="checkPartId" filterable remote clearable :remote-method="searchParts"
        placeholder="选择要盘点的备品" style="width: 300px">
        <el-option v-for="p in partOptions" :key="p.id" :label="`${p.partNo} ${p.name}（账面${p.goodStock}）`" :value="p.id" />
      </el-select>
      <el-input-number v-model="actualQty" :min="0" placeholder="实盘数" />
      <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitCheck">提交盘点</el-button>
    </div>

    <el-divider>盘点差异记录</el-divider>
    <el-table :data="checks.records || []" border>
      <el-table-column prop="partId" label="备品ID" width="90" />
      <el-table-column prop="bookQty" label="账面数" width="90" />
      <el-table-column prop="actualQty" label="实盘数" width="90" />
      <el-table-column label="差异" width="120">
        <template #default="{ row }">
          <span :style="{ color: row.diffQty >= 0 ? '#67c23a' : '#f56c6c' }">
            {{ row.diffQty >= 0 ? '+' : '' }}{{ row.diffQty }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="100" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="checks.total || 0"
      layout="prev, pager, next, total" @current-change="loadChecks" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getParts, doCheck, getChecks } from '../api'
import { useUserStore } from '../store/user'

const userStore = useUserStore()
const partOptions = ref([])
const checkPartId = ref(null)
const actualQty = ref(0)
const checks = ref({ records: [], total: 0 })
const page = ref(1)
const submitting = ref(false)

async function searchParts(kw) {
  const res = await getParts({ page: 1, size: 20, keyword: kw || '' })
  partOptions.value = res.records || []
}
async function loadChecks() {
  checks.value = await getChecks({ page: page.value, size: 20 })
}
async function submitCheck() {
  if (submitting.value) return
  if (!checkPartId.value) { ElMessage.warning('请选择备品'); return }
  try {
    submitting.value = true
    await doCheck({ partId: checkPartId.value, actualQty: actualQty.value })
    ElMessage.success('盘点完成，已自动调账并通知主管')
    page.value = 1
    await loadChecks()
  } finally {
    submitting.value = false
  }
}
onMounted(() => { if (userStore.isAdmin) searchParts(''); loadChecks() })
</script>
