<template>
  <div class="page-card">
    <div class="toolbar"><el-button type="primary" @click="load">刷新</el-button><el-button @click="exportRows">导出流水</el-button></div>
    <el-table :data="rows.records || []" border size="small">
      <el-table-column label="业务类型" width="120"><template #default="{row}">{{ types[row.flowType] || '未知类型' }}</template></el-table-column>
      <FlowContextColumns />
      <el-table-column prop="remark" label="说明" min-width="150" /><el-table-column prop="businessDate" label="业务日期" width="110" />
      <el-table-column prop="createdAt" label="操作时间" width="170" />
      <el-table-column prop="flowNo" label="流水单号" width="220" />
      <el-table-column v-if="isAdmin" label="操作" width="110"><template #default="{row}">
        <el-button v-if="row.flowType === 3 || row.flowType === 8" link type="danger" @click="cancelReplace(row)">撤销换新单</el-button>
        <el-button v-else-if="reversibleTypes.includes(row.flowType)" link type="danger" @click="reverse(row)">撤销</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="rows.total || 0" :current-page="page" :page-size="20" @current-change="p => { page=p; load() }" />
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFlows, reverseFlow, cancelReplacement } from '../api'
import { useUserStore } from '../store/user'
import FlowContextColumns from '../components/FlowContextColumns.vue'
const user = useUserStore(), isAdmin = computed(() => user.isAdmin), rows = ref({records:[],total:0}), page = ref(1)
const types={0:'初始化',1:'备品入库',2:'历史普通出库',3:'更换领新',4:'更换旧件入废',5:'库内损坏报废',6:'盘盈',7:'盘亏',8:'异常更换',9:'报废品清运',10:'冲正'}
const reversibleTypes=[1,2,5,6,7,9]
async function load(){ rows.value=await getFlows({page:page.value,size:20}) }
async function reverse(row){ const {value}=await ElMessageBox.prompt('请输入冲正原因','流水冲正',{inputValidator:v=>!!v||'原因不能为空'}); await reverseFlow(row.id,value); ElMessage.success('冲正成功'); load() }
async function cancelReplace(row){ const {value}=await ElMessageBox.prompt('将整体撤销该换新单的良品出库和旧品入废，请输入原因','撤销换新单',{confirmButtonText:'确认撤销',inputValidator:v=>!!v||'原因不能为空'}); await cancelReplacement(row.flowNo,value); ElMessage.success('换新单已整体撤销'); load() }
function exportRows(){ window.open('/api/v1/flows/export','_blank') }
onMounted(load)
</script>
