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
  importSample: (bundle) => request('POST', '/import', bundle),
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
  positionReview: (id) => request('GET', `/positions/${id}/review`),
  resolveMaterial: (id, resolution, note) => request('POST', `/positions/${id}/material`, { resolution, note }),
  preApplyCheck: (id) => request('POST', `/positions/${id}/pre-apply-check`),

  addRequirement: (body) => request('POST', '/requirements', body),
  deleteRequirement: (id) => request('DELETE', `/requirements/${id}`),
  rejectRequirement: (id, reason, note) => request('POST', `/requirements/${id}/reject`, { reason, note }),
  positionRejections: (id) => request('GET', `/positions/${id}/rejections`),
  companyRejections: (id) => request('GET', `/companies/${id}/rejections`),
  restoreRejection: (id) => request('POST', `/rejections/${id}/restore`),
  confirmRequirement: (id, appliesToAllPositions) =>
    request('POST', `/requirements/${id}/confirm`, { appliesToAllPositions }),
  judgeRequirement: (id, verdict, note) => request('POST', `/requirements/${id}/judgement`, { verdict, note }),

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

// 岗位的主要状态：一个岗位同一时间只有一个
export const REVIEW_STATUS_LABEL = {
  TO_CHECK: '待核查',
  PRELIM_MATCH: '初筛匹配',
  PROPOSED_EXCLUSION: '拟排除·待确认',
  EXCLUDED: '已排除',
  VERIFIED: '已核实可报',
}

export const TODO_KIND_LABEL = {
  MISSING_PROFILE: '档案缺信息',
  MISSING_MATERIAL: '缺招聘材料',
  MATERIAL_SCOPE: '材料范围',
  NEEDS_JUDGEMENT: '需本人判定',
  CONFIRM_EXCLUSION: '确认后会排除',
}

export const PENDING_LABEL = {
  MISSING_PROFILE: '档案缺信息',
  CONFIRM_EXTRACTION: '待确认抽取',
  NEEDS_JUDGEMENT: '需本人判定',
}

export const MATERIAL_LABEL = {
  PAGE_COMPLETE: '岗位页面即完整要求',
  REFERENCED_NO_EXTRA: '引用材料已核对，没有额外条件',
  REFERENCED_EXTRA_ADDED: '引用材料已核对，额外条件已补录',
}

export const REJECTION_REASON_LABEL = {
  NOT_IN_SOURCE: '原文没有这个要求',
  NOT_A_REQUIREMENT: '不是报名硬性条件',
  WRONG_VALUE: '数值或参数读错',
  WRONG_TYPE: '条件类型错',
  WRONG_SCOPE: '适用对象或分组错',
  DUPLICATE: '与另一条重复',
  OTHER: '其他',
}

export const IMPORT_OUTCOME_LABEL = { CREATED: '新增', UPDATED: '更新', UNCHANGED: '无变化', SKIPPED: '未导入' }

export const ORIGIN_LABEL = { LLM: '模型抽取', MANUAL: '人工录入', DEMO: '演示数据' }

export const STATUS_LABEL = { CANDIDATE: '候选', INTERESTED: '感兴趣', EXCLUDED: '已排除' }

export const POSITION_STATUS_LABEL = { FOUND: '已找到', APPLYING: '准备投', APPLIED: '已投递', DROPPED: '放弃' }
