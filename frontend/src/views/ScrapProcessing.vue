<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="报废品清运" name="clear">
        <el-alert title="处理已通过以旧换新进入报废区的旧件，操作后同步核销报废品档案。" type="info" :closable="false" style="margin-bottom:16px" />
        <el-form :model="clearForm" label-width="110px" style="max-width:520px">
          <el-form-item label="报废备品"><el-select v-model="clearForm.partId" filterable style="width:100%">
            <el-option v-for="p in parts" :key="p.id" :label="`${p.partNo} ${p.name}（待处理 ${p.scrapStock}）`" :value="p.id" />
          </el-select></el-form-item>
          <el-form-item label="清运数量"><el-input-number v-model="clearForm.qty" :min="1" /></el-form-item>
          <el-form-item label="处理说明"><el-input v-model="clearForm.remark" placeholder="回收单位、处置方式等" /></el-form-item>
          <el-form-item><el-button type="danger" @click="clearScrap">确认清运</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="库内损坏报废" name="damage">
        <el-alert title="仅用于尚未安装到设备、但在仓库内损坏的良品。设备拆回旧件必须走“备品更换”。" type="warning" :closable="false" style="margin-bottom:16px" />
        <el-form :model="damageForm" label-width="110px" style="max-width:520px">
          <el-form-item label="备品"><el-select v-model="damageForm.partId" filterable style="width:100%">
            <el-option v-for="p in parts" :key="p.id" :label="`${p.partNo} ${p.name}`" :value="p.id" />
          </el-select></el-form-item>
          <el-form-item label="报废数量"><el-input-number v-model="damageForm.qty" :min="1" /></el-form-item>
          <el-form-item label="损坏原因"><el-input v-model="damageForm.remark" /></el-form-item>
          <el-form-item><el-button type="danger" @click="damageScrap">确认转入报废区</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getParts, scrapOut, goodToScrap } from '../api'
const tab = ref('clear'), parts = ref([])
const clearForm = reactive({ partId: null, qty: 1, remark: '' })
const damageForm = reactive({ partId: null, qty: 1, remark: '' })
async function loadParts() { const res = await getParts({ page: 1, size: 100 }); parts.value = res.records || [] }
async function clearScrap() { if (!clearForm.partId || !clearForm.qty) return ElMessage.warning('请填写完整'); await scrapOut(clearForm); ElMessage.success('报废品已完成清运处理'); await loadParts() }
async function damageScrap() { if (!damageForm.partId || !damageForm.qty || !damageForm.remark) return ElMessage.warning('请填写备品、数量和损坏原因'); await goodToScrap(damageForm); ElMessage.success('库内损坏备品已转入报废区'); await loadParts() }
onMounted(loadParts)
</script>
