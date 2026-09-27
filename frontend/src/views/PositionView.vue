<script setup>
// 第三层：单个岗位。整体结论 = 公司级条件 + 岗位级条件。
import { onMounted, ref } from 'vue'
import { api, POSITION_STATUS_LABEL } from '../api.js'
import CheckList from '../components/CheckList.vue'
import RequirementForm from '../components/RequirementForm.vue'
import VerdictBadge from '../components/VerdictBadge.vue'

const props = defineProps({ id: { type: String, required: true } })
const positionId = Number(props.id)

const position = ref(null)
const assessment = ref({ overall: 'UNKNOWN', results: [], pending: [] })
const error = ref('')
const busy = ref(false)
const showReq = ref(false)

async function load() {
  error.value = ''
  try {
    ;[position.value, assessment.value] = await Promise.all([
      api.position(positionId),
      api.positionAssessment(positionId),
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
const remove = (id) => {
  if (window.confirm('确定删除这条条件？重新抽取时模型可能再次抽出它。')) run(() => api.deleteRequirement(id))
}

onMounted(load)
</script>

<template>
  <main v-if="position" class="page">
    <p class="muted">
      <router-link to="/">公司</router-link> /
      <router-link :to="`/companies/${position.companyId}`">{{ position.companyName }}</router-link> /
      {{ position.title }}
    </p>

    <section class="card">
      <div class="row spread">
        <div class="row">
          <h1>{{ position.title }}</h1>
          <VerdictBadge :verdict="assessment.overall" />
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
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card">
      <div class="row spread">
        <h2>资格判断</h2>
        <div class="row">
          <button :disabled="busy || !position.jdText" @click="extract">{{ busy ? '处理中…' : '用模型从 JD 抽取' }}</button>
          <button v-if="position.jdText && !position.checkedAt" :disabled="busy" @click="markChecked">JD 已人工核查完</button>
          <button @click="showReq = !showReq">{{ showReq ? '取消' : '手动录入' }}</button>
        </div>
      </div>
      <div v-if="showReq" class="card">
        <RequirementForm :company-id="position.companyId" :position-id="positionId" @saved="load" />
      </div>
      <ul v-if="assessment.pending.length" class="pending">
        <li v-for="p in assessment.pending" :key="p">{{ p }}</li>
      </ul>
      <CheckList :results="assessment.results" @confirm="confirm" @remove="remove" />
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
