<script setup>
// 手动录入一条条件。填了原文引用时，后端同样会核对它是否真的出现在原文里。
import { computed, reactive, ref } from 'vue'
import { api, TYPE_LABEL } from '../api.js'

const props = defineProps({
  companyId: { type: Number, required: true },
  announcementId: { type: Number, default: null },
  positionId: { type: Number, default: null },
})
const emit = defineEmits(['saved'])

const empty = () => ({
  type: 'ENGLISH',
  description: '',
  quote: '',
  level: '',
  minScore: null,
  allowEquivalent: false,
  minDate: '',
  maxDate: '',
  maxAge: null,
  ageReferenceDate: '',
  listValues: '',
  ageStrict: false,
  appliesTo: '',
  alternativeGroup: '',
  appliesToAllPositions: false,
})
const form = reactive(empty())
const error = ref('')

const needs = computed(() => ({
  level: ['DEGREE', 'ENGLISH'].includes(form.type),
  score: form.type === 'ENGLISH',
  minDate: form.type === 'GRADUATION_WINDOW',
  maxDate: ['GRADUATION_WINDOW', 'DEADLINE'].includes(form.type),
  age: form.type === 'AGE',
  list: ['MAJOR', 'GRADUATION_COHORT'].includes(form.type),
}))

function blankToNull(v) {
  return v === '' ? null : v
}

async function save() {
  error.value = ''
  try {
    await api.addRequirement({
      companyId: props.companyId,
      announcementId: props.announcementId,
      positionId: props.positionId,
      type: form.type,
      description: form.description,
      quote: blankToNull(form.quote),
      level: blankToNull(form.level),
      minScore: form.minScore,
      allowEquivalent: form.allowEquivalent,
      minDate: blankToNull(form.minDate),
      maxDate: blankToNull(form.maxDate),
      maxAge: form.maxAge,
      ageReferenceDate: blankToNull(form.ageReferenceDate),
      listValues: blankToNull(form.listValues),
      ageStrict: form.ageStrict,
      appliesTo: blankToNull(form.appliesTo),
      alternativeGroup: blankToNull(form.alternativeGroup),
      appliesToAllPositions: !props.positionId && form.appliesToAllPositions,
    })
    Object.assign(form, empty())
    emit('saved')
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <form @submit.prevent="save">
    <div class="grid2">
      <label>类型
        <select v-model="form.type">
          <option v-for="(label, key) in TYPE_LABEL" :key="key" :value="key">{{ label }}</option>
        </select>
      </label>
      <label>说明 *<input v-model="form.description" required placeholder="如：须通过大学英语六级" /></label>
      <label v-if="needs.level">{{ form.type === 'DEGREE' ? '最低学历（本科/硕士/博士）' : '证书类型（CET-4、CET-6、IELTS…）' }}
        <input v-model="form.level" />
      </label>
      <label v-if="needs.score">最低分数<input v-model.number="form.minScore" type="number" step="0.5" /></label>
      <label v-if="needs.score" class="row"><input v-model="form.allowEquivalent" type="checkbox" style="width: auto" /> 接受同等水平证书</label>
      <label v-if="needs.minDate">起始日期<input v-model="form.minDate" type="date" /></label>
      <label v-if="needs.maxDate">{{ form.type === 'DEADLINE' ? '截止日期' : '结束日期' }}<input v-model="form.maxDate" type="date" /></label>
      <label v-if="needs.age">年龄上限（周岁）<input v-model.number="form.maxAge" type="number" /></label>
      <label v-if="needs.age">年龄计算截止日<input v-model="form.ageReferenceDate" type="date" /></label>
      <label v-if="needs.age" class="row"><input v-model="form.ageStrict" type="checkbox" style="width: auto" /> 原文是「未满 N 周岁」</label>
      <label v-if="needs.list">{{ form.type === 'MAJOR' ? '专业名单（顿号分隔）' : '届别年份（如 2027，多个用顿号分隔）' }}<input v-model="form.listValues" /></label>
      <label>适用对象（只针对部分人时填，如「硕士研究生」）<input v-model="form.appliesTo" /></label>
      <label>二选一组名（满足其一即可的几条填同一个名字）<input v-model="form.alternativeGroup" /></label>
    </div>
    <label v-if="!positionId" class="row"><input v-model="form.appliesToAllPositions" type="checkbox" style="width: auto" /> 适用于本批次全部岗位（勾选后才会用于整家公司排除）</label>
    <label>原文引用（从原文复制，系统会核对）<textarea v-model="form.quote" rows="2" /></label>
    <p v-if="error" class="error">{{ error }}</p>
    <button type="submit">保存条件</button>
  </form>
</template>
