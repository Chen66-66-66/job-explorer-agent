<script setup>
import { onMounted, ref } from 'vue'
import { api } from './api.js'

const status = ref(null)
onMounted(async () => {
  status.value = await api.status().catch(() => null)
})
</script>

<template>
  <header class="topbar">
    <div class="page row spread">
      <nav class="row">
        <router-link to="/" class="brand">校招岗位探索</router-link>
        <router-link to="/agent">问 Agent</router-link>
      </nav>
      <span v-if="status" class="muted">
        档案：{{ status.candidateName }} ·
        大模型：{{ status.llmAvailable ? '已启用' : '未配置（可手动录入条件）' }}
      </span>
      <span v-else class="error">后端未连接</span>
    </div>
  </header>
  <router-view />
</template>

<style scoped>
.topbar {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.topbar .page {
  padding-top: 12px;
  padding-bottom: 12px;
}

.brand {
  font-weight: 600;
  color: var(--text);
  margin-right: 8px;
}
</style>
