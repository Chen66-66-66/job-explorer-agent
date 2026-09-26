<script setup>
// 第一层：公司列表。公司级条件不符合的会被标为「不符合」，下面的岗位不必再看。
import { computed, onMounted, reactive, ref } from 'vue'
import { api, STATUS_LABEL } from '../api.js'
import VerdictBadge from '../components/VerdictBadge.vue'

const companies = ref([])
const error = ref('')
const showForm = ref(false)
const form = reactive({ name: '', groupName: '', platform: '', careerSiteUrl: '', summary: '' })

const ORDER = { PASS: 0, UNKNOWN: 1, FAIL: 2 }
const sorted = computed(() => [...companies.value].sort((a, b) => ORDER[a.verdict] - ORDER[b.verdict]))
const pruned = computed(() => companies.value.filter((c) => c.verdict === 'FAIL').length)

async function load() {
  try {
    companies.value = await api.companies()
  } catch (e) {
    error.value = e.message
  }
}

async function create() {
  error.value = ''
  try {
    await api.createCompany({ ...form })
    Object.assign(form, { name: '', groupName: '', platform: '', careerSiteUrl: '', summary: '' })
    showForm.value = false
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="row spread">
      <h1>公司</h1>
      <button class="primary" @click="showForm = !showForm">{{ showForm ? '取消' : '添加公司' }}</button>
    </div>
    <p class="muted">
      共 {{ companies.length }} 家，其中 {{ pruned }} 家因公告硬条件不符合被剪掉。
      按「符合 → 待核实 → 不符合」排序。
    </p>
    <p v-if="error" class="error">{{ error }}</p>

    <form v-if="showForm" class="card" @submit.prevent="create">
      <div class="grid2">
        <label>公司名称 *<input v-model="form.name" required /></label>
        <label>所属集团<input v-model="form.groupName" /></label>
        <label>招聘平台<input v-model="form.platform" placeholder="北森 / 国聘 / 智联 / 自有" /></label>
        <label>招聘网站<input v-model="form.careerSiteUrl" /></label>
      </div>
      <label>简介<textarea v-model="form.summary" rows="2" /></label>
      <button class="primary" type="submit">保存</button>
    </form>

    <div class="card">
      <ul class="list">
        <li v-for="c in sorted" :key="c.id">
          <div class="row spread">
            <div class="row">
              <VerdictBadge :verdict="c.verdict" />
              <router-link :to="`/companies/${c.id}`"><strong>{{ c.name }}</strong></router-link>
              <span v-if="c.groupName" class="muted">{{ c.groupName }}</span>
            </div>
            <div class="row">
              <span v-if="c.platform" class="tag">{{ c.platform }}</span>
              <span class="tag">{{ STATUS_LABEL[c.status] }}</span>
            </div>
          </div>
          <div v-if="c.summary" class="muted">{{ c.summary }}</div>
        </li>
      </ul>
      <p v-if="!companies.length" class="muted">还没有公司。</p>
    </div>
  </main>
</template>
