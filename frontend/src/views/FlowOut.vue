<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="普通入库" name="in">
        <el-form :model="inForm" label-width="90px" style="max-width: 480px">
          <el-form-item label="备品">
            <el-select v-model="inForm.partId" filterable remote clearable
              :remote-method="searchParts" placeholder="搜索备品" style="width: 100%">
              <el-option v-for="p in partOptions" :key="p.id" :label="`${p.partNo} ${p.name}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="数量"><el-input-number v-model="inForm.qty" :min="1" /></el-form-item>
          <el-form-item label="入库日期"><el-date-picker v-model="inForm.businessDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="inForm.remark" /></el-form-item>
          <el-button type="primary" @click="submitIn">入库</el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="良品转报废" name="convert">
        <el-form :model="convertForm" label-width="100px" style="max-width:480px">
          <el-form-item label="备品"><el-select v-model="convertForm.partId" filterable style="width:100%">
            <el-option v-for="p in partOptions" :key="p.id" :label="`${p.partNo} ${p.name}`" :value="p.id" />
          </el-select></el-form-item>
          <el-form-item label="数量"><el-input-number v-model="convertForm.qty" :min="1" /></el-form-item>
          <el-form-item label="原因"><el-input v-model="convertForm.remark" /></el-form-item>
          <el-button type="danger" @click="submitConvert">转入报废区</el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="报废品清运" name="scrap">
        <el-form :model="scrapForm" label-width="90px" style="max-width: 480px">
          <el-form-item label="备品">
            <el-select v-model="scrapForm.partId" filterable remote clearable
              :remote-method="searchParts" placeholder="搜索备品" style="width: 100%">
              <el-option v-for="p in partOptions" :key="p.id" :label="`${p.partNo} ${p.name}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="数量"><el-input-number v-model="scrapForm.qty" :min="1" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="scrapForm.remark" /></el-form-item>
          <el-button type="danger" @click="submitScrap">确认清运</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-divider>流水记录</el-divider>
    <el-button type="primary" size="small" @click="loadFlows">刷新流水</el-button>
    <el-table :data="flows.records || []" border size="small" style="margin-top: 12px">
      <el-table-column prop="flowNo" label="流水号" width="200" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="flowTypeMap[row.flowType]?.type || 'info'">{{ flowTypeMap[row.flowType]?.label || row.flowType }}</el-tag>
        </template>
      </el-table-column>
      <FlowContextColumns />
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column prop="businessDate" label="业务日期" width="110" />
      <el-table-column label="异常" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isException" type="danger" size="small">异常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90"><template #default="{ row }">
        <el-button v-if="row.flowType !== 0 && row.flowType !== 10" link type="danger" @click="reverse(row)">冲正</el-button>
      </template></el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import FlowContextColumns from '../components/FlowContextColumns.vue'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getParts, getDevices, stockIn, stockOut, scrapOut, goodToScrap, getFlows, reverseFlow } from '../api'

const tab = ref('in')
const partOptions = ref([])
const devices = ref([])
const flows = ref({ records: [], total: 0 })
const today = new Date().toISOString().slice(0, 10)
const inForm = reactive({ partId: null, qty: 1, remark: '', businessDate: today })
const outForm = reactive({ partId: null, deviceId: null, qty: 1, remark: '' })
const scrapForm = reactive({ partId: null, qty: 1, remark: '' })
const convertForm = reactive({ partId: null, qty: 1, remark: '' })

const flowTypeMap = {
  0: { label: '初始化', type: 'info' },
  1: { label: '入库', type: 'success' },
  2: { label: '出库', type: 'warning' },
  3: { label: '换新出', type: 'primary' },
  4: { label: '换新入废', type: 'primary' },
  5: { label: '良品转报废', type: 'danger' },
  6: { label: '盘盈', type: 'success' },
  7: { label: '盘亏', type: 'danger' },
  8: { label: '异常换新', type: 'danger' },
  9: { label: '报废品清运', type: 'info' }
  ,10: { label: '冲正', type: 'info' }
}

async function searchParts(kw) {
  const res = await getParts({ page: 1, size: 20, keyword: kw || '' })
  partOptions.value = res.records || []
}
async function loadDevices() {
  devices.value = await getDevices()
}
async function loadFlows() {
  flows.value = await getFlows({ page: 1, size: 20 })
}
async function submitIn() {
  if (!inForm.partId || !inForm.qty) { ElMessage.warning('请选择备品和数量'); return }
  await stockIn({ partId: inForm.partId, qty: inForm.qty, remark: inForm.remark, businessDate: inForm.businessDate })
  ElMessage.success('入库成功')
  loadFlows()
}
async function submitOut() {
  if (!outForm.partId || !outForm.deviceId || !outForm.qty) { ElMessage.warning('请填写完整'); return }
  await stockOut({ partId: outForm.partId, deviceId: outForm.deviceId, qty: outForm.qty, remark: outForm.remark })
  ElMessage.success('出库成功')
  loadFlows()
}
async function submitScrap() {
  if (!scrapForm.partId || !scrapForm.qty) { ElMessage.warning('请填写完整'); return }
  await scrapOut({ partId: scrapForm.partId, qty: scrapForm.qty, remark: scrapForm.remark })
  ElMessage.success('报废品清运成功')
  loadFlows()
}
async function submitConvert() {
  if (!convertForm.partId || !convertForm.qty || !convertForm.remark) { ElMessage.warning('请填写备品、数量和原因'); return }
  await goodToScrap(convertForm)
  ElMessage.success('良品已转入报废区')
  loadFlows()
}
async function reverse(row) {
  const { value } = await ElMessageBox.prompt('请输入冲正原因', '流水冲正', { inputValidator: v => !!v || '原因不能为空' })
  await reverseFlow(row.id, value); ElMessage.success('冲正成功'); await loadFlows()
}
onMounted(() => { searchParts(''); loadDevices(); loadFlows() })
</script>
