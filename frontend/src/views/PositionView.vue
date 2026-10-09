<script setup>
// 第三层：单个岗位。顶部是主要状态和待办，下面是逐条条件（公司级 + 岗位级）。
import { computed, onMounted, ref } from 'vue'
import { api, MATERIAL_LABEL, POSITION_STATUS_LABEL, TODO_KIND_LABEL } from '../api.js'
import CheckList from '../components/CheckList.vue'
import RequirementForm from '../components/RequirementForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import VerdictBadge from '../components/VerdictBadge.vue'

const props = defineProps({ id: { type: String, required: true } })
const positionId = Number(props.id)

const position = ref(null)
const assessment = ref({ overall: 'UNKNOWN', results: [], pending: [] })
const review = ref(null)
const error = ref('')
const busy = ref(false)
const showReq = ref(false)
const material = ref({ resolution: 'PAGE_COMPLETE', note: '' })

const materialTodo = computed(() =>
  review.value?.todos.some((t) => t.kind === 'MISSING_MATERIAL' || t.kind === 'MATERIAL_SCOPE'),
)

async function load() {
  error.value = ''
  try {
    ;[position.value, assessment.value, review.value] = await Promise.all([
      api.position(positionId),
      api.positionAssessment(positionId),
      api.positionReview(positionId),
    ])
  } catch (e) {
    error.value = e.message
  }
}

