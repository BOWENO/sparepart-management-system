<template>
  <div>
    <el-card class="page-card">
      <el-button type="primary" @click="replaceVisible = true">办理备品更换</el-button>
      <el-alert type="info" :closable="false" style="margin-top: 12px"
        title="支持一次登记多条备品更换、快速新增备品/设备，以及无旧件的异常更换模式。" />
    </el-card>
    <ReplaceBatch v-model:visible="replaceVisible" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ReplaceBatch from '../components/ReplaceBatch.vue'

const replaceVisible = ref(true)
</script>
