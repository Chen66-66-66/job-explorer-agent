// 所有后端请求集中在这里。出错时抛出后端返回的中文提示。
async function request(method, url, body) {
  const res = await fetch(`/api${url}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.message || `请求失败（${res.status}）`)
  return data
}

export const api = {
  status: () => request('GET', '/status'),

  companies: () => request('GET', '/companies'),
  company: (id) => request('GET', `/companies/${id}`),
  createCompany: (body) => request('POST', '/companies', body),
  updateCompany: (id, body) => request('PATCH', `/companies/${id}`, body),
  companyAssessment: (id) => request('GET', `/companies/${id}/assessment`),

  announcements: (id) => request('GET', `/companies/${id}/announcements`),
  addAnnouncement: (id, body) => request('POST', `/companies/${id}/announcements`, body),
  extractAnnouncement: (id) => request('POST', `/announcements/${id}/extract`),
  markAnnouncementChecked: (id) => request('POST', `/announcements/${id}/mark-checked`),

  positions: (id) => request('GET', `/companies/${id}/positions`),
  addPosition: (id, body) => request('POST', `/companies/${id}/positions`, body),
  position: (id) => request('GET', `/positions/${id}`),
  updatePosition: (id, body) => request('PATCH', `/positions/${id}`, body),
  extractPosition: (id) => request('POST', `/positions/${id}/extract`),
  positionAssessment: (id) => request('GET', `/positions/${id}/assessment`),
  markPositionChecked: (id) => request('POST', `/positions/${id}/mark-checked`),

  addRequirement: (body) => request('POST', '/requirements', body),
  deleteRequirement: (id) => request('DELETE', `/requirements/${id}`),
  confirmRequirement: (id, appliesToAllPositions) =>
    request('POST', `/requirements/${id}/confirm`, { appliesToAllPositions }),

  askAgent: (question) => request('POST', '/agent/ask', { question }),

  notes: (id) => request('GET', `/companies/${id}/notes`),
  addNote: (id, body) => request('POST', `/companies/${id}/notes`, body),
}

export const VERDICT_LABEL = {
  PASS: '符合',
  FAIL: '不符合',
  UNKNOWN: '待核实',
  PARTIAL: '已核查部分通过',
  NOT_APPLICABLE: '不适用',
}

export const TYPE_LABEL = {
  DEGREE: '学历',
  MAJOR: '专业',
  ENGLISH: '英语',
  GRADUATION_WINDOW: '毕业时间',
  GRADUATION_COHORT: '届别',
  AGE: '年龄',
  OVERSEAS_CERT: '留服认证',
  DEADLINE: '截止日',
  OTHER: '其他',
}

export const ORIGIN_LABEL = { LLM: '模型抽取', MANUAL: '人工录入', DEMO: '演示数据' }

export const STATUS_LABEL = { CANDIDATE: '候选', INTERESTED: '感兴趣', EXCLUDED: '已排除' }

export const POSITION_STATUS_LABEL = { FOUND: '已找到', APPLYING: '准备投', APPLIED: '已投递', DROPPED: '放弃' }
