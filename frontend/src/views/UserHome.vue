<script setup>
import { ref, watch } from 'vue'
import { api } from '../api'
import CompanyHome from './CompanyHome.vue'
import UserAvatar from '../components/UserAvatar.vue'
const props=defineProps({ userId:String, ownId:String })
const emit=defineEmits(['back','edit','detail','expired'])
const showRecruiter=ref(false)
const profile=ref(null),error=ref(''),loading=ref(true)
watch(()=>props.userId,async id=>{showRecruiter.value=false;loading.value=true;error.value='';profile.value=null;try{profile.value=await api('/users/'+id)}catch(e){error.value=e.message;if(e.status===401)emit('expired')}finally{loading.value=false}},{immediate:true})
</script>
<template>
  <CompanyHome v-if="profile?.role==='PUBLISHER' && !showRecruiter" :key="userId" :company-id="userId" :own-id="ownId" @back="emit('back')" @edit="emit('edit')" @person="showRecruiter=true" @detail="emit('detail',$event)" @expired="emit('expired')"/>
  <section v-else class="panel public-profile"><button class="text-button" @click="showRecruiter ? (showRecruiter=false) : emit('back')">← {{showRecruiter ? '返回企业主页' : '返回'}}</button><div v-if="loading" class="empty-state">正在加载主页…</div><p v-else-if="error" class="alert error">{{error}}</p><template v-else-if="profile"><div class="profile-cover"><span>每一次相遇，都是新的可能。</span></div><div class="profile-identity"><UserAvatar :src="profile.avatar_url" :name="profile.display_name" :size="100"/><button v-if="ownId===userId" class="secondary" @click="emit('edit')">编辑资料</button></div><div class="profile-body"><div class="profile-name"><h2>{{profile.display_name}}</h2><span class="status-pill" :class="{approved:profile.verification_status==='APPROVED'}">{{profile.verification_status==='APPROVED'?'已实名认证':'未完成认证'}}</span></div><p class="profile-role">{{profile.role==='STUDENT'?'学生 · 寻找成长的机会':'企业 · 连接校园人才'}}</p><div class="profile-tags"><span v-if="profile.school">{{profile.school}}</span><span v-if="profile.organization">{{profile.organization}}</span><span v-if="profile.grade">{{profile.grade}}</span><span v-if="profile.major">{{profile.major}}</span></div><h3>关于{{ownId===userId?'我':'TA'}}</h3><p class="profile-bio">{{profile.bio || '还没有填写简介，期待在下一次合作中认识你。'}}</p><p class="profile-joined">{{new Date(profile.created_at).toLocaleDateString('zh-CN')}} 加入平台</p></div></template></section>
</template>
