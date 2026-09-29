<template>
  <div class="page-card">
    <el-alert title="用于采购到货、退库等良品入库；设备领用请前往“备品更换”。" type="info" :closable="false" style="margin-bottom:16px" />
    <el-form :model="form" label-width="100px" style="max-width:520px">
      <el-form-item label="入库备品"><el-select v-model="form.partId" filterable remote clearable :remote-method="searchParts" style="width:100%">
        <el-option v-for="p in parts" :key="p.id" :label="`${p.partNo} ${p.name}`" :value="p.id" />
      </el-select></el-form-item>
      <el-form-item label="入库数量"><el-input-number v-model="form.qty" :min="1" /></el-form-item>
      <el-form-item label="入库日期"><el-date-picker v-model="form.businessDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="入库说明"><el-input v-model="form.remark" placeholder="采购单号、退库原因等" /></el-form-item>
      <el-form-item><el-button type="primary" @click="submit">确认入库</el-button></el-form-item>
    </el-form>
  </div>
</template>
<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getParts, stockIn } from '../api'
const parts = ref([])
const form = reactive({ partId: null, qty: 1, businessDate: new Date().toISOString().slice(0, 10), remark: '' })
async function searchParts(keyword) { const res = await getParts({ page: 1, size: 20, keyword: keyword || '' }); parts.value = res.records || [] }
async function submit() {
  if (!form.partId || !form.qty) return ElMessage.warning('请选择备品并填写数量')
  await stockIn(form); ElMessage.success('备品入库成功'); form.qty = 1; form.remark = ''
}
onMounted(() => searchParts(''))
</script>
