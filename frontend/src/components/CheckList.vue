<script setup>
// 逐条展示判断结果：结论、理由、原文引用、核对与确认状态。
// 模型抽取的条件需要人工确认后才参与确定性判断；规则判断不了的口径问题，由本人判定并写明依据。
// 模型抽错的条件要「驳回」并选原因（留下记录），人工录入的直接删除。
import { reactive } from 'vue'
import VerdictBadge from './VerdictBadge.vue'
import { ORIGIN_LABEL, PENDING_LABEL, REJECTION_REASON_LABEL, TYPE_LABEL } from '../api.js'

defineProps({ results: { type: Array, required: true } })
const emit = defineEmits(['confirm', 'remove', 'judge', 'reject'])

// 公司级条件确认时，是否同时确认「适用于本批次全部岗位」
const appliesAll = reactive({})
// 本人判定的依据，按条件分别填写
const notes = reactive({})

// 正在驳回的条件：选择的原因和说明
const rejecting = reactive({})
// 读错了的，驳回后可以手动录入一条正确的
const FIXABLE = ['WRONG_VALUE', 'WRONG_TYPE', 'WRONG_SCOPE']

function judge(r, verdict) {
  emit('judge', r.requirementId, verdict, notes[r.requirementId] || '')
}

// 驳回成功后这条条件会从列表消失；失败时表单保留，已填的说明不丢
function reject(id) {
  emit('reject', id, rejecting[id].reason, rejecting[id].note)
}
</script>

<template>
  <p v-if="!results.length" class="muted">还没有条件。可以让模型从原文抽取，或者手动录入。</p>
  <ul v-else class="list">
    <li v-for="r in results" :id="`req-${r.requirementId}`" :key="r.requirementId">
      <div class="row spread">
        <div class="row">
          <VerdictBadge :verdict="r.verdict" />
          <strong>{{ TYPE_LABEL[r.type] }}</strong>
          <span>{{ r.description }}</span>
        </div>
        <div class="row">
          <span v-if="r.pending" class="tag pending-tag">{{ PENDING_LABEL[r.pending] }}</span>
          <span v-if="r.judged" class="tag">本人判定</span>
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
      <div class="row confirm">
        <template v-if="!r.confirmed">
          <span class="muted">核对引用和解析结果无误后：</span>
          <label v-if="r.companyLevel" class="row inline">
            <input v-model="appliesAll[r.requirementId]" type="checkbox" /> 适用于本批次全部岗位
          </label>
          <button @click="emit('confirm', r.requirementId, !!appliesAll[r.requirementId])">确认</button>
        </template>
        <template v-else-if="r.pending === 'NEEDS_JUDGEMENT'">
          <input v-model="notes[r.requirementId]" class="note" placeholder="判定依据，如「已电话确认电子信息属于相关专业」" />
          <button @click="judge(r, 'PASS')">判定符合</button>
          <button @click="judge(r, 'FAIL')">判定不符合</button>
        </template>
        <button v-if="r.judged" class="link" @click="emit('judge', r.requirementId, null, '')">撤销本人判定</button>
        <template v-if="r.origin === 'LLM'">
          <button v-if="!rejecting[r.requirementId]" class="link"
            @click="rejecting[r.requirementId] = { reason: 'NOT_IN_SOURCE', note: '' }">驳回（抽错了）</button>
        </template>
        <button v-else class="link" @click="emit('remove', r.requirementId)">删除</button>
      </div>
      <div v-if="rejecting[r.requirementId]" class="row confirm">
        <select v-model="rejecting[r.requirementId].reason" style="width: auto">
          <option v-for="(label, key) in REJECTION_REASON_LABEL" :key="key" :value="key">{{ label }}</option>
        </select>
        <input v-model="rejecting[r.requirementId].note" class="note" placeholder="说明（选「其他」时必填）" />
        <button @click="reject(r.requirementId)">确认驳回</button>
        <button class="link" @click="delete rejecting[r.requirementId]">取消</button>
        <span v-if="FIXABLE.includes(rejecting[r.requirementId].reason)" class="muted">
          驳回后可用「手动录入」补一条正确的
        </span>
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

.note {
  flex: 1;
  min-width: 220px;
}

.pending-tag {
  color: var(--unknown);
  border-color: var(--unknown);
}

.link {
  border: none;
  background: none;
  color: var(--muted);
  font-size: 12px;
  padding: 0 4px;
}
</style>
