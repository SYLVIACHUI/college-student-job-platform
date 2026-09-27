<script setup>
import { ref, onMounted } from 'vue'
import { payUnit } from '../jobFormat'
import { api } from '../api'
const props=defineProps({user:Object})
const emit=defineEmits(['detail','applicants','home','expired'])
const result=ref({items:[],total:0,page:0}),busy=ref(false),error=ref('')
const money=cents=>((cents||0)/100).toFixed(2)
const date=value=>new Date(value).toLocaleString('zh-CN')
async function load(page=0){busy.value=true;error.value='';try{result.value=await api('/me/history?page='+page)}catch(e){error.value=e.message;if(e.status===401)emit('expired')}finally{busy.value=false}}
onMounted(()=>load())
</script>
<template>
  <section class="panel personal-card"><div class="section-title"><div><h2>{{user.role==='PUBLISHER'?'岗位发布记录':'兼职接取历史'}}</h2><p>共 {{result.total}} 条记录 · 按时间倒序</p></div><button class="secondary" :disabled="busy" @click="load(result.page)">刷新</button></div><p v-if="error" class="alert error">{{error}}</p><div v-if="busy" class="empty-state">加载中…</div><div v-else-if="!result.items.length" class="empty-state">还没有{{user.role==='PUBLISHER'?'发布':'接取'}}记录</div><div v-else class="history-list"><article v-for="item in result.items" :key="item.application_id||item.id" class="history-row"><div><h3><button class="record-title" @click="emit('detail',item.id)">{{item.title}} ↗</button></h3><p>{{item.location}} · ¥{{item.pay}} / 人 / {{payUnit(item.pay_unit)}}</p><small>{{date(item.accepted_at||item.created_at)}}</small><p v-if="user.role==='STUDENT'"><button class="text-button" @click="emit('home',item.publisher_id)">{{item.organization||'查看企业主页'}} ↗</button></p></div><div class="history-actions"><template v-if="user.role==='PUBLISHER'"><span>{{item.application_count}} 人接取</span><button class="secondary" @click="emit('applicants',item.id)">查看接取人</button></template><template v-else><span class="status-pill" :class="{approved:item.paid_cents}">{{item.paid_cents?'已结算 ¥'+money(item.paid_cents):'待结算'}}</span><small v-if="item.paid_at">{{date(item.paid_at)}}</small></template></div></article></div><div class="pagination"><button class="secondary" :disabled="busy||result.page===0" @click="load(result.page-1)">上一页</button><span>{{result.page+1}} / {{Math.max(1,Math.ceil(result.total/20))}}</span><button class="secondary" :disabled="busy||(result.page+1)*20>=result.total" @click="load(result.page+1)">下一页</button></div></section>
</template>
