import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'

// Render the actual portal templates in representative states, without a live API.
const server = await createServer({
  configLoader: 'native',
  server: { middlewareMode: true },
  optimizeDeps: { noDiscovery: true, entries: [] },
  plugins: [{
    name: 'portal-test-state', enforce: 'pre',
    resolveId(id) { if (id.endsWith('/composables/usePortal')) return '\0portal-state' },
    load(id) {
      if (id !== '\0portal-state') return
      return `export function usePortal() {
        return { ready:true, user:null, mode:'login', page:'home', jobs:[], events:[],
          authForm:{}, verifyForm:{}, jobForm:{}, statusNames:{APPROVED:'已认证'},
          filteredJobs:[], date:()=>'', ...globalThis.__portalTestState }
      }`
    },
    transform(source, id) {
      const path = id.replaceAll('\\', '/')
      if (path.endsWith('/views/JobDetail.vue')) return source.replace('const job = ref(null)', 'const job = ref(globalThis.__jobDetailTestState ?? null)').replace('confirming = ref(false)', 'confirming = ref(globalThis.__screeningConfirm ?? false)')
      if (path.endsWith('/views/ApplicantsPage.vue')) return source.replace('const data = ref(null)', 'const data = ref(globalThis.__applicantsTestState ?? null)')
      if (path.endsWith('/views/HistoryPage.vue')) return source.replace('ref({items:[],total:0,page:0})', 'ref(globalThis.__historyTestState ?? {items:[],total:0,page:0})')
    },
  }],
})
try {
  const {default:card} = await server.ssrLoadModule('/src/components/JobCard.vue')
  const cardHtml = await renderToString(createSSRApp(card,{job:{id:'test',title:'测试兼职',category:'活动执行',organization:'测试企业',remaining:3,starts_at:'2099-05-06T09:00:00',duration_minutes:240,pay:180.50,description:'不应在卡片展示的内容',requirements:'不应在卡片展示的要求'}}))
  assert.ok(cardHtml.includes('还需 3 人') && cardHtml.includes('4小时'))
  assert.ok(!cardHtml.includes('180.5') && !cardHtml.includes('不应在卡片'))
  console.log('Job card: summary fields and private detail omission passed')
  const screeningJob = {id:'screening',publisher_id:'company',title:'简历筛选岗位',category:'技术开发',organization:'测试企业',recruitment_mode:'SCREENING',status:'OPEN',applications:3,required_count:1,accepted_count:0,remaining:1,participants:[],application_status:null,starts_at:'2099-05-06T09:00:00',duration_minutes:240,pay:180,pay_unit:'TOTAL',description:'工作内容',requirements:'工作要求'}
  const screeningCard = await renderToString(createSSRApp(card,{job:screeningJob}))
  for (const label of ['需简历筛选','已报名','需要人数','已录取','3 人','1 人','0 人']) assert.ok(screeningCard.includes(label),label)
  const {default:publishForm} = await server.ssrLoadModule('/src/components/JobPublishForm.vue')
  const publishHtml = await renderToString(createSSRApp(publishForm,{form:{recruitmentMode:'SCREENING'}}))
  assert.ok(publishHtml.includes('企业录取后才能参加') && publishHtml.includes('报名人数可超过需要人数'))
  const {default:detail} = await server.ssrLoadModule('/src/views/JobDetail.vue')
  const student = {id:'student',role:'STUDENT'}
  globalThis.__jobDetailTestState = {...screeningJob,application_status:'PENDING',applied:true}
  const pendingHtml = await renderToString(createSSRApp(detail,{jobId:'screening',user:student,canAct:true}))
  assert.ok(pendingHtml.includes('等待企业筛选') && !pendingHtml.includes('提交简历报名</button>'))
  assert.ok(!pendingHtml.includes('你已加入该兼职') && pendingHtml.includes('撤回报名'))
  globalThis.__jobDetailTestState = screeningJob; globalThis.__screeningConfirm = true
  const resumeHtml = await renderToString(createSSRApp(detail,{jobId:'screening',user:student,canAct:true}))
  assert.ok(resumeHtml.includes('在线简历') && resumeHtml.includes('确认提交简历') && resumeHtml.includes('maxlength="8000"'))
  globalThis.__screeningConfirm = false
  const {default:applicants} = await server.ssrLoadModule('/src/views/ApplicantsPage.vue')
  const person = {id:'student',application_id:'application',application_status:'PENDING',display_name:'报名同学',resume:'Java技能 <script>alert(1)</script>',accepted_at:'2026-10-06T09:00:00'}
  globalThis.__applicantsTestState = {job:screeningJob,items:[person],total:1,page:0}
  const applicantHtml = await renderToString(createSSRApp(applicants,{jobId:'screening'}))
  assert.ok(applicantHtml.includes('查看在线简历') && applicantHtml.includes('不录取</button>') && applicantHtml.includes('录取</button>'))
  assert.ok(!applicantHtml.includes('发放兼职费</button>'), 'Pending applicants cannot receive payment')
  assert.ok(applicantHtml.includes('&lt;script&gt;') && !applicantHtml.includes('<script>alert'))
  globalThis.__applicantsTestState = {job:{...screeningJob,accepted_count:1},items:[{...person,application_status:'ACTIVE'}],total:1,page:0}
  assert.ok((await renderToString(createSSRApp(applicants,{jobId:'screening'}))).includes('发放兼职费</button>'))
  const {default:history} = await server.ssrLoadModule('/src/views/HistoryPage.vue')
  globalThis.__historyTestState = {items:[{...screeningJob,application_id:'application',application_status:'PENDING',created_at:'2026-10-06T09:00:00'}],total:1,page:0}
  assert.ok((await renderToString(createSSRApp(history,{user:student}))).includes('简历待筛选'))
  globalThis.__historyTestState.items[0].application_status = 'REJECTED'
  assert.ok((await renderToString(createSSRApp(history,{user:student}))).includes('未录取'))
  console.log('Resume screening: counts, publishing, pending state, resume form, owner decisions, payment controls and history passed')
  for (const [name, role] of [['Publisher', 'PUBLISHER'], ['Student', 'STUDENT']]) {
    const { default: component } = await server.ssrLoadModule(`/src/pages/${name.toLowerCase()}/${name}App.vue`)
    async function render(state) { globalThis.__portalTestState = state; return renderToString(createSSRApp(component)) }
    const login = await render({})
    assert.ok(login.includes(role + ' ACCESS'))
    assert.ok(!login.includes('verification-form'))
    const user = { id:'test', role, display_name:'测试用户', verification_status:'UNVERIFIED' }
    const verify = await render({ user, page:'verify' })
    assert.ok(verify.includes('verification-form'))
    assert.ok(verify.includes(role === 'PUBLISHER' ? '单位名称' : '学校名称'))
    assert.equal(verify.includes('身份证号码'), role === 'PUBLISHER')
    assert.equal(verify.includes('学校名称<input'), role === 'STUDENT')
    assert.ok(!verify.includes('PUBLISHER ACCESS') && !verify.includes('STUDENT ACCESS'))
    const approved = await render({ user:{...user,verification_status:'APPROVED'}, page:'verify', verified:true })
    assert.ok(!approved.includes('verification-form'), 'Approved users should see review status, not the form')
    const home = await render({ user, canAct:true })
    assert.equal(home.includes('发布新岗位'), role === 'PUBLISHER')
    assert.equal(home.includes('mobile-nav'), role === 'STUDENT')
    console.log(`${name}: login, verification, approved status and dashboard passed`)
  }
} finally {
  delete globalThis.__portalTestState
  delete globalThis.__jobDetailTestState
  delete globalThis.__screeningConfirm
  delete globalThis.__applicantsTestState
  delete globalThis.__historyTestState
  await server.close()
}
