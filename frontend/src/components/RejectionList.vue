<script setup>
// 驳回记录：模型抽错、被本人驳回的条件。可以回看错在哪里，驳回错了可以恢复。
import { computed } from 'vue'
import { REJECTION_REASON_LABEL, TYPE_LABEL } from '../api.js'

const props = defineProps({ rejections: { type: Array, required: true } })
const emit = defineEmits(['restore'])

// 按原因计数，如「数值或参数读错 2 · 不是报名硬性条件 1」
const summary = computed(() => {
  const counts = {}
  for (const r of props.rejections) counts[r.reason] = (counts[r.reason] || 0) + 1
  return Object.entries(counts)
    .map(([reason, n]) => `${REJECTION_REASON_LABEL[reason]} ${n}`)
    .join(' · ')
})
</script>

<template>
  <details v-if="rejections.length" class="rejections">
    <summary>
      驳回记录（{{ rejections.length }}）<span class="muted"> {{ summary }}</span>
    </summary>
    <ul class="list">
      <li v-for="r in rejections" :key="r.id">
        <div class="row spread">
          <div class="row">
            <span class="tag">{{ REJECTION_REASON_LABEL[r.reason] }}</span>
            <strong>{{ TYPE_LABEL[r.type] }}</strong>
            <span>{{ r.description }}</span>
          </div>
          <button class="link" @click="emit('restore', r.id)">恢复为待确认</button>
        </div>
        <div v-if="r.params" class="muted">解析结果：{{ r.params }}</div>
        <div v-if="r.note" class="muted">说明：{{ r.note }}</div>
        <div v-if="r.quote" class="quote">{{ r.quote }}</div>
        <div class="muted">
          {{ r.source }} · {{ new Date(r.rejectedAt).toLocaleString() }} ·
          {{ r.active ? '重新抽取时跳过相同的结果' : '原文已改动，不再自动跳过' }}
        </div>
      </li>
    </ul>
  </details>
</template>

<style scoped>
.rejections {
  margin-top: 12px;
}

summary {
  cursor: pointer;
}

.link {
  border: none;
  background: none;
  color: var(--accent);
  font-size: 12px;
  padding: 0 4px;
}
</style>
