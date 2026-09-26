<script setup>
// 逐条展示判断结果：结论、理由、原文引用、核对与确认状态。
// 模型抽取的条件需要人工确认后才参与确定性判断。
import { reactive } from 'vue'
import VerdictBadge from './VerdictBadge.vue'
import { ORIGIN_LABEL, TYPE_LABEL } from '../api.js'

defineProps({ results: { type: Array, required: true } })
const emit = defineEmits(['confirm'])

// 公司级条件确认时，是否同时确认「适用于本批次全部岗位」
const appliesAll = reactive({})
</script>

<template>
  <p v-if="!results.length" class="muted">还没有条件。可以让模型从原文抽取，或者手动录入。</p>
  <ul v-else class="list">
    <li v-for="r in results" :key="r.requirementId">
      <div class="row spread">
        <div class="row">
          <VerdictBadge :verdict="r.verdict" />
          <strong>{{ TYPE_LABEL[r.type] }}</strong>
          <span>{{ r.description }}</span>
        </div>
        <div class="row">
          <span v-if="r.appliesTo" class="tag">仅限：{{ r.appliesTo }}</span>
          <span v-if="r.alternativeGroup" class="tag">满足其一：{{ r.alternativeGroup }}</span>
          <span class="tag">{{ r.companyLevel ? (r.appliesToAllPositions ? '公司级 · 适用全部岗位' : '公司级') : '岗位级' }}</span>
          <span class="tag">{{ ORIGIN_LABEL[r.origin] }}</span>
        </div>
      </div>
      <div class="muted">{{ r.reason }}</div>
      <div v-if="r.parseIssue" class="error">⚠ {{ r.parseIssue }}</div>
      <div v-if="r.quote" class="quote">
        {{ r.quote }}
        <span :class="r.quoteVerified ? 'ok' : 'bad'">
          {{ r.quoteVerified ? '✓ 已在原文中找到' : '✗ 原文中找不到这句' }}
        </span>
      </div>
      <div v-if="!r.confirmed" class="row confirm">
        <span class="muted">核对引用和解析结果无误后：</span>
        <label v-if="r.companyLevel" class="row inline">
          <input v-model="appliesAll[r.requirementId]" type="checkbox" /> 适用于本批次全部岗位
        </label>
        <button @click="emit('confirm', r.requirementId, !!appliesAll[r.requirementId])">确认</button>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.ok {
  color: var(--pass);
  margin-left: 6px;
}

.bad {
  color: var(--fail);
  margin-left: 6px;
}

.confirm {
  margin-top: 4px;
}

.inline {
  margin: 0;
  gap: 4px;
}

.inline input {
  width: auto;
}
</style>
