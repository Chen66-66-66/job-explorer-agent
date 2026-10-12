<script setup>
// 导入本地样本（JSON）：选择文件或粘贴内容，导入后显示报告。文件只发给本机后端。
import { ref } from 'vue'
import { api, IMPORT_OUTCOME_LABEL } from '../api.js'

const emit = defineEmits(['imported'])

const text = ref('')
const report = ref(null)
const error = ref('')
const busy = ref(false)

function readFile(event) {
  const file = event.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => (text.value = reader.result)
  reader.readAsText(file, 'utf-8')
}

async function submit() {
  error.value = ''
  report.value = null
  let bundle
  try {
    bundle = JSON.parse(text.value)
  } catch (e) {
    error.value = `不是合法的 JSON：${e.message}`
    return
  }
  busy.value = true
  try {
    report.value = await api.importSample(bundle)
    emit('imported')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="card">
    <p class="muted">
      格式见仓库 samples/README.md，可先试 samples/demo-import.json。导入只录入原文，不调用大模型，也不会标为已核查。
    </p>
    <input type="file" accept=".json,application/json" @change="readFile" />
    <textarea v-model="text" rows="6" placeholder="或者把 JSON 粘贴到这里" />
    <button class="primary" :disabled="busy || !text.trim()" @click="submit">{{ busy ? '导入中…' : '导入' }}</button>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="report" class="report">
      <h3>
        导入报告：
        <router-link :to="`/companies/${report.companyId}`">{{ report.companyName }}</router-link>
        <span class="muted">（{{ report.companyCreated ? '新建公司' : '合并到已有公司' }}）</span>
      </h3>
      <ul v-if="report.warnings.length" class="pending">
        <li v-for="w in report.warnings" :key="w">{{ w }}</li>
      </ul>
      <template v-for="group in [['公告', report.announcements], ['岗位', report.positions]]" :key="group[0]">
        <h3 v-if="group[1].length">{{ group[0] }}（{{ group[1].length }}）</h3>
        <ul class="list">
          <li v-for="(item, i) in group[1]" :key="i">
            <div class="row">
              <span class="tag" :class="item.outcome">{{ IMPORT_OUTCOME_LABEL[item.outcome] }}</span>
              <router-link v-if="group[0] === '岗位' && item.id" :to="`/positions/${item.id}`">
                <strong>{{ item.title }}</strong>
              </router-link>
              <strong v-else>{{ item.title }}</strong>
            </div>
            <ul v-if="item.notes.length" class="notes muted">
              <li v-for="n in item.notes" :key="n">{{ n }}</li>
            </ul>
          </li>
        </ul>
      </template>
    </div>
  </div>
</template>

<style scoped>
.report {
  margin-top: 12px;
}

.notes {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 13px;
}

.CREATED {
  color: var(--pass);
  border-color: var(--pass);
}

.UPDATED {
  color: var(--unknown);
  border-color: var(--unknown);
}

.SKIPPED {
  color: var(--fail);
  border-color: var(--fail);
}
</style>
