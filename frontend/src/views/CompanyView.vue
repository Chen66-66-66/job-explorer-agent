<script setup>
// 第二层：单个公司。公告 → 公司级条件 → 岗位 → 笔记。
import { onMounted, reactive, ref } from 'vue'
import { api, POSITION_STATUS_LABEL, STATUS_LABEL } from '../api.js'
import CheckList from '../components/CheckList.vue'
import RequirementForm from '../components/RequirementForm.vue'
import VerdictBadge from '../components/VerdictBadge.vue'

const props = defineProps({ id: { type: String, required: true } })
const companyId = Number(props.id)

const company = ref(null)
const assessment = ref({ overall: 'UNKNOWN', results: [], pending: [] })
const announcements = ref([])
const positions = ref([])
const notes = ref([])
const error = ref('')
const busy = ref(false)

const annForm = reactive({ title: '', sourceUrl: '', viewedAt: today(), content: '' })
const posForm = reactive({ title: '', unit: '', location: '', deadline: '', sourceUrl: '', viewedAt: today(), jdText: '' })
const noteText = ref('')
const show = reactive({ ann: false, req: false, pos: false })
const reqAnnouncementId = ref(null)

function today() {
  return new Date().toISOString().slice(0, 10)
}

async function load() {
  error.value = ''
  try {
    ;[company.value, assessment.value, announcements.value, positions.value, notes.value] = await Promise.all([
      api.company(companyId),
      api.companyAssessment(companyId),
      api.announcements(companyId),
      api.positions(companyId),
      api.notes(companyId),
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

const setStatus = (status) => run(() => api.updateCompany(companyId, { ...company.value, status }))

const addAnnouncement = () =>
  run(async () => {
    await api.addAnnouncement(companyId, { ...annForm, sourceUrl: annForm.sourceUrl || null })
    Object.assign(annForm, { title: '', sourceUrl: '', content: '' })
    show.ann = false
  })

const extract = (annId) => run(() => api.extractAnnouncement(annId))
const markChecked = (annId) => run(() => api.markAnnouncementChecked(annId))

const addPosition = () =>
  run(async () => {
    await api.addPosition(companyId, {
      ...posForm,
      deadline: posForm.deadline || null,
      sourceUrl: posForm.sourceUrl || null,
    })
    Object.assign(posForm, { title: '', unit: '', location: '', deadline: '', sourceUrl: '', jdText: '' })
    show.pos = false
  })

const addNote = () =>
  run(async () => {
    await api.addNote(companyId, { content: noteText.value })
    noteText.value = ''
  })

const confirm = (id, appliesAll) => run(() => api.confirmRequirement(id, appliesAll))

onMounted(load)
</script>

<template>
  <main v-if="company" class="page">
    <p class="muted"><router-link to="/">公司</router-link> / {{ company.name }}</p>

    <section class="card">
      <div class="row spread">
        <div class="row">
          <h1>{{ company.name }}</h1>
          <VerdictBadge :verdict="assessment.overall" />
        </div>
        <select :value="company.status" style="width: auto" @change="setStatus($event.target.value)">
          <option v-for="(label, key) in STATUS_LABEL" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>
      <p v-if="company.summary">{{ company.summary }}</p>
      <p class="muted">
        {{ [company.groupName, company.platform && `招聘平台：${company.platform}`].filter(Boolean).join(' · ') }}
        <a v-if="company.careerSiteUrl" :href="company.careerSiteUrl" target="_blank" rel="noopener"> · 去招聘网站登录查看</a>
      </p>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card">
      <div class="row spread">
        <h2>公司级条件</h2>
        <button @click="show.req = !show.req">{{ show.req ? '取消' : '手动录入' }}</button>
      </div>
      <p class="muted">来自招聘公告。只有确认「适用于本批次全部岗位」的条件不符合时，才会整家剪掉。</p>
      <div v-if="show.req" class="card">
        <label>出处公告
          <select v-model="reqAnnouncementId">
            <option :value="null">（无出处）</option>
            <option v-for="a in announcements" :key="a.id" :value="a.id">{{ a.title }}</option>
          </select>
        </label>
        <RequirementForm :company-id="companyId" :announcement-id="reqAnnouncementId" @saved="load" />
      </div>
      <ul v-if="assessment.pending.length" class="pending">
        <li v-for="p in assessment.pending" :key="p">{{ p }}</li>
      </ul>
      <CheckList :results="assessment.results" @confirm="confirm" />
    </section>

    <section class="card">
      <div class="row spread">
        <h2>招聘公告</h2>
        <button @click="show.ann = !show.ann">{{ show.ann ? '取消' : '粘贴公告' }}</button>
      </div>
      <form v-if="show.ann" class="card" @submit.prevent="addAnnouncement">
        <div class="grid2">
          <label>标题 *<input v-model="annForm.title" required /></label>
          <label>来源链接<input v-model="annForm.sourceUrl" /></label>
          <label>查看日期<input v-model="annForm.viewedAt" type="date" /></label>
        </div>
        <label>公告原文 *<textarea v-model="annForm.content" required rows="8" /></label>
        <button class="primary" type="submit" :disabled="busy">保存</button>
      </form>
      <ul class="list">
        <li v-for="a in announcements" :key="a.id">
          <div class="row spread">
            <strong>{{ a.title }}</strong>
            <div class="row">
              <button :disabled="busy" @click="extract(a.id)">{{ busy ? '处理中…' : '用模型抽取条件' }}</button>
              <button v-if="!a.checkedAt" :disabled="busy" @click="markChecked(a.id)">已人工核查完</button>
            </div>
          </div>
          <p class="muted">
            {{ a.checkedAt ? '已核查' : '尚未核查' }} · 查看于 {{ a.viewedAt || '未记录' }}
            <a v-if="a.sourceUrl" :href="a.sourceUrl" target="_blank" rel="noopener"> · 来源</a>
          </p>
          <details>
            <summary class="muted">展开原文</summary>
            <div class="source">{{ a.content }}</div>
          </details>
        </li>
      </ul>
      <p v-if="!announcements.length" class="muted">还没有公告。</p>
    </section>

    <section class="card">
      <div class="row spread">
        <h2>岗位</h2>
        <button @click="show.pos = !show.pos">{{ show.pos ? '取消' : '录入岗位' }}</button>
      </div>
      <p class="muted">登录招聘网站找到感兴趣的岗位后，把信息录进来。</p>
      <form v-if="show.pos" class="card" @submit.prevent="addPosition">
        <div class="grid2">
          <label>岗位名称 *<input v-model="posForm.title" required /></label>
          <label>用人单位<input v-model="posForm.unit" /></label>
          <label>工作地点<input v-model="posForm.location" /></label>
          <label>截止日期<input v-model="posForm.deadline" type="date" /></label>
          <label>来源链接<input v-model="posForm.sourceUrl" /></label>
          <label>查看日期<input v-model="posForm.viewedAt" type="date" /></label>
        </div>
        <label>JD 原文<textarea v-model="posForm.jdText" rows="6" /></label>
        <button class="primary" type="submit" :disabled="busy">保存</button>
      </form>
      <ul class="list">
        <li v-for="p in positions" :key="p.id" class="row spread">
          <div class="row">
            <VerdictBadge :verdict="p.verdict" />
            <router-link :to="`/positions/${p.id}`"><strong>{{ p.title }}</strong></router-link>
            <span class="muted">{{ [p.unit, p.location].filter(Boolean).join(' · ') }}</span>
          </div>
          <div class="row">
            <span v-if="p.deadline" class="muted">截止 {{ p.deadline }}</span>
            <span class="tag">{{ POSITION_STATUS_LABEL[p.status] }}</span>
          </div>
        </li>
      </ul>
      <p v-if="!positions.length" class="muted">还没有岗位。</p>
    </section>

    <section class="card">
      <h2>笔记</h2>
      <form class="row" @submit.prevent="addNote">
        <input v-model="noteText" required placeholder="记录想法，比如为什么想投、要问清什么" style="flex: 1" />
        <button type="submit" :disabled="busy">记下</button>
      </form>
      <ul class="list" style="margin-top: 8px">
        <li v-for="n in notes" :key="n.id">
          <div>{{ n.content }}</div>
          <div class="muted">
            {{ new Date(n.createdAt).toLocaleString('zh-CN') }}
            <span v-if="n.positionTitle"> · {{ n.positionTitle }}</span>
          </div>
        </li>
      </ul>
    </section>
  </main>
  <main v-else class="page">
    <p :class="error ? 'error' : 'muted'">{{ error || '加载中…' }}</p>
  </main>
</template>