async function run(fn) {
  error.value = ''
  busy.value = true
  try {
    await fn()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

const extract = () => run(() => api.extractPosition(positionId))
const markChecked = () => run(() => api.markPositionChecked(positionId))
const setStatus = (status) => run(() => api.updatePosition(positionId, { ...position.value, status }))

const confirm = (id, appliesAll) => run(() => api.confirmRequirement(id, appliesAll))
const judge = (id, verdict, note) => run(() => api.judgeRequirement(id, verdict, note))
const remove = (id) => {
  if (window.confirm('确定删除这条条件？重新抽取时模型可能再次抽出它。')) run(() => api.deleteRequirement(id))
}

const saveMaterial = () =>
  run(async () => {
    await api.resolveMaterial(positionId, material.value.resolution, material.value.note)
    material.value.note = ''
  })
const revokeMaterial = () => run(() => api.resolveMaterial(positionId, null, null))
const preApplyCheck = () => {
  if (window.confirm('请确认：已打开官网岗位页，岗位要求、截止时间与这里记录的一致。')) run(() => api.preApplyCheck(positionId))
}

function goTo(requirementId) {
  document.getElementById(`req-${requirementId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function deadlineText(r) {
  if (r.daysLeft == null) return ''
  if (r.daysLeft < 0) return `已截止 ${-r.daysLeft} 天`
  return r.daysLeft === 0 ? '今天截止' : `距截止 ${r.daysLeft} 天`
}

onMounted(load)
</script>

<template>
  <main v-if="position" class="page">
    <p v-if="error" class="error-banner">{{ error }}</p>
    <p class="muted">
      <router-link to="/">公司</router-link> /
      <router-link :to="`/companies/${position.companyId}`">{{ position.companyName }}</router-link> /
      {{ position.title }}
    </p>

    <section class="card">
      <div class="row spread">
        <div class="row">
          <h1>{{ position.title }}</h1>
          <StatusBadge v-if="review" :status="review.status" />
        </div>
        <select :value="position.status" style="width: auto" @change="setStatus($event.target.value)">
          <option v-for="(label, key) in POSITION_STATUS_LABEL" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>
      <p class="muted">
        {{ [position.unit, position.location].filter(Boolean).join(' · ') }}
        <span v-if="position.deadline"> · 截止 {{ position.deadline }}</span>
        <span> · 查看于 {{ position.viewedAt || '未记录' }}</span>
        <a v-if="position.sourceUrl" :href="position.sourceUrl" target="_blank" rel="noopener"> · 来源</a>
      </p>
      <p v-if="review">
        {{ review.statusReason }}
        <span v-if="deadlineText(review)" :class="review.daysLeft < 3 ? 'error' : 'muted'">（{{ deadlineText(review) }}）</span>
      </p>
    </section>

    <section v-if="review" class="card">
      <h2>待办（{{ review.todos.length }}）</h2>
      <p v-if="!review.todos.length" class="muted">没有待办。</p>
      <ul v-else class="list">
        <li v-for="(t, i) in review.todos" :key="i" class="row spread">
          <div class="row todo">
            <span class="tag">{{ TODO_KIND_LABEL[t.kind] }}</span>
            <span>{{ t.text }}</span>
          </div>
          <button v-if="t.requirementId" class="link" @click="goTo(t.requirementId)">去处理</button>
        </li>
      </ul>

      <h3>材料范围</h3>
      <p v-if="review.materialBasis">
        {{ review.materialBasis }}
        <button v-if="position.materialResolution" class="link" :disabled="busy" @click="revokeMaterial">撤销</button>
      </p>
      <form v-if="materialTodo" class="row" @submit.prevent="saveMaterial">
        <select v-model="material.resolution" style="width: auto">
          <option v-for="(label, key) in MATERIAL_LABEL" :key="key" :value="key">{{ label }}</option>
        </select>
        <input v-model="material.note" class="grow" placeholder="依据：看了哪份材料、在哪里看的" />
        <button :disabled="busy" type="submit">记录</button>
      </form>
      <p v-if="materialTodo" class="muted">看过但仍无法判断时先不记录，待办会保留。</p>

      <div class="grid2">
        <div>
          <h3>已确认满足（{{ review.verifiedConditions.length }}）</h3>
          <ul class="plain">
            <li v-for="c in review.verifiedConditions" :key="c">✓ {{ c }}</li>
          </ul>
        </div>
        <div v-if="review.unverifiedPositives.length">
          <h3>规则判满足，待确认抽取（{{ review.unverifiedPositives.length }}）</h3>
          <ul class="plain muted">
            <li v-for="c in review.unverifiedPositives" :key="c">· {{ c }}</li>
          </ul>
        </div>
      </div>

      <h3>投递前核对</h3>
      <p v-if="review.preApplyCheckedAt" class="muted">
        已于 {{ new Date(review.preApplyCheckedAt).toLocaleString() }} 对照官网核对。
      </p>
      <div class="row">
        <button class="primary" :disabled="busy || !review.readyForPreApplyCheck" @click="preApplyCheck">
          {{ review.preApplyCheckedAt ? '重新核对' : '已对照官网核对，信息无变化' }}
        </button>
        <span v-if="!review.readyForPreApplyCheck" class="muted">所有适用条件确认满足、待办清空后才能核对。</span>
      </div>
    </section>

    <section class="card">
      <div class="row spread">
        <div class="row">
          <h2>逐条条件</h2>
          <VerdictBadge :verdict="assessment.overall" />
        </div>
        <div class="row">
          <button :disabled="busy || !position.jdText" @click="extract">{{ busy ? '处理中…' : '用模型从 JD 抽取' }}</button>
          <button v-if="position.jdText && !position.checkedAt" :disabled="busy" @click="markChecked">JD 已人工核查完</button>
          <button @click="showReq = !showReq">{{ showReq ? '取消' : '手动录入' }}</button>
        </div>
      </div>
      <div v-if="showReq" class="card">
        <RequirementForm :company-id="position.companyId" :position-id="positionId" @saved="load" />
      </div>
      <CheckList :results="assessment.results" @confirm="confirm" @remove="remove" @judge="judge" />
    </section>

    <section class="card">
      <h2>JD 原文</h2>
      <div v-if="position.jdText" class="source">{{ position.jdText }}</div>
      <p v-else class="muted">没有录入 JD 原文。</p>
    </section>
  </main>
  <main v-else class="page">
    <p :class="error ? 'error' : 'muted'">{{ error || '加载中…' }}</p>
  </main>
</template>

<style scoped>
.todo {
  flex: 1;
  flex-wrap: nowrap;
  align-items: flex-start;
}

.todo .tag {
  flex-shrink: 0;
}

.grow {
  flex: 1;
  min-width: 220px;
}

.plain {
  list-style: none;
  padding: 0;
  margin: 0;
}

.link {
  border: none;
  background: none;
  color: var(--accent);
  font-size: 12px;
  padding: 0 4px;
}
</style>
