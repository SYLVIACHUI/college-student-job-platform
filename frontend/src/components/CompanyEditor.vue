<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
const props=defineProps({userId:String})
const emit=defineEmits(['home','expired'])
const company=ref(null), introduction=ref(''), busy=ref(false), error=ref(''), success=ref(''), pendingDelete=ref('')
async function action(fn){if(busy.value)return;busy.value=true;error.value='';success.value='';try{await fn()}catch(e){error.value=e.message;if(e.status===401)emit('expired')}finally{busy.value=false}}
function load(){return action(async()=>{company.value=await api('/companies/'+props.userId);introduction.value=company.value.introduction})}
function save(){action(async()=>{company.value=await api('/me/company',{method:'PUT',body:{introduction:introduction.value}});success.value='企业介绍已保存'})}
function upload(event){const file=event.target.files[0];event.target.value='';if(!file)return;if(file.size>2*1024*1024){error.value='照片不能超过2MB';return}action(async()=>{const body=new FormData();body.append('file',file);company.value=await api('/me/company/photos',{method:'POST',body});success.value='企业照片已上传'})}
function remove(id){action(async()=>{await api('/me/company/photos/'+id,{method:'DELETE'});company.value.photos=company.value.photos.filter(p=>p.id!==id);pendingDelete.value='';success.value='企业照片已删除'})}
onMounted(load)
</script>
<template>
  <section class="panel personal-card company-editor">
    <div class="section-title"><div><h2>企业主页资料</h2><p>展示企业环境，让同学了解你的团队。</p></div><button class="secondary" @click="emit('home',userId)">预览企业主页 ↗</button></div>
    <p v-if="error" class="alert error" role="alert">{{error}} <button v-if="!company" class="text-button" @click="load">重试</button></p><p v-if="success" class="alert success" role="status">{{success}}</p>
    <template v-if="company">
      <p class="profile-note">企业名称：{{company.name || '请先完成企业实名认证'}}。招聘人员使用当前账号的昵称和头像，加入时间使用账号注册时间。</p>
      <form @submit.prevent="save"><label>企业介绍<textarea v-model="introduction" rows="6" maxlength="2000" placeholder="介绍企业业务、工作环境以及面向同学的招聘机会"></textarea><small class="optional">{{introduction.length}} / 2000 · 对登录用户公开</small></label><button class="primary" :disabled="busy">保存企业介绍</button></form>
      <div class="section-title company-photo-heading"><h3>企业照片 · {{company.photos.length}} / 6</h3><label class="secondary upload-label">上传照片<input type="file" accept="image/png,image/jpeg" :disabled="busy || company.photos.length>=6" @change="upload" /></label></div>
      <p class="muted">每张不超过2MB，支持JPG / PNG，保留照片比例。请上传可公开展示的企业照片。</p>
      <div class="company-gallery"><figure v-for="(photo,index) in company.photos" :key="photo.id"><img :src="photo.url" :alt="`企业照片 ${index+1}`"/><figcaption><template v-if="pendingDelete===photo.id"><span>确认删除？</span><button class="text-button" :disabled="busy" @click="remove(photo.id)">确认</button><button class="text-button" :disabled="busy" @click="pendingDelete=''">取消</button></template><button v-else class="text-button" :disabled="busy" @click="pendingDelete=photo.id">删除照片</button></figcaption></figure></div>
      <p v-if="!company.photos.length" class="company-empty">上传第一张照片，展示你的企业环境。</p>
    </template><p v-else-if="busy" class="company-empty">正在加载企业资料…</p>
  </section>
</template>
