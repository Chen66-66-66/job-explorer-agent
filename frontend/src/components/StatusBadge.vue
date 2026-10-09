<script setup>
// 岗位的主要状态。和 VerdictBadge（单条条件的结论）分开，避免把「没发现不符合」显示成「符合」。
import { REVIEW_STATUS_LABEL } from '../api.js'

defineProps({ status: { type: String, default: 'TO_CHECK' } })
</script>

<template>
  <span class="badge" :class="status">{{ REVIEW_STATUS_LABEL[status] }}</span>
</template>

<style scoped>
.badge {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

.TO_CHECK {
  color: var(--muted);
  background: var(--bg);
  border: 1px solid var(--border);
}

.PRELIM_MATCH {
  color: var(--accent);
  background: #ddf4ff;
}

.PROPOSED_EXCLUSION {
  color: var(--unknown);
  background: var(--unknown-bg);
}

.EXCLUDED {
  color: var(--fail);
  background: var(--fail-bg);
}

.VERIFIED {
  color: var(--pass);
  background: var(--pass-bg);
}
</style>
