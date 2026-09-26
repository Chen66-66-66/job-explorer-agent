<script setup>
import { VERDICT_LABEL } from '../api.js'

defineProps({ verdict: { type: String, default: 'UNKNOWN' } })
</script>

<template>
  <span class="badge" :class="verdict">{{ VERDICT_LABEL[verdict] }}</span>
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

.PASS {
  color: var(--pass);
  background: var(--pass-bg);
}

.FAIL {
  color: var(--fail);
  background: var(--fail-bg);
}

.UNKNOWN {
  color: var(--unknown);
  background: var(--unknown-bg);
}

.PARTIAL {
  color: var(--accent);
  background: #ddf4ff;
}

.NOT_APPLICABLE {
  color: var(--muted);
  background: var(--bg);
}
</style>
