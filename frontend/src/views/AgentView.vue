<script setup>
// 向 Agent 提问。回答下方列出模型调用了哪些工具，便于核对回答的依据。
import { ref } from 'vue'
import { api } from '../api.js'

const EXAMPLES = ['我能报哪些公司和岗位？哪些不能报，为什么？', '哪些岗位还需要我去核实？分别要核实什么？', '最近快截止的岗位有哪些？']

const question = ref(EXAMPLES[0])
const answer = ref(null)
const error = ref('')
const busy = ref(false)

async function ask() {
  error.value = ''
  busy.value = true
  answer.value = null
  try {
    answer.value = await api.askAgent(question.value)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page">
    <h1>问 Agent</h1>
    <p class="muted">
      Agent 会自己决定查询哪些公司、岗位和条件，再根据规则判断的结果回答。它只能读取，不能修改任何数据；
      「能不能报」由规则计算，模型不会自行改判。
    </p>

    <form class="card" @submit.prevent="ask">
      <label>问题<textarea v-model="question" rows="2" maxlength="500" required /></label>
      <div class="row spread">
        <div class="row">
          <button v-for="q in EXAMPLES" :key="q" type="button" class="example" @click="question = q">{{ q }}</button>
        </div>
        <button class="primary" type="submit" :disabled="busy">{{ busy ? '思考中…' : '提问' }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </form>

    <template v-if="answer">
      <section class="card">
        <h2>回答</h2>
        <div class="answer">{{ answer.answer }}</div>
      </section>
      <section class="card">
        <h2>依据：工具调用记录</h2>
        <ol class="trace">
          <li v-for="(t, i) in answer.trace" :key="i">
            <code>{{ t.tool }}({{ t.arguments }})</code>
            <span class="muted"> → {{ t.result }}</span>
          </li>
        </ol>
        <p v-if="!answer.trace.length" class="error">模型没有调用任何工具，这个回答没有依据，请不要采信。</p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.answer {
  white-space: pre-wrap;
}

.example {
  font-size: 12px;
  padding: 2px 8px;
  color: var(--muted);
}

.trace {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
}

code {
  font-size: 12px;
  background: var(--bg);
  padding: 1px 4px;
  border-radius: 4px;
}
</style>
