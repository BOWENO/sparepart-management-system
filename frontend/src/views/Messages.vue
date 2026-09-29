<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="msgType" @change="load">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="1">库存预警</el-radio-button>
        <el-radio-button :value="2">盘点差异</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="list.records || []" border>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="row.msgType === 1 ? 'warning' : 'info'">{{ row.msgType === 1 ? '库存预警' : '盘点差异' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" />
      <el-table-column prop="content" label="内容" min-width="200" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.isRead" type="info">已读</el-tag>
          <el-tag v-else type="danger" effect="plain">未读</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="!row.isRead" link type="primary" @click="read(row)">标记已读</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, prev, pager, next"
      :total="list.total || 0" :current-page="page" :page-size="size" @current-change="onPage" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMessages, markRead } from '../api'

const list = ref({ records: [], total: 0 })
const page = ref(1)
const size = ref(10)
const msgType = ref(null)

async function load() {
  list.value = await getMessages({ page: page.value, size: size.value, msgType: msgType.value || undefined })
}
function onPage(p) { page.value = p; load() }
async function read(row) {
  await markRead(row.id)
  row.isRead = 1
}
onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>