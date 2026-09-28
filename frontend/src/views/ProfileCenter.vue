<script setup>
import { reactive, ref } from 'vue'
import { api } from '../api'
import CompanyEditor from '../components/CompanyEditor.vue'
import UserAvatar from '../components/UserAvatar.vue'
const props = defineProps({ user: Object })
const emit = defineEmits(['updated', 'home', 'history', 'wallet', 'expired'])
const form = reactive({ nickname: props.user.nickname || '', birthday: props.user.birthday?.slice(0,10) || '', grade: props.user.grade || '', major: props.user.major || '', bio: props.user.bio || '' })
const busy = ref(false), error = ref(''), success = ref('')
const today = new Date().toLocaleDateString('en-CA')
async function action(fn) { if(busy.value)return;busy.value=true;error.value='';success.value='';try{await fn()}catch(e){error.value=e.message;if(e.status===401)emit('expired')}finally{busy.value=false} }
function save() { action(async()=>{const result=await api('/me/profile',{method:'PUT',body:{...form,birthday:form.birthday || null}});emit('updated',result);success.value='个人资料已保存'}) }
function upload(event) { const file=event.target.files[0];event.target.value='';if(!file)return;if(file.size>2*1024*1024){error.value='头像不能超过2MB';return}action(async()=>{const body=new FormData();body.append('file',file);emit('updated',await api('/me/avatar',{method:'POST',body}));success.value='头像已更换'}) }
</script>
<template>
  <div class="personal-grid">
    <section class="panel personal-card">
      <div class="section-title"><div><h2>个人资料</h2><p>让与你相遇的人，更了解你一点。</p></div><button class="secondary" @click="emit('home',user.id)">{{user.role==='PUBLISHER'?'预览企业主页':'预览我的主页'}} ↗</button></div>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p><p v-if="success" class="alert success" role="status">{{ success }}</p>
      <div class="avatar-editor"><UserAvatar :src="user.avatar_url" :name="user.display_name" :size="84" /><div><label class="secondary upload-label">{{ busy ? '处理中…' : '更换头像' }}<input type="file" accept="image/png,image/jpeg" :disabled="busy" @change="upload" /></label><p>JPG / PNG · 最大2MB · 自动裁剪为方形</p></div></div>
      <form @submit.prevent="save">
        <label>昵称<input v-model.trim="form.nickname" required maxlength="40" placeholder="给自己起一个好记的名字" /></label>
        <label>生日 <span class="optional">仅自己可见</span><input v-model="form.birthday" type="date" min="1900-01-01" :max="today" /></label>
        <div v-if="user.role==='STUDENT'" class="field-grid"><label>年级<select v-model="form.grade"><option value="">暂不填写</option><option v-for="grade in ['大一','大二','大三','大四','大五','硕士','博士']" :key="grade">{{ grade }}</option></select></label><label>专业<input v-model.trim="form.major" maxlength="100" placeholder="如：计算机科学与技术" /></label></div>
        <label>个人简介<textarea v-model.trim="form.bio" rows="4" maxlength="300" placeholder="介绍你的兴趣、能力，或者单位的招募方向"></textarea><small class="optional">{{ form.bio.length }}/300 · 将展示在主页</small></label>
        <p class="privacy-note">主页展示昵称、头像、简介、身份及认证状态。生日、手机号、证件信息与钱包仅本人可见。</p>
        <button class="primary" :disabled="busy">{{ busy?'保存中…':'保存个人资料' }}</button>
      </form>
    </section>
    <aside class="profile-aside">
      <section class="panel personal-card"><span class="card-kicker">MY SPACE</span><h3>{{ user.display_name }}</h3><p class="muted">{{ user.account }}</p><div class="profile-shortcuts"><button @click="emit('history')"><span>{{ user.role==='STUDENT'?'兼职接取历史':'岗位发布记录' }}</span><span>→</span></button><button @click="emit('wallet')"><span>{{ user.role==='STUDENT'?'我的钱包':'企业结算钱包' }}</span><span>→</span></button><button @click="emit('home',user.id)"><span>{{user.role==='PUBLISHER'?'企业主页':'我的个人主页'}}</span><span>→</span></button></div></section>
      <section class="panel personal-card"><h3>资料与实名认证</h3><p class="profile-note">昵称与简介可自由更新。真实姓名、单位和学校来自实名认证资料，不在此处修改。</p></section>
    </aside>
    <CompanyEditor v-if="user.role==='PUBLISHER'" :user-id="user.id" @home="emit('home',$event)" @expired="emit('expired')"/>
  </div>
</template>
