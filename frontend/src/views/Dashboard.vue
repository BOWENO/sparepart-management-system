<template>
  <div>
    <div class="action-row">
      <el-button v-permission="'ADMIN'" type="primary" size="large" @click="replaceVisible = true">
        <el-icon><RefreshRight /></el-icon> 办理备品更换
      </el-button>
      <el-button v-permission="'ADMIN'" type="success" size="large" @click="$router.push('/flows/in')">
        <el-icon><Switch /></el-icon> 快速入库
      </el-button>
    </div>

    <el-row :gutter="16" class="metric-row">
      <el-col :xs="12" :sm="6">
        <el-card class="metric"><el-icon class="metric-icon blue"><RefreshRight /></el-icon><div class="metric-num">{{ summary.todayUsage ?? 0 }}</div><div class="metric-label">今日领用总数</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="metric"><el-icon class="metric-icon green"><Box /></el-icon><div class="metric-num">{{ summary.goodStock ?? 0 }}</div><div class="metric-label">良品总库存余量</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="metric"><el-icon class="metric-icon amber"><Warning /></el-icon><div class="metric-num warn">{{ summary.lowStockCount ?? 0 }}</div><div class="metric-label">低于水位种类数</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="metric"><el-icon class="metric-icon red"><Delete /></el-icon><div class="metric-num">{{ summary.scrapStock ?? 0 }}</div><div class="metric-label">报废区待处理数量</div></el-card>
      </el-col>
    </el-row>

    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>消息速览（最新未读）</span>
          <el-button link type="primary" @click="$router.push('/messages')">更多</el-button>
        </div>
      </template>
      <el-table :data="summary.latestUnread || []" size="small">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
    </el-card>

    <ReplaceBatch v-model:visible="replaceVisible" @success="loadSummary" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '../api'
import ReplaceBatch from '../components/ReplaceBatch.vue'

const summary = ref({})
const replaceVisible = ref(false)

async function loadSummary() {
  summary.value = await getDashboard()
}

onMounted(loadSummary)
</script>

<style scoped>
.action-row { display: flex; gap: 16px; margin-bottom: 16px; }
.action-row .el-button { padding: 18px 32px; font-size: 16px; }
.metric-row { margin-bottom: 16px; }
.metric { text-align: left; position:relative; overflow:hidden; min-height:132px; }
.metric::after { content:''; position:absolute; width:110px; height:110px; right:-42px; top:-44px; border-radius:50%; background:linear-gradient(145deg,rgba(64,158,255,.13),rgba(255,255,255,0)); }
.metric-icon { float:right; width:44px; height:44px; border-radius:13px; color:#fff; font-size:22px; box-shadow:0 8px 15px rgba(37,83,128,.22), inset 0 1px 1px rgba(255,255,255,.55); }
.metric-icon.blue { background:linear-gradient(145deg,#72c2ff,#287fc9); }.metric-icon.green { background:linear-gradient(145deg,#77dfb0,#249566); }
.metric-icon.amber { background:linear-gradient(145deg,#ffd879,#d9951d); }.metric-icon.red { background:linear-gradient(145deg,#ff9b9b,#d64d5c); }
.metric-num { font-size: 28px; font-weight: 700; color: #409eff; }
.metric-num.warn { color: #f56c6c; }
.metric-label { color: #909399; margin-top: 6px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
