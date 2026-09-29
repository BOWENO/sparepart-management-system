<template>
  <div class="page-card">
    <div class="toolbar">
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" placeholder="选择日期范围" />
      <el-button type="primary" @click="loadAll">刷新图表</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12"><el-card class="analysis-card"><template #header>备品领用总量排行</template><div ref="unitRef" class="chart"></div></el-card></el-col>
      <el-col :xs="24" :md="12"><el-card class="analysis-card"><template #header>消耗趋势</template><div ref="trendRef" class="chart"></div></el-card></el-col>
      <el-col :xs="24" :md="12"><el-card class="analysis-card"><template #header>备品类型偏好</template><div ref="pieRef" class="chart"></div></el-card></el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <template #header><div class="section-head"><span>设备月度更换频率</span>
        <el-select v-model="selectedDevice" placeholder="选择设备" style="width:220px" @change="loadDeviceTrend">
          <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceCode} ${d.deviceName}`" :value="d.id" />
        </el-select></div></template>
      <el-alert v-if="frequencyWarning" type="error" :closable="false" title="本月更换次数较上月增长超过50%" />
      <div ref="deviceTrendRef" class="chart"></div>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>设备备件寿命监控</template>
      <el-table :data="lifeList" size="small" border>
        <el-table-column prop="partNo" label="编码" width="120" />
        <el-table-column prop="partName" label="备件" min-width="130" />
        <el-table-column prop="deviceName" label="使用设备" min-width="130" />
        <el-table-column prop="ratedLifeCount" label="额定次数" width="110" />
        <el-table-column prop="avgActualUsage" label="平均实际次数" width="130" />
        <el-table-column label="寿命达成率" width="150"><template #default="{ row }">
          <el-tag v-if="row.lifeAchievementRate != null" :type="Number(row.lifeAchievementRate) < 80 ? 'danger' : 'success'">{{ row.lifeAchievementRate }}%</el-tag>
          <el-tag v-else type="info">未设置额定寿命</el-tag>
        </template></el-table-column>
        <el-table-column prop="failureCount" label="故障报废" width="100" />
        <el-table-column prop="forcedCount" label="到期强换" width="100" />
        <el-table-column prop="replacementCount" label="更换总数" width="100" />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>备件水位预测（近90天换新消耗预测未来30天，含20%安全系数）</template>
      <el-table :data="forecastList" size="small" border>
        <el-table-column prop="partNo" label="编码" width="120" />
        <el-table-column prop="partName" label="备件" min-width="130" />
        <el-table-column prop="used90Days" label="近90天换新" width="110" />
        <el-table-column prop="goodStock" label="当前库存" width="100" />
        <el-table-column prop="recommendedMinStock" label="建议最低水位" width="130" />
        <el-table-column prop="estimatedAvailableDays" label="预计可用天数" width="130" />
        <el-table-column label="建议" width="100"><template #default="{ row }">
          <el-tag :type="row.needsAdjustment ? 'danger' : 'success'">{{ row.needsAdjustment ? '建议调高' : '合理' }}</el-tag>
        </template></el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="drillRows.length" style="margin-top:16px">
      <template #header><div class="section-head"><span>下钻流水明细</span><el-button size="small" @click="exportDrill">导出明细</el-button></div></template>
      <el-table :data="drillRows" border size="small">
        <el-table-column prop="flowNo" label="流水号" width="210" />
        <el-table-column prop="flowTypeName" label="业务类型" width="120" />
        <FlowContextColumns />
        <el-table-column prop="businessDate" label="业务日期" width="110" />
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>僵尸备品清单（默认90天未领用）</template>
      <el-table :data="zombieList" size="small" border>
        <el-table-column prop="partNo" label="编码" width="140" />
        <el-table-column prop="partName" label="名称" min-width="120" />
        <el-table-column label="最近领用" prop="lastUsed" width="180" />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>
        <div class="section-head">
          <span>报废品使用统计</span>
          <el-button type="primary" size="small" @click="exportScrap">一键导出</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12"><div ref="scrapRef" class="chart"></div></el-col>
        <el-col :span="12">
          <el-table :data="scrapList" size="small" border height="340">
            <el-table-column prop="partNo" label="编码" width="110" />
            <el-table-column prop="partName" label="名称" min-width="110" />
            <el-table-column label="使用次数" width="95">
              <template #default="{ row }"><span style="color:#1f4fa8;font-weight:700">{{ row.useCount }}</span></template>
            </el-table-column>
            <el-table-column prop="totalQty" label="累计报废" width="90" />
            <el-table-column prop="devices" label="曾使用设备" min-width="130" show-overflow-tooltip />
          </el-table>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import FlowContextColumns from '../components/FlowContextColumns.vue'
import { getUnitTotal, getConsumeTrend, getPartType, getZombieParts, getScrapStats, getLifeStats, getStockForecast, getDeviceTrend, getDevices, getFlows } from '../api'

const dateRange = ref([])
const unitRef = ref()
const trendRef = ref()
const pieRef = ref()
const scrapRef = ref()
const deviceTrendRef = ref()
const selectedDevice = ref(null)
const devices = ref([])
const frequencyWarning = ref(false)
const drillRows = ref([])
const drillFilter = ref({})
const zombieList = ref([])
const scrapList = ref([])
const lifeList = ref([])
const forecastList = ref([])
const charts = []

function endOfMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
function startOfMonths(n) {
  const d = new Date()
  d.setMonth(d.getMonth() - n)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

async function loadAll() {
  const start = dateRange.value?.[0] || startOfMonths(6)
  const end = dateRange.value?.[1] || endOfMonth()
  const params = { startDate: start, endDate: end }

  const [unit, trend, pie, zombie, scrap, life, forecast, deviceRows] = await Promise.all([
    getUnitTotal(), getConsumeTrend(params), getPartType(), getZombieParts({ days: 45 }), getScrapStats(), getLifeStats(), getStockForecast(), getDevices()
  ])
  devices.value = deviceRows
  zombieList.value = zombie
  scrapList.value = scrap
  lifeList.value = life
  forecastList.value = forecast

  renderBar(unitRef.value, unit.map(x => x.partName || '备品' + x.partId), unit.map(x => Number(x.totalQty)), '领用数量',
    unit.map(x => ({ partId: x.partId })))
  renderLine(trendRef.value, trend.map(x => x.date), trend.map(x => Number(x.totalQty)))
  renderPie(pieRef.value, pie.map(x => ({ name: x.partName || '备品' + x.partId, value: Number(x.totalQty) })))
  renderBar(scrapRef.value, scrap.map(x => x.partNo + ' ' + (x.partName || '')), scrap.map(x => Number(x.useCount)), '使用次数')
}

async function loadDeviceTrend() {
  if (!selectedDevice.value) return
  const rows = await getDeviceTrend({ deviceId: selectedDevice.value, months: 12 })
  const values = rows.map(x => Number(x.totalQty))
  const current = values.at(-1) || 0
  const previous = values.at(-2) || 0
  frequencyWarning.value = previous === 0 ? current > 0 : current > previous * 1.5
  renderLine(deviceTrendRef.value, rows.map(x => x.month), values)
}

function exportScrap() {
  window.open('/api/v1/analysis/scrap-stats/export', '_blank')
}

function renderBar(el, cats, vals, name, drillMeta = []) {
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: '{b}<br/>{a}: <b>{c}</b>'
    },
    grid: { left: 50, right: 24, top: 32, bottom: 45 },
    xAxis: {
      type: 'category', data: cats,
      axisLabel: { color: '#666', interval: 0, rotate: 25 },
      axisLine: { lineStyle: { color: '#c0c4cc' } }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef1f6' } },
      axisLabel: { color: '#666' }
    },
    series: [{
      type: 'bar', name, data: vals, barMaxWidth: 38,
      itemStyle: {
        borderRadius: [8, 8, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#8ac4ff' },
          { offset: 1, color: '#1f4fa8' }
        ]),
        shadowColor: 'rgba(31,79,168,0.35)',
        shadowBlur: 10,
        shadowOffsetY: 6
      },
      label: { show: true, position: 'top', color: '#333', fontSize: 12 },
      emphasis: { itemStyle: { shadowBlur: 16, shadowOffsetY: 8 } }
    }]
  })
  charts.push(chart)
  if (drillMeta.length) chart.on('click', params => loadDrill(drillMeta[params.dataIndex] || {}))
}
async function loadDrill(filter) {
  drillFilter.value = filter
  const data = await getFlows({ page: 1, size: 100, ...filter })
  drillRows.value = data.records || []
}
function exportDrill() {
  const params = new URLSearchParams(drillFilter.value).toString()
  window.open(`/api/v1/flows/export?${params}`, '_blank')
}
function renderLine(el, cats, vals) {
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 24, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: cats, axisLine: { lineStyle: { color: '#c0c4cc' } }, axisLabel: { color: '#666' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef1f6' } }, axisLabel: { color: '#666' } },
    series: [{
      type: 'line', smooth: true, data: vals,
      symbol: 'circle', symbolSize: 7,
      itemStyle: { color: '#409eff', borderColor: '#fff', borderWidth: 2 },
      lineStyle: { color: '#409eff', width: 3, shadowColor: 'rgba(64,158,255,.4)', shadowBlur: 8 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.42)' },
          { offset: 1, color: 'rgba(64,158,255,0.03)' }
        ])
      }
    }]
  })
  charts.push(chart)
}
function renderPie(el, data) {
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 10, itemHeight: 10, textStyle: { color: '#666' } },
    color: ['#409eff', '#36cfc9', '#9254de', '#ff9c6e', '#67c23a', '#f56c6c', '#eb2f96', '#faad14', '#597ef7'],
    series: [{
      type: 'pie', radius: ['38%', '65%'], center: ['50%', '45%'],
      stillShowZeroSum: true, data,
      label: { formatter: '{b}\n{d}%', color: '#555' },
      itemStyle: {
        borderRadius: 6, borderColor: '#fff', borderWidth: 2,
        shadowBlur: 8, shadowOffsetY: 4, shadowColor: 'rgba(0,0,0,0.15)'
      },
      emphasis: { scaleSize: 10, itemStyle: { shadowBlur: 18 } }
    }]
  })
  charts.push(chart)
}

onMounted(loadAll)
onBeforeUnmount(() => charts.forEach(c => c.dispose()))
</script>

<style scoped>
.chart { height: 360px; }
.chart :deep(canvas) { max-width: 100%; }
.analysis-card { margin-bottom:16px; background:linear-gradient(145deg,#fff,#f8fbff); }
.analysis-card :deep(.el-card__header) { font-weight:700; color:#29445f; border-bottom-color:#edf2f7; }
</style>
