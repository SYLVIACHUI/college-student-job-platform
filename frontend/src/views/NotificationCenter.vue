<script setup>
import { ref,onMounted,onUnmounted } from 'vue'
import { api } from '../api'
const emit=defineEmits(['unread','open','expired'])
const result=ref({items:[],total:0,page:0,unread:0}), unreadOnly=ref(false), busy=ref(false), error=ref('')
let timer,alive=true
async function fetchPage(page){
  const data=await api(`/notifications?page=${page}&unreadOnly=${unreadOnly.value}`)
  if(!alive)return
  if(page>0 && !data.items.length){return fetchPage(Math.max(0,Math.ceil(data.total/20)-1))}
  result.value=data;emit('unread',data.unread)
}
async function action(fn){if(busy.value)return;busy.value=true;error.value='';try{await fn()}catch(e){if(alive){error.value=e.message;if(e.status===401)emit('expired')}}finally{busy.value=false}}
function load(page=0){return action(()=>fetchPage(page))}
function read(item,open=false){action(async()=>{if(!item.read_at)await api(`/notifications/${item.id}/read`,{method:'POST'});await fetchPage(result.value.page);if(alive&&open)emit('open',item)})}
function readAll(){action(async()=>{await api('/notifications/read-all',{method:'POST'});await fetchPage(0)})}
onMounted(()=>{load();timer=setInterval(()=>{if(document.visibilityState==='visible')load(result.value.page)},15000)})
onUnmounted(()=>{alive=false;clearInterval(timer)})
</script>
<template>
  <section class="panel personal-card notification-center">
    <div class="section-title"><div><h2>站内消息 <span class="status-pill">{{result.unread}} 条未读</span></h2><p>审核、报名变动、报酬到账与活动取消通知。</p></div><button class="secondary" :disabled="busy||!result.unread" @click="readAll">全部标为已读</button></div>
    <div class="notification-filters"><label><input v-model="unreadOnly" type="checkbox" :disabled="busy" @change="load(0)"/>只看未读</label><button class="text-button" :disabled="busy" @click="load(result.page)">刷新</button></div>
    <p v-if="error" class="alert error" role="alert">{{error}}</p>
    <p v-if="!result.items.length" class="empty-state">{{busy?'正在加载消息…':unreadOnly?'暂无未读消息':'暂无消息，新的业务通知会显示在这里。'}}</p>
    <article v-for="item in result.items" :key="item.id" class="notification-item" :class="{unread:!item.read_at}">
      <div class="notification-heading"><h3><span v-if="!item.read_at" class="notification-dot" aria-label="未读"></span>{{item.title}}</h3><small>{{new Date(item.created_at).toLocaleString('zh-CN')}}</small></div>
      <p>{{item.content}}</p><div class="button-row"><button class="text-button" :disabled="busy" @click="read(item,true)">{{item.target_type==='WALLET'?'查看钱包':item.target_type==='VERIFY'?'查看认证':'查看兼职'}} →</button><button v-if="!item.read_at" class="text-button" :disabled="busy" @click="read(item)">标为已读</button><span v-else class="muted">已读</span></div>
    </article>
    <div v-if="result.total>20" class="pagination"><button class="secondary" :disabled="busy||!result.page" @click="load(result.page-1)">上一页</button><span>{{result.page+1}} / {{Math.ceil(result.total/20)}}</span><button class="secondary" :disabled="busy||(result.page+1)*20>=result.total" @click="load(result.page+1)">下一页</button></div>
  </section>
</template>
<style>
.notification-entry{display:inline-flex;align-items:center;gap:5px;border:0;background:transparent;color:var(--green,#296451);cursor:pointer;white-space:nowrap;padding:8px}.notification-badge{background:#b54b36;color:white;min-width:18px;height:18px;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;font-size:11px;padding:0 4px}.notification-filters{display:flex;justify-content:space-between;align-items:center;margin:20px 0}.notification-filters label{display:flex;align-items:center;gap:8px;margin:0}.notification-filters input{width:auto;margin:0}.notification-item{padding:22px 18px;border-top:1px solid #e3eae6;overflow-wrap:anywhere}.notification-item.unread{background:#f2f7f3;border-left:3px solid #3c765b}.notification-heading{display:flex;justify-content:space-between;gap:16px;align-items:baseline}.notification-heading h3{margin:0;font-size:16px}.notification-heading small{color:#718078;white-space:nowrap}.notification-dot{display:inline-block;width:7px;height:7px;background:#34805e;border-radius:50%;margin-right:8px}.notification-item p{line-height:1.8;white-space:pre-wrap}.notification-item .muted{font-size:12px}.notification-center .section-title h2{display:flex;flex-wrap:wrap;align-items:center;gap:12px}@media(max-width:600px){.notification-heading{flex-direction:column;gap:8px}.notification-center .section-title{flex-direction:column;align-items:flex-start;gap:14px}.notification-entry{padding:5px;font-size:12px}}
@media(max-width:600px){.topbar .breadcrumb{display:none}.topbar .top-actions{width:100%;gap:8px;justify-content:flex-end}.topbar .notification-entry{margin-right:auto;flex-shrink:0}.topbar .role-switch button{white-space:nowrap}.topbar .top-divider{display:none}}
</style>
