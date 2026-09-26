<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { Sprout, ArrowUpRight, ArrowRight, LayoutDashboard, BriefcaseBusiness, ShieldCheck, LogOut, Bell, Building2, GraduationCap, Smartphone, Check, MapPin, Clock3, Plus, RefreshCw, ChevronRight, LockKeyhole, CircleHelp, X } from 'lucide-vue-next'
import { api } from './api'
import ProfileCenter from './views/ProfileCenter.vue'
import UserHome from './views/UserHome.vue'
import HistoryPage from './views/HistoryPage.vue'
import ApplicantsPage from './views/ApplicantsPage.vue'
import WalletPage from './views/WalletPage.vue'
import UserAvatar from './components/UserAvatar.vue'

const role = ref(location.pathname.startsWith('/student') ? 'STUDENT' : 'PUBLISHER')
const publisher = computed(() => role.value === 'PUBLISHER')
const user = ref(null), ready = ref(false), dev = ref(false), page = ref('home'), mode = ref('login')
const busy = ref(false), sending = ref(false), notice = ref(''), error = ref(''), codeHint = ref(''), cooldown = ref(0), jobs = ref([]), events = ref([])
const authForm = reactive({ phone: '', code: '', password: '' })
const verifyForm = reactive({ organization: '', surname: '', name: '', identityNumber: '', school: '', studentNumber: '', email: '' })
const jobForm = reactive({ title: '', description: '', location: '', pay: 120 })
const showJob = ref(false), search = ref('')
const statusNames = { UNVERIFIED: '未实名认证', PENDING: '审核中', APPROVED: '已认证', REJECTED: '审核未通过' }
const verified = computed(() => user.value?.verification_status === 'APPROVED')
const canAct = computed(() => verified.value && (publisher.value ? user.value?.can_publish : user.value?.can_accept) === 1)
const filteredJobs = computed(() => jobs.value.filter(j => (j.title + j.location + j.organization).includes(search.value)))
const pending = computed(() => user.value?.verification_status === 'PENDING')
const profileUserId = ref(''), selectedJobId = ref(''), homeReturn = ref('profile')
const pageTitle = computed(() => ({ profile:'个人中心', userHome:'个人主页', history:publisher.value?'岗位发布记录':'兼职接取历史', applicants:'岗位接取人', wallet:publisher.value?'企业结算钱包':'我的钱包', verify:'实名认证', jobs:publisher.value?'岗位管理':'我的领取' }[page.value] || (publisher.value?'今天，也有新的可能。':'发现值得出发的机会。')))
function openUser(id) { homeReturn.value=page.value;profileUserId.value=id;navigate('userHome') }
function openApplicants(id) { selectedJobId.value=id;navigate('applicants') }
function sessionExpired() { user.value=null;clearDrafts();error.value='登录已过期，请重新登录' }
let timer, poll
async function run(action) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await action() } catch (e) { error.value = e.message; if (e.status === 401 && user.value) user.value = null } finally { busy.value = false }
}
async function loadUser() {
  user.value = await api('/me'); role.value = user.value.role
  history.replaceState({}, '', publisher.value ? '/publisher' : '/student')
}
async function loadJobs() { jobs.value = await api('/jobs') }
function clearDrafts() {
  Object.keys(verifyForm).forEach(k => verifyForm[k] = '')
  Object.assign(jobForm, { title: '', description: '', location: '', pay: 120 })
  showJob.value = false; search.value = ''; authForm.password = ''; authForm.code = ''; codeHint.value = ''
}
async function refresh() { await loadUser(); await loadJobs(); if (page.value === 'verify') events.value = await api('/verification/events') }
async function switchRole(next) {
  if (role.value === next) return
  await run(async () => {
    if (user.value) await api('/auth/logout', { method: 'POST' })
    user.value = null; jobs.value = []; events.value = []; role.value = next; page.value = 'home'; clearDrafts()
    history.replaceState({}, '', next === 'PUBLISHER' ? '/publisher' : '/student')
  })
}
async function sendCode() {
  if (sending.value || cooldown.value) return
  if (!/^1[3-9]\d{9}$/.test(authForm.phone)) { error.value = '请输入正确的11位手机号'; return }
  sending.value = true; error.value = ''
  try {
    const result = await api('/auth/code', { method: 'POST', body: { role: role.value, phone: authForm.phone } })
    codeHint.value = result.debugCode || ''; notice.value = result.message; cooldown.value = 60
  } catch (e) { error.value = e.message } finally { sending.value = false }
}
function submitAuth() {
  run(async () => {
    if (mode.value === 'register') {
      await api('/auth/register', { method: 'POST', body: { ...authForm, role: role.value } })
      mode.value = 'login'; authForm.code = ''; codeHint.value = ''; notice.value = '注册成功！账号默认为手机号，请登录。'
    } else {
      await api('/auth/login', { method: 'POST', body: { ...authForm, role: role.value } }); authForm.password = ''; await refresh()
    }
  })
}
function logout() { run(async () => { await api('/auth/logout', { method: 'POST' }); user.value = null; jobs.value = []; events.value = []; page.value = 'home'; clearDrafts() }) }
function navigate(next) { page.value = next; window.scrollTo({ top: 0, behavior: 'smooth' }); if (next === 'verify') run(async () => { events.value = await api('/verification/events') }) }
function submitVerification() {
  run(async () => {
    await api('/verification', { method: 'POST', body: verifyForm }); await refresh()
    Object.keys(verifyForm).forEach(k => verifyForm[k] = ''); notice.value = '资料提交成功，审核通过后将开放操作权限。'
  })
}
function mockReview(approved) { run(async () => { await api('/dev/review', { method: 'POST', body: { approved, reviewId: user.value.review_id } }); await refresh() }) }
function publish() { run(async () => { await api('/jobs', { method: 'POST', body: jobForm }); showJob.value = false; Object.assign(jobForm, { title: '', description: '', location: '', pay: 120 }); await refresh(); notice.value = '岗位发布成功，领取端主页已可查看。' }) }
function apply(job) { run(async () => { await api(`/jobs/${job.id}/apply`, { method: 'POST' }); await loadJobs(); notice.value = '领取成功，可在“我的领取”中查看。' }) }
function date(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无记录' }
onMounted(async () => {
  try { dev.value = (await api('/config')).development; await refresh() } catch (e) { if (e.status !== 401) error.value = e.message } finally { ready.value = true }
  timer = setInterval(() => { if (cooldown.value > 0) cooldown.value-- }, 1000)
  poll = setInterval(async () => { if (user.value && document.visibilityState === 'visible' && !busy.value) { try { await refresh() } catch (e) { if (e.status === 401) user.value = null } } }, 15000)
})
onUnmounted(() => { clearInterval(timer); clearInterval(poll) })
</script>

<template>
  <div class="app-shell" :class="{ 'student-shell': !publisher }">
    <aside class="sidebar">
      <a class="brand" href="/publisher"><span class="brand-icon"><Sprout :size="25" /></span><span>贝鱼<span class="brand-sub">校园兼职</span></span></a>
      <div class="workspace-label">{{ publisher ? '企业工作空间' : '学生成长空间' }}</div>
      <nav>
        <button v-if="user" :class="{ active: page === 'profile' }" @click="navigate('profile')"><UserAvatar :src="user.avatar_url" :name="user.display_name" :size="20" />个人中心</button>
        <button v-if="user" :class="{ active: page === 'history' || page === 'applicants' }" @click="navigate('history')"><Clock3 :size="19" />{{ publisher ? '发布记录' : '接取历史' }}</button>
        <button v-if="user" :class="{ active: page === 'wallet' }" @click="navigate('wallet')"><span style="width:19px;font-size:19px">¥</span>{{ publisher ? '企业钱包' : '我的钱包' }}</button>
        <button :class="{ active: page === 'home' }" @click="navigate('home')"><LayoutDashboard :size="19" />{{ publisher ? '工作台' : '发现兼职' }}<span class="nav-dot" /></button>
        <button :class="{ active: page === 'jobs' }" @click="navigate('jobs')"><BriefcaseBusiness :size="19" />{{ publisher ? '岗位管理' : '我的领取' }}</button>
        <button :class="{ active: page === 'verify' }" @click="user ? navigate('verify') : error = '请先登录，再进行实名认证'"><ShieldCheck :size="19" />实名认证<span v-if="user && !verified" class="tiny-dot" /></button>
      </nav>
      <div class="side-note"><div class="note-sprout"><Sprout :size="30" /></div><strong>让每一份努力，都有回响</strong><p>连接校园与机会<br>从一份安心的兼职开始。</p><span>GROW TOGETHER <ArrowUpRight :size="14" /></span></div>
      <div class="side-footer"><ShieldCheck :size="16" /> 实名保障 · 安心连接</div>
    </aside>

    <div class="main-wrap">
      <header class="topbar"><div class="breadcrumb">贝鱼校园 <ChevronRight :size="14" /> <span>{{ publisher ? '发布端' : '领取端' }}</span></div><div class="top-actions"><div class="role-switch"><button :class="{ selected: publisher }" :disabled="busy" @click="switchRole('PUBLISHER')">发布端</button><button :class="{ selected: !publisher }" :disabled="busy" @click="switchRole('STUDENT')"><Smartphone :size="14" />领取端</button></div><span class="top-divider"></span><button v-if="user" class="icon-button" aria-label="刷新数据" @click="run(refresh)"><RefreshCw :size="18" /></button><button v-if="user" class="personal-header-button" aria-label="个人中心" @click="navigate('profile')"><UserAvatar :src="user.avatar_url" :name="user.display_name" :size="32" /><span class="top-account">{{ user.display_name }}</span></button><span v-else class="avatar">{{ publisher ? '企' : '学' }}</span><button v-if="user" class="icon-button" aria-label="退出登录" @click="logout"><LogOut :size="17" /></button></div></header>
      <main>
        <div v-if="dev" class="dev-label">本地演示环境 · 验证码可见 · 审核为开发模拟</div>
        <div v-if="error" class="alert error" role="alert">{{ error }}<button aria-label="关闭提示" @click="error = ''"><X :size="16" /></button></div>
        <div v-if="notice" class="alert success" role="status">{{ notice }}<button aria-label="关闭提示" @click="notice = ''"><X :size="16" /></button></div>
        <div v-if="!ready" class="loading">正在连接贝鱼校园…</div>

        <template v-else-if="!user">
          <div class="page-heading"><div><div class="eyebrow">A LITTLE WORK. A BIG BEGINNING.</div><h1>{{ publisher ? '好机会，从这里开始。' : '把课余时光，变成新的可能。' }}</h1><p>{{ publisher ? '连接有活力的校园人才，让每一次招募都更简单。' : '发现身边的靠谱兼职，让成长与收获一起发生。' }}</p></div><span class="outline-tag"><span></span> {{ publisher ? '企业发布中心' : '学生机会中心' }}</span></div>
          <div class="welcome-grid">
            <section class="hero-card"><div class="hero-top"><span class="hero-label">贝鱼 · {{ publisher ? '企业伙伴计划' : '校园成长计划' }}</span><ArrowUpRight :size="22" /></div><h2>{{ publisher ? '你提供机会，' : '走出课堂，' }}<br>{{ publisher ? '我们连接新生力量。' : '遇见更好的自己。' }}</h2><p>{{ publisher ? '从一份兼职开始，让年轻的想法\n与真实的工作双向奔赴。' : '灵活的时间、真实的岗位，\n每一小步，都算成长。' }}</p><div class="hero-art" aria-hidden="true"><div class="art-orbit orbit-one"></div><div class="art-orbit orbit-two"></div><div class="art-card card-back"><span class="art-mini-line"></span><span class="art-mini-line short"></span><div class="art-bars"><i></i><i></i><i></i><i></i></div></div><div class="art-card card-front"><span class="art-icon"><Sprout :size="40" /></span><strong>新的机会，正在萌芽</strong><small>YOUR NEXT CHAPTER</small><div class="art-check"><Check :size="16" /> 实名认证 · 安心同行</div></div><span class="floating-plus">+</span><span class="floating-star">✳</span></div><div class="hero-bottom"><span class="hero-avatars"><i>贝</i><i>鱼</i><i>＋</i></span><span>连接每一份值得期待的可能</span><ArrowRight :size="18" /></div></section>
            <section class="auth-card"><span class="card-kicker">{{ publisher ? 'PUBLISHER ACCESS' : 'STUDENT ACCESS' }}</span><h2>{{ mode === 'login' ? '欢迎回来' : '加入贝鱼校园' }}<span class="greeting-dot">.</span></h2><p>{{ mode === 'login' ? '登录账号，开启今天的新连接' : '手机号注册，让机会从此触手可及' }}</p><div class="auth-tabs"><button :class="{ active: mode === 'login' }" @click="mode = 'login'; error = ''">账号登录</button><button :class="{ active: mode === 'register' }" @click="mode = 'register'; error = ''">手机号注册</button></div><form @submit.prevent="submitAuth"><label>手机号 <input v-model.trim="authForm.phone" type="tel" inputmode="numeric" autocomplete="username" placeholder="请输入11位手机号码" pattern="1[3-9][0-9]{9}" maxlength="11" required /></label><label v-if="mode === 'register'">短信验证码<div class="code-input"><input v-model.trim="authForm.code" inputmode="numeric" autocomplete="one-time-code" placeholder="6位验证码" pattern="[0-9]{6}" maxlength="6" required /><button type="button" :disabled="sending || cooldown > 0" @click="sendCode">{{ cooldown ? cooldown + '秒后重试' : sending ? '发送中…' : '获取验证码' }}</button></div></label><div v-if="codeHint && mode === 'register'" class="debug-code">开发验证码：<strong>{{ codeHint }}</strong>（非真实短信）</div><label>密码<input v-model="authForm.password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" :placeholder="mode === 'register' ? '8–64位，包含字母和数字' : '请输入登录密码'" :minlength="mode === 'register' ? 8 : 1" maxlength="64" required /></label><div class="form-info"><LockKeyhole :size="13" /> 账号信息加密保护</div><button class="primary auth-submit" :disabled="busy">{{ busy ? '请稍候…' : mode === 'login' ? '登 录' : '注 册' }}<ArrowRight :size="17" /></button></form><div class="auth-foot"><ShieldCheck :size="16" /><span>注册后完成实名认证，即可{{ publisher ? '发布岗位' : '领取兼职' }}</span></div></section>
          </div>
          <section class="steps-row"><article><span class="step-icon"><Smartphone :size="22" /></span><div><small>01 / 创建账号</small><h3>手机号快捷注册</h3><p>一个手机号，开启新的连接</p></div></article><article><span class="step-icon"><ShieldCheck :size="22" /></span><div><small>02 / 实名认证</small><h3>{{ publisher ? '认证企业与发布人' : '认证你的学生身份' }}</h3><p>真实身份，让每一次选择更安心</p></div></article><article><span class="step-icon"><BriefcaseBusiness :size="22" /></span><div><small>03 / 开始连接</small><h3>{{ publisher ? '发布你的第一份岗位' : '领取你的第一份兼职' }}</h3><p>审核通过，解锁更多可能</p></div></article></section>
        </template>

        <template v-else>
          <div class="page-heading"><div><div class="eyebrow">YOUR CAMPUS, YOUR OPPORTUNITIES</div><h1>{{ pageTitle }}</h1><p>{{ page === 'verify' ? '真实的身份，是每一份信任的开始。' : '欢迎来到贝鱼，让每一份努力都被看见。' }}</p></div><span class="status-pill" :class="{ approved: verified }"><ShieldCheck :size="16" />{{ statusNames[user.verification_status] }}</span></div>
          <ProfileCenter v-if="page === 'profile'" :key="user.id" :user="user" @updated="user=$event" @home="openUser" @history="navigate('history')" @wallet="navigate('wallet')" @expired="sessionExpired" />
          <UserHome v-else-if="page === 'userHome'" :user-id="profileUserId" :own-id="user.id" @back="navigate(homeReturn)" @edit="navigate('profile')" @expired="sessionExpired" />
          <HistoryPage v-else-if="page === 'history'" :key="user.id" :user="user" @applicants="openApplicants" @home="openUser" @expired="sessionExpired" />
          <ApplicantsPage v-else-if="page === 'applicants'" :key="selectedJobId" :job-id="selectedJobId" @back="navigate('history')" @home="openUser" @wallet="navigate('wallet')" @expired="sessionExpired" />
          <WalletPage v-else-if="page === 'wallet'" :key="user.id" :user="user" @expired="sessionExpired" />
          <template v-else-if="page === 'verify'">
            <div class="verification-layout"><section class="panel verification-panel"><div class="section-title"><h2>{{ publisher ? '发布人身份认证' : '学生身份认证' }}</h2><span> {{ publisher ? '企业 / 单位' : '在校学生' }} </span></div><div class="progress-steps"><span class="done">1 填写资料</span><i></i><span :class="{ done: pending || verified }">2 资料审核</span><i></i><span :class="{ done: verified }">3 开通权限</span></div><div v-if="pending || verified" class="review-state"><span class="review-icon"><ShieldCheck v-if="verified" :size="42" /><Clock3 v-else :size="42" /></span><h2>{{ verified ? '身份认证已通过' : '你的资料已提交' }}</h2><p>{{ user.review_note }}</p><p v-if="pending">审核结果更新后，将在此处展示。</p><button class="secondary" :disabled="busy" @click="run(refresh)"><RefreshCw :size="15" />刷新状态</button><div v-if="dev && pending" class="dev-review"><strong>开发测试工具 · 非真实 AI 审核</strong><p>仅用于验证通过、驳回与权限控制流程。</p><button class="secondary" :disabled="busy" @click="mockReview(true)">模拟通过</button><button class="secondary" :disabled="busy" @click="mockReview(false)">模拟驳回</button></div></div><form v-else class="verification-form" @submit.prevent="submitVerification"><div v-if="user.verification_status === 'REJECTED'" class="rejection">{{ user.review_note }}</div><template v-if="publisher"><label>单位名称<input v-model.trim="verifyForm.organization" placeholder="请输入单位完整名称" maxlength="150" required /></label><div class="field-grid"><label>姓氏<input v-model.trim="verifyForm.surname" placeholder="如：张" maxlength="40" required /></label><label>名字<input v-model.trim="verifyForm.name" placeholder="如：明" maxlength="80" required /></label></div><label>身份证号码<input v-model.trim="verifyForm.identityNumber" placeholder="请输入18位身份证号码" maxlength="18" pattern="[1-9][0-9]{16}[0-9Xx]" required /></label></template><template v-else><label>学校名称<input v-model.trim="verifyForm.school" placeholder="请输入学校完整名称" maxlength="150" required /></label><label>真实姓名<input v-model.trim="verifyForm.name" placeholder="请输入你的真实姓名" maxlength="80" required /></label><label>学号<input v-model.trim="verifyForm.studentNumber" placeholder="请输入学校学号" maxlength="50" required /></label></template><label>邮箱 <span class="optional">选填</span><input v-model.trim="verifyForm.email" type="email" placeholder="用于后续联系" maxlength="254" /></label><p class="privacy-note"><LockKeyhole :size="16" />姓名、证件号码、学号等敏感资料加密保存，提交后不回显原文。</p><button class="primary" :disabled="busy">{{ busy ? '正在提交…' : '提交认证资料' }}<ArrowRight :size="17" /></button></form></section><aside class="panel info-panel"><span class="step-icon"><ShieldCheck :size="25" /></span><h3>为每一次连接，多一份保障</h3><p>请填写本人真实资料。{{ publisher ? '单位名称请与实际任职单位一致。' : '学校名称和学号请与在校信息一致。' }}</p><ul><li>提交后进入待审核状态</li><li>审核通过后开放{{ publisher ? '发布' : '领取' }}权限</li><li>审核未通过可修改并重新提交</li></ul><div class="integration-note">审核服务尚待接入，正式环境不会自动通过。</div><h4>认证记录</h4><div v-if="!events.length" class="muted">暂无提交记录</div><div v-for="event in events" :key="event.id" class="event"><strong>{{ statusNames[event.status] }}</strong><small>{{ date(event.created_at) }}</small><p>{{ event.note }}</p></div></aside></div>
          </template>
          <template v-else>
            <div v-if="!canAct" class="verify-banner"><span class="banner-icon"><ShieldCheck :size="28" /></span><div><h3>{{ pending ? '认证审核中，请耐心等待' : verified ? '当前操作权限尚未开放' : '完成实名认证，开启更多机会' }}</h3><p>你可以浏览岗位，{{ publisher ? '发布' : '领取' }}前需通过身份审核并开通权限。</p></div><button class="secondary" @click="navigate('verify')">{{ pending ? '查看进度' : '查看认证' }}<ArrowUpRight :size="16" /></button></div>
            <div class="stats-grid"><article class="panel stat"><span>{{ publisher ? '已发布岗位' : '可浏览岗位' }}</span><strong>{{ jobs.length.toString().padStart(2, '0') }}<BriefcaseBusiness :size="23" /></strong><small>{{ publisher ? '你发布的兼职机会' : '最新100条兼职机会' }}</small></article><article class="panel stat"><span>{{ publisher ? '岗位领取人次' : '已领取岗位' }}</span><strong>{{ (publisher ? jobs.reduce((n,j) => n + j.applications, 0) : jobs.filter(j => j.applied).length).toString().padStart(2, '0') }}<Check :size="23" /></strong><small>每一份连接，都是新的开始</small></article><article class="panel stat"><span>账号状态</span><strong class="status-text">{{ statusNames[user.verification_status] }}<ShieldCheck :size="23" /></strong><small>上次登录：{{ date(user.last_login_at) }}</small></article></div>
            <section class="panel jobs-panel"><div class="section-title"><div><h2>{{ publisher ? '我的岗位' : page === 'jobs' ? '我的领取' : '最新兼职' }}</h2><p>{{ publisher ? '管理发布的机会，等待新生力量加入。' : '每15秒自动更新，让好机会及时抵达。' }}</p></div><button v-if="publisher" class="primary" @click="canAct ? showJob = true : navigate('verify')"><Plus :size="17" />发布新岗位</button><button v-else class="secondary" :disabled="busy" @click="run(refresh)"><RefreshCw :size="16" />刷新</button></div><input v-model="search" class="search" placeholder="搜索岗位、单位或地点" aria-label="搜索岗位" /><div class="job-list"><article v-for="job in filteredJobs.filter(j => publisher || page !== 'jobs' || j.applied)" :key="job.id" class="job-card"><div class="job-top"><span class="company-icon"><Building2 :size="24" /></span><span class="job-pay">¥{{ job.pay }}<small> / 天</small></span></div><h3>{{ job.title }}</h3><p class="company-name"><button class="user-home-trigger" @click="openUser(job.publisher_id)">{{ job.organization || '企业主页' }} ↗</button></p><p class="job-description">{{ job.description }}</p><div class="job-location"><MapPin :size="14" />{{ job.location }}</div><div class="job-bottom"><span v-if="publisher">{{ job.applications }} 人已领取</span><span v-else>{{ job.applied ? '已成功领取' : '实名岗位' }}</span><button v-if="!publisher" class="text-button" :disabled="busy || !!job.applied" @click="canAct ? apply(job) : navigate('verify')">{{ job.applied ? '已领取' : canAct ? '领取岗位' : '去认证' }}<ArrowRight :size="15" /></button><button v-else class="text-button" @click="openApplicants(job.id)">查看接取人 →</button></div></article></div><div v-if="!filteredJobs.filter(j => publisher || page !== 'jobs' || j.applied).length" class="empty-state"><BriefcaseBusiness :size="38" /><h3>{{ search ? '没有找到匹配的岗位' : publisher ? '第一个好机会，等你发布' : page === 'jobs' ? '还没有领取兼职' : '新的机会正在路上' }}</h3><p>{{ search ? '试试其他关键词。' : publisher ? '完成认证后，点击“发布新岗位”开始招募。' : '发布端发布岗位后，会在这里显示。' }}</p></div></section>
          </template>
        </template>
        <footer class="page-footer"><span>© {{ new Date().getFullYear() }} 夏倪 · 让成长有迹可循</span><span><LockKeyhole :size="13" /> 我们将严格按照《中华人民共和国个人信息保护法》及相关法律法规的要求处理您的个人信息。您的个人信息将被认真保密，我们不会将其用于与您使用本服务无关的目的。 </span></footer>
      </main>
    </div>
    <nav v-if="!publisher && user" class="mobile-nav"><button @click="navigate('home')" :class="{ active: page === 'home' }"><LayoutDashboard :size="21" />发现</button><button @click="navigate('history')" :class="{ active: page === 'history' }"><BriefcaseBusiness :size="21" />接取记录</button><button @click="navigate('wallet')" :class="{ active: page === 'wallet' }"><span style="font-size:20px;line-height:21px">¥</span>钱包</button><button @click="navigate('profile')" :class="{ active: page === 'profile' }"><UserAvatar :src="user.avatar_url" :name="user.display_name" :size="21" />我的</button><button @click="navigate('verify')" :class="{ active: page === 'verify' }"><ShieldCheck :size="21" />认证</button></nav>
    <div v-if="showJob" class="modal-backdrop" @click.self="showJob = false"><section class="panel modal" role="dialog" aria-modal="true" aria-label="发布新岗位"><div class="section-title"><h2>发布新岗位</h2><button class="icon-button" aria-label="关闭" @click="showJob = false"><X :size="20" /></button></div><form @submit.prevent="publish"><label>岗位名称<input v-model.trim="jobForm.title" maxlength="100" required placeholder="如：周末活动助理" /></label><label>工作地点<input v-model.trim="jobForm.location" maxlength="150" required placeholder="如：大学城文化中心" /></label><label>日薪（元）<input v-model.number="jobForm.pay" type="number" min="1" max="100000" required /></label><label>岗位说明<textarea v-model.trim="jobForm.description" maxlength="2000" rows="4" required placeholder="描述工作内容、时间和要求"></textarea></label><button class="primary" :disabled="busy">{{ busy ? '发布中…' : '确认发布' }}<ArrowUpRight :size="17" /></button></form></section></div>
  </div>
</template>
