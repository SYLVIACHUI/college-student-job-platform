<script setup>
import {ref,onMounted,nextTick} from 'vue'
import {payUnit} from '../jobFormat'
import {api} from '../api'
import {newRequestKey} from '../requestKey'
import UserAvatar from '../components/UserAvatar.vue'
const props=defineProps({jobId:String})
const emit=defineEmits(['back','home','wallet','expired'])
const data=ref(null),balance=ref(0),mode=ref('DISABLED'),busy=ref(false),error=ref(''),success=ref(''),selected=ref(null),amount=ref(''),requestKey=ref('')
const paymentPanel=ref(null)
const money=cents=>((cents||0)/100).toFixed(2)
async function load(page=0){const [applicants,wallet]=await Promise.all([api(`/jobs/${props.jobId}/applicants?page=${page}`),api('/wallet')]);data.value=applicants;balance.value=wallet.balance_cents;mode.value=wallet.mode}
async function action(fn){if(busy.value)return;busy.value=true;error.value='';try{await fn()}catch(e){error.value=e.message;if(e.status===401)emit('expired')}finally{busy.value=false}}
async function choose(person){selected.value=person;amount.value=String(data.value.job.pay);requestKey.value=newRequestKey();error.value='';success.value='';await nextTick();paymentPanel.value?.scrollIntoView({behavior:'smooth',block:'center'})}
async function pay(){action(async()=>{await api(`/applications/${selected.value.application_id}/payment`,{method:'POST',body:{amount:amount.value,requestKey:requestKey.value}});selected.value=null;success.value='兼职费已从企业模拟余额转入学生钱包';await load(data.value.page)})}
onMounted(()=>action(()=>load()))
</script>
<template>
  <section class="panel personal-card"><button class="text-button" @click="emit('back')">← 返回发布记录</button><div v-if="data" class="section-title record-heading"><div><h2>{{data.job.title}}</h2><p>{{data.job.location}} · ¥{{data.job.pay}} / 人 / {{payUnit(data.job.pay_unit)}} · {{data.total}} 人接取</p></div><button class="secondary" @click="emit('wallet')">企业钱包 ¥{{money(balance)}}</button></div><p class="simulation-banner">模拟资金结算，不涉及真实扣款或到账。每条接取记录支持一次结算，请根据岗位约定的计价方式填写结算总额。</p><p v-if="error" class="alert error" role="alert">{{error}}</p><p v-if="success" class="alert success" role="status">{{success}}</p><div v-if="!data" class="empty-state">{{busy?'加载中…':'暂时无法加载'}}<button v-if="!busy" class="secondary" @click="action(()=>load())">重试</button></div><template v-else><div v-if="!data.items.length" class="empty-state">暂时还没有人接取这个岗位</div><article v-for="person in data.items" :key="person.application_id" class="applicant-row"><button class="person-link" @click="emit('home',person.id)"><UserAvatar :src="person.avatar_url" :name="person.display_name"/><span><strong>{{person.display_name}}</strong><small>{{[person.grade,person.major].filter(Boolean).join(' · ') || '查看个人主页'}} ↗</small></span></button><div class="private-identity"><strong>仅本岗位发布者可见</strong><div>姓名：{{person.real_name || "未填写"}}</div><div>学号：{{person.student_number || "未填写"}}</div><div>学校：{{person.school || "未填写"}}</div></div><div class="history-actions"><small>{{new Date(person.accepted_at).toLocaleString('zh-CN')}} 接取</small><span v-if="person.paid_cents" class="status-pill approved">已发放 ¥{{money(person.paid_cents)}}</span><span v-else-if="person.application_status==='WITHDRAWN'" class="status-pill">已退出</span><span v-else-if="data.job.status==='CANCELLED'" class="status-pill">活动已取消</span><button v-else class="primary" :disabled="busy||mode!=='SIMULATED'" @click="choose(person)">发放兼职费</button></div></article><div class="pagination"><button class="secondary" :disabled="busy||data.page===0" @click="action(()=>load(data.page-1))">上一页</button><span>{{data.page+1}} / {{Math.max(1,Math.ceil(data.total/20))}}</span><button class="secondary" :disabled="busy||(data.page+1)*20>=data.total" @click="action(()=>load(data.page+1))">下一页</button></div></template>
    <div v-if="selected" ref="paymentPanel" class="payment-box"><h3>向 {{selected.display_name}} 发放模拟兼职费</h3><form @submit.prevent="pay"><label>结算总额（元）<input v-model="amount" type="number" min="0.01" max="100000" step="0.01" required :disabled="busy" @input="requestKey=newRequestKey()" /></label><p class="muted">企业可用模拟余额：¥{{money(balance)}}，不足时请先去企业钱包模拟充值。</p><div class="button-row"><button class="primary" :disabled="busy">{{busy?'处理中…':'确认模拟发放'}}</button><button type="button" class="secondary" :disabled="busy" @click="selected=null">取消</button></div></form></div>
  </section>
</template>
