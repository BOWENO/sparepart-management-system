<template>
  <el-table-column label="使用产线" min-width="115"><template #default="{row}">
    <div>{{ row.lineCode || '未关联产线' }}</div>
    <div v-if="row.lineName && row.lineName !== row.lineCode" class="flow-detail">{{ row.lineName }}</div>
  </template></el-table-column>
  <el-table-column label="使用设备" min-width="165"><template #default="{row}">
    <div>{{ row.deviceName || (row.deviceId ? '设备档案缺失' : '不涉及设备') }}</div>
    <div v-if="row.deviceCode" class="flow-detail">{{ row.deviceCode }}</div>
  </template></el-table-column>
  <el-table-column prop="recipientName" label="领用人" min-width="115" />
  <el-table-column label="备品" min-width="190"><template #default="{row}">
    <div>{{ row.partName || '备品档案缺失' }}</div>
    <div class="flow-detail">{{ [row.partNo, row.partSpec].filter(Boolean).join(' · ') }}</div>
  </template></el-table-column>
  <el-table-column label="数量" width="85"><template #default="{row}">{{ row.quantity }} {{ row.unit || '' }}</template></el-table-column>
  <el-table-column prop="operatorName" label="录入人" min-width="100" />
</template>
<style scoped>
.flow-detail { color: #606266; font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
</style>
