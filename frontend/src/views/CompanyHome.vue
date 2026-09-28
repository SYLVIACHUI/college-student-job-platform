<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import UserAvatar from '../components/UserAvatar.vue'
import JobCard from '../components/JobCard.vue'
const props=defineProps({ companyId:String, ownId:String })
const emit=defineEmits(['back','edit','person','detail','expired'])
const company=ref(null), jobs=ref([]), total=ref(0), page=ref(0), loading=ref(true), busy=ref(false), error=ref('')
function fail(e){error.value=e.message;if(e.status===401)emit('expired')}
async function loadJobs(next=0){
  if(busy.value)return
  busy.value=true;error.value=''
  try{const data=await api(`/companies/${props.companyId}/jobs?page=${next}`);jobs.value=data.items;total.value=data.total;page.value=next}catch(e){fail(e)}finally{busy.value=false}
}
async function load(){
  loading.value=true;error.value=''
  try{company.value=await api('/companies/'+props.companyId);await loadJobs()}catch(e){fail(e)}finally{loading.value=false}
}
onMounted(load)
</script>
<template>
  <div class="company-home">
    <button class="text-button" @click="emit('back')">← 返回</button>
    <p v-if="error" class="alert error" role="alert">{{error}} <button class="text-button" @click="load">重试</button></p>
    <div v-if="loading" class="empty-state">正在加载企业主页…</div>
    <template v-else-if="company">
      <section class="panel company-hero">
        <div class="company-hero-art" aria-hidden="true"><span>贝鱼 · 企业伙伴</span><i></i><i></i></div>
        <div class="company-hero-body">
          <div><span class="card-kicker">MEET YOUR NEXT OPPORTUNITY</span><h2>{{company.name || '企业名称待认证'}}</h2>
            <div class="company-meta"><span class="status-pill" :class="{approved:company.verification_status==='APPROVED'}">{{company.verification_status==='APPROVED'?'已实名认证':'未完成认证'}}</span><span>{{new Date(company.joined_at).toLocaleDateString('zh-CN')}} 加入平台</span></div>
          </div>
          <button v-if="ownId===companyId" class="secondary" @click="emit('edit')">编辑企业资料 ↗</button>
        </div>
      </section>
      <div class="company-columns">
        <div class="company-main">
          <section class="panel personal-card"><div class="section-title"><h3>关于企业</h3><span>ABOUT US</span></div><p class="company-introduction">{{company.introduction || '企业暂未填写介绍。你可以通过下方岗位了解招聘方向。'}}</p></section>
          <section class="panel personal-card"><div class="section-title"><h3>企业相册</h3><span>{{company.photos.length}} 张照片</span></div>
            <div v-if="company.photos.length" class="company-gallery"><figure v-for="(photo,index) in company.photos" :key="photo.id"><a :href="photo.url" target="_blank" rel="noopener" :aria-label="`查看企业照片 ${index+1} 原图`"><img :src="photo.url" :alt="`${company.name || '企业'}照片 ${index+1}`" loading="lazy" /></a></figure></div>
            <p v-else class="company-empty">企业还没有上传照片。</p>
          </section>
        </div>
        <aside class="panel personal-card company-recruiters"><div class="section-title"><h3>企业招聘人员</h3><span>{{company.recruiters.length}} 人</span></div>
          <button v-for="person in company.recruiters" :key="person.id" class="company-recruiter" @click="emit('person',person.id)"><UserAvatar :src="person.avatar_url" :name="person.display_name" :size="56"/><span><strong>{{person.display_name}}</strong><small>招聘负责人 · 查看个人主页 ↗</small></span></button>
        </aside>
      </div>
      <section class="panel personal-card"><div class="section-title"><div><h3>目前发布的兼职 <span class="optional">{{total}} 个</span></h3><p>展示尚未开始的岗位，满员岗位也会保留。</p></div><button class="text-button" :disabled="busy" @click="loadJobs(page)">刷新岗位</button></div>
        <div class="job-list"><JobCard v-for="job in jobs" :key="job.id" :job="job" :show-company="false" @open="emit('detail',$event)" /></div>
        <p v-if="!jobs.length" class="company-empty">{{busy?'正在加载岗位…':'暂时没有尚未开始的兼职，之后再来看看。'}}</p>
        <div v-if="total>12" class="company-pagination"><button class="secondary" :disabled="busy || page===0" @click="loadJobs(page-1)">上一页</button><span>第 {{page+1}} 页 / {{Math.ceil(total/12)}} 页</span><button class="secondary" :disabled="busy || (page+1)*12>=total" @click="loadJobs(page+1)">下一页</button></div>
      </section>
    </template>
  </div>
</template>
