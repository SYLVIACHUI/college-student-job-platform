<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { payUnit } from '../jobFormat'
import { api } from '../api'
import { newRequestKey } from '../requestKey'
import UserAvatar from '../components/UserAvatar.vue'
const props = defineProps({ jobId: String })
const emit = defineEmits(['back', 'home', 'wallet', 'expired'])
const data = ref(null), balance = ref(0), mode = ref('DISABLED'), busy = ref(false), error = ref(''), success = ref('')
const selected = ref(null), amount = ref(''), requestKey = ref(''), paymentPanel = ref(null)
const reviewTarget = ref(null), reviewAccept = ref(true)
const screening = computed(() => data.value?.job.recruitment_mode === 'SCREENING')
const started = computed(() => data.value?.job.starts_at && new Date(data.value.job.starts_at).getTime() <= Date.now())
const names = { PENDING: '待筛选', ACTIVE: '已录取', REJECTED: '未录取', WITHDRAWN: '已退出' }
const money = cents => ((cents || 0) / 100).toFixed(2)
async function load(page = 0) {
  const [applicants, wallet] = await Promise.all([api(`/jobs/${props.jobId}/applicants?page=${page}`), api('/wallet')])
  data.value = applicants; balance.value = wallet.balance_cents; mode.value = wallet.mode
}
async function action(fn) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await fn() } catch (e) { error.value = e.message; if (e.status === 401) emit('expired') } finally { busy.value = false }
}
async function choose(person) {
  selected.value = person; amount.value = String(data.value.job.pay); requestKey.value = newRequestKey(); error.value = ''; success.value = ''
  await nextTick(); paymentPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
async function pay() {
  await action(async () => {
    await api(`/applications/${selected.value.application_id}/payment`, { method: 'POST', body: { amount: amount.value, requestKey: requestKey.value } })
    selected.value = null; success.value = '兼职费已从企业模拟余额转入学生钱包'; await load(data.value.page)
  })
}
function review(person, accepted) { reviewTarget.value = person; reviewAccept.value = accepted; error.value = ''; success.value = '' }
async function decide() {
  await action(async () => {
    await api(`/jobs/${props.jobId}/applications/${reviewTarget.value.application_id}/decision`, { method: 'POST', body: { accepted: reviewAccept.value } })
    success.value = reviewAccept.value ? '已录取该学生，已发送录取通知' : '已标记为未录取，已通知学生'
    reviewTarget.value = null; await load(data.value.page)
  })
}
onMounted(() => action(() => load()))
</script>
<template>
  <section class="panel personal-card">
    <button class="text-button" @click="emit('back')">← 返回发布记录</button>
    <div v-if="data" class="section-title record-heading">
      <div><h2>{{ data.job.title }}</h2><p>{{ data.job.location }} · ¥{{ data.job.pay }} / 人 / {{ payUnit(data.job.pay_unit) }}</p></div>
      <button class="secondary" @click="emit('wallet')">企业钱包 ¥{{ money(balance) }}</button>
    </div>
    <template v-if="data">
      <span v-if="screening" class="status-pill screening-tag">简历筛选岗位</span>
      <dl class="enrollment-counts">
        <div><dt>已报名人数</dt><dd>{{ data.job.applications }} 人</dd></div>
        <div><dt>需要人数</dt><dd>{{ data.job.required_count }} 人</dd></div>
        <div><dt>已录取人数</dt><dd>{{ data.job.accepted_count }} 人</dd></div>
      </dl>
      <p v-if="screening" class="muted">查看在线简历后选择录取或不录取。仅已录取的学生可以参加并结算，待筛选报名不占用名额。已报名人数不包含已退出的报名。</p>
      <button class="text-button" :disabled="busy" @click="action(() => load(data.page))">刷新报名与录取状态</button>
    </template>
    <p class="simulation-banner">模拟资金结算，不涉及真实扣款或到账。每条已录取的报名记录支持一次结算，请按岗位约定填写结算总额。</p>
    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <p v-if="success" class="alert success" role="status">{{ success }}</p>
    <div v-if="!data" class="empty-state">{{ busy ? '加载中…' : '暂时无法加载' }}<button v-if="!busy" class="secondary" @click="action(() => load())">重试</button></div>
    <template v-else>
      <div v-if="!data.items.length" class="empty-state">暂时还没有人报名这个岗位</div>
      <article v-for="person in data.items" :key="person.application_id" class="applicant-row">
        <button class="person-link" @click="emit('home', person.id)">
          <UserAvatar :src="person.avatar_url" :name="person.display_name" />
          <span><strong>{{ person.display_name }}</strong><small>{{ [person.grade, person.major].filter(Boolean).join(' · ') || '查看个人主页' }} ↗</small></span>
        </button>
        <div class="private-identity"><strong>仅本岗位发布者可见</strong><div>姓名：{{ person.real_name || '未填写' }}</div><div>学号：{{ person.student_number || '未填写' }}</div><div>学校：{{ person.school || '未填写' }}</div></div>
        <div class="history-actions">
          <small>{{ new Date(person.accepted_at).toLocaleString('zh-CN') }} 报名</small>
          <span class="status-pill" :class="{approved:person.application_status==='ACTIVE'}">{{ names[person.application_status] }}</span>
          <span v-if="person.paid_cents" class="status-pill approved">已发放 ¥{{ money(person.paid_cents) }}</span>
          <span v-else-if="data.job.status==='CANCELLED'" class="status-pill">活动已取消</span>
          <template v-else-if="screening && person.application_status==='PENDING'">
            <div class="button-row" v-if="!started">
              <button class="primary" :disabled="busy || data.job.accepted_count>=data.job.required_count" @click="review(person,true)">录取</button>
              <button class="secondary" :disabled="busy" @click="review(person,false)">不录取</button>
            </div><small v-else>兼职已开始，已停止筛选</small>
          </template>
          <button v-else-if="person.application_status==='ACTIVE'" class="primary" :disabled="busy || mode!=='SIMULATED'" @click="choose(person)">发放兼职费</button>
        </div>
        <details v-if="screening && person.resume" class="applicant-resume"><summary>查看在线简历 · {{ person.display_name }}</summary><p class="resume-preview">{{ person.resume }}</p></details>
      </article>
      <div class="pagination"><button class="secondary" :disabled="busy || data.page===0" @click="action(() => load(data.page-1))">上一页</button><span>{{ data.page+1 }} / {{ Math.max(1,Math.ceil(data.total/20)) }}</span><button class="secondary" :disabled="busy || (data.page+1)*20>=data.total" @click="action(() => load(data.page+1))">下一页</button></div>
    </template>
    <div v-if="reviewTarget" class="modal-backdrop" @click.self="!busy && (reviewTarget=null)">
      <section class="panel modal" role="dialog" aria-modal="true" aria-label="确认筛选结果">
        <h2>确认{{ reviewAccept ? '录取' : '不录取' }} {{ reviewTarget.display_name }}？</h2>
        <p>{{ reviewAccept ? '录取后占用一个名额，学生将收到录取通知。' : '学生将收到未录取通知，本次报名的筛选结果无法再次修改。' }}</p>
        <p v-if="error" class="alert error" role="alert">{{ error }}</p>
        <div class="button-row"><button class="primary" :disabled="busy" @click="decide">{{ busy ? '处理中…' : '确认结果' }}</button><button class="secondary" :disabled="busy" @click="reviewTarget=null">返回查看简历</button></div>
      </section>
    </div>
    <div v-if="selected" ref="paymentPanel" class="payment-box">
      <h3>向 {{ selected.display_name }} 发放模拟兼职费</h3>
      <form @submit.prevent="pay"><label>结算总额（元）<input v-model="amount" type="number" min="0.01" max="100000" step="0.01" required :disabled="busy" @input="requestKey=newRequestKey()" /></label><p class="muted">企业可用模拟余额：¥{{ money(balance) }}，不足时请先去企业钱包模拟充值。</p><div class="button-row"><button class="primary" :disabled="busy">{{ busy ? '处理中…' : '确认模拟发放' }}</button><button type="button" class="secondary" :disabled="busy" @click="selected=null">取消</button></div></form>
    </div>
  </section>
</template>
