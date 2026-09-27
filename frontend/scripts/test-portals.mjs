import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'

// Render the actual portal templates in representative states, without a live API.
const server = await createServer({
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
  }],
})
try {
  const {default:card} = await server.ssrLoadModule('/src/components/JobCard.vue')
  const cardHtml = await renderToString(createSSRApp(card,{job:{id:'test',title:'测试兼职',category:'活动执行',organization:'测试企业',remaining:3,starts_at:'2099-05-06T09:00:00',duration_minutes:240,pay:180.50,description:'不应在卡片展示的内容',requirements:'不应在卡片展示的要求'}}))
  assert.ok(cardHtml.includes('还需 3 人') && cardHtml.includes('4小时'))
  assert.ok(!cardHtml.includes('180.5') && !cardHtml.includes('不应在卡片'))
  console.log('Job card: summary fields and private detail omission passed')
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
  await server.close()
}
