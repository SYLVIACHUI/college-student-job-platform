<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { api } from "../api";
import { jobTime, jobDuration, payUnit } from "../jobFormat";
import UserAvatar from "../components/UserAvatar.vue";
const props = defineProps({ jobId: String, user: Object, canAct: Boolean });
const emit = defineEmits([
  "back",
  "home",
  "verify",
  "applicants",
  "joined",
  "expired",
]);
const job = ref(null),
  error = ref(""),
  busy = ref(false),
  confirming = ref(false),
  success = ref("");
const actionConfirm=ref(''),cancelReason=ref('')
const cancelled=computed(()=>job.value?.status==='CANCELLED')
const withdrawn=computed(()=>job.value?.application_status==='WITHDRAWN')
const screening=computed(()=>job.value?.recruitment_mode==='SCREENING')
const pending=computed(()=>job.value?.application_status==='PENDING')
const rejected=computed(()=>job.value?.application_status==='REJECTED')
const resume=ref('')
async function changeParticipation(){
  if(busy.value)return
  busy.value=true;error.value='';success.value=''
  try{
    const cancelling=actionConfirm.value==='cancel'
    await api(`/jobs/${props.jobId}/${cancelling?'cancel':'withdraw'}`,{method:'POST',...(cancelling?{body:{reason:cancelReason.value}}:{})})
    actionConfirm.value='';success.value=cancelling?'活动已取消，已通知报名学生':pending.value?'已撤回简历报名，已通知企业':'已退出兼职，名额已释放，已通知企业'
    emit('joined');await load()
  }catch(e){error.value=e.message;if(e.status===401)emit('expired');await refresh()}finally{busy.value=false}
}
const student = computed(() => props.user.role === "STUDENT");
const started = computed(
  () =>
    job.value?.starts_at &&
    new Date(job.value.starts_at).getTime() <= Date.now(),
);
let poll,
  alive = true,
  loading = false;
async function load() {
  if (loading) return;
  loading = true;
  try {
    const result = await api(`/jobs/${props.jobId}`);
    if (alive) job.value = result;
  } finally {
    loading = false;
  }
}
async function refresh() {
  try {
    await load();
  } catch (e) {
    error.value = e.message;
    if (e.status === 401) emit("expired");
  }
}
function choose() {
  if (!props.canAct) {
    emit("verify");
    return;
  }
  error.value = "";
  confirming.value = true;
}
async function join() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    await api(`/jobs/${props.jobId}/apply`, { method: "POST", ...(screening.value ? {body:{resume:resume.value}} : {}) });
    confirming.value = false;
    success.value = screening.value ? "简历已提交，等待企业筛选，可在报名记录中查看结果" : "加入成功，可在接取历史中查看";
    resume.value = '';
    emit("joined");
    await load();
  } catch (e) {
    error.value = e.message;
    if (e.status === 401) emit("expired");
    await refresh();
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  refresh();
  poll = setInterval(() => {
    if (document.visibilityState === "visible" && !busy.value) refresh();
  }, 10000);
});
onUnmounted(() => {
  alive = false;
  clearInterval(poll);
});
</script>
<template>
  <section class="panel personal-card job-detail">
    <button class="text-button" @click="emit('back')">← 返回上一页</button>
    <p v-if="error" class="alert error" role="alert">
      {{ error }}<button class="text-button" @click="refresh">刷新</button>
    </p>
    <p v-if="success" class="alert success" role="status">{{ success }}</p>
    <div v-if="!job" class="empty-state">正在加载岗位详情…</div>
    <template v-else>
      <p v-if="cancelled" class="alert error">活动已取消：{{job.cancel_reason}}</p>
      <p v-if="withdrawn" class="status-pill">你已退出该兼职，本次报名不可恢复。</p>
      <p v-if="pending && !cancelled" class="alert">简历已提交，等待企业筛选。录取后才能参加，当前不占用名额。</p>
      <p v-if="rejected" class="status-pill">本次报名未录取，可浏览其他岗位。</p>
      <div class="record-heading">
        <span class="outline-tag">{{ job.category }}</span>
        <span v-if="screening" class="status-pill screening-tag">需简历筛选 · 企业录取后参加</span>
        <h2>{{ job.title }}</h2>
        <button
          class="user-home-trigger"
          @click="emit('home', job.publisher_id)"
        >
          {{ job.organization || "企业主页" }} ↗
        </button>
      </div>
      <dl class="job-facts">
        <div>
          <dt>报酬金额</dt>
          <dd>
            ¥{{ Number(job.pay).toFixed(2) }} / 人 / {{ payUnit(job.pay_unit) }}
          </dd>
        </div>
        <div>
          <dt>工作地点</dt>
          <dd>{{ job.location }}</dd>
        </div>
        <div>
          <dt>兼职时间</dt>
          <dd>{{ jobTime(job.starts_at) }}</dd>
        </div>
        <div>
          <dt>持续时间</dt>
          <dd>{{ jobDuration(job.duration_minutes) }}</dd>
        </div>
        <div>
          <dt>需要人数</dt>
          <dd>{{ job.required_count }} 人 · {{cancelled ? '已停止招募' : '还需 '+job.remaining+' 人'}}</dd>
        </div>
        <div>
          <dt>已报名人数</dt>
          <dd>{{ job.applications }} 人</dd>
        </div>
        <div>
          <dt>已录取人数</dt>
          <dd>{{ job.accepted_count }} 人</dd>
        </div>
        <div>
          <dt>接收状态</dt>
          <dd>
            {{ job.acceptance_status === "ACCEPTED" ? "已有同学录取" : "暂无录取"
            }}{{ job.remaining === 0 ? " · 已满员" : "" }}
          </dd>
        </div>
      </dl>
      <h3>具体工作内容</h3>
      <p class="job-prose">{{ job.description }}</p>
      <h3>工作要求</h3>
      <p class="job-prose">{{ job.requirements }}</p>
      <div class="section-title">
        <h3>
          {{ screening ? '已录取的同学' : '一起兼职的同学' }} · {{ job.accepted_count }} / {{ job.required_count }}
        </h3>
        <button class="text-button" @click="refresh">刷新名额</button>
      </div>
      <p v-if="student && screening && !cancelled" class="muted">报名时填写在线简历，由企业决定是否录取。简历和实名资料仅本岗位发布者可查看。已报名人数包含待筛选与未录取的报名，不包含已退出的报名。</p>
      <p v-else-if="student && !cancelled && !withdrawn" class="muted">
        点击空位的加号并确认即可加入。加入后，本岗位发布者可以查看你的真实姓名、学号和学校。
      </p>
      <div class="participant-slots">
        <button
          v-for="person in job.participants"
          :key="person.id"
          class="participant-slot"
          @click="emit('home', person.id)"
          :aria-label="`查看${person.display_name}的主页`"
        >
          <UserAvatar
            :src="person.avatar_url"
            :name="person.display_name"
            :size="56"
          /><span>{{ person.display_name }}</span>
        </button>
        <button
          v-for="slot in (cancelled || screening ? 0 : job.remaining)"
          :key="`vacant-${slot}`"
          class="participant-slot"
          :disabled="!student || !!job.application_status || busy || started || cancelled"
          @click="choose"
          :aria-label="`加入兼职，空位${slot}`"
        >
          <span class="vacant-circle">+</span><span>空缺名额</span>
        </button>
      </div>
      <button v-if="screening && student && !job.application_status && !cancelled && !started && job.remaining > 0" class="primary" :disabled="busy" @click="choose">提交简历报名</button>
      <p v-if="job.application_status === 'ACTIVE' && !cancelled" class="status-pill approved">{{screening ? '你已被录取，请按时参加' : '你已加入该兼职'}}</p>
      <p v-else-if="started" class="muted">兼职已开始，停止接取。</p>
      <p v-else-if="!job.remaining" class="muted">名额已满。</p>
      <button
        v-if="user.id === job.publisher_id"
        class="primary"
        @click="emit('applicants', job.id)"
      >
        {{ screening ? '查看简历 / 筛选录取 / 发放兼职费' : '查看接取人资料 / 发放兼职费' }}
      </button>
      <div class="button-row" style="margin-top:20px" v-if="!cancelled && !started && job.starts_at">
        <button v-if="student && job.applied && !job.paid" class="secondary" :disabled="busy" @click="actionConfirm='withdraw';error=''">{{pending ? '撤回报名' : '退出兼职'}}</button>
        <button v-if="user.id===job.publisher_id" class="secondary" :disabled="busy" @click="actionConfirm='cancel';error=''">取消活动</button>
      </div>
      <div v-if="actionConfirm" class="modal-backdrop" @click.self="!busy && (actionConfirm='')">
        <section class="panel modal" role="dialog" aria-modal="true" :aria-label="actionConfirm==='cancel'?'取消活动':'退出兼职'">
          <h2>{{actionConfirm==='cancel'?'确认取消活动？':pending?'确认撤回报名？':'确认退出兼职？'}}</h2>
          <p>{{actionConfirm==='cancel'?'取消后停止报名，已报名学生将收到通知。已有结算或已开工的活动不能取消。':pending?'撤回后通知企业，本次简历报名不能恢复或再次提交。':'退出后释放名额并通知企业，本次报名不能恢复。已结算或已开工的兼职不能退出。'}}</p>
          <form @submit.prevent="changeParticipation"><label v-if="actionConfirm==='cancel'">取消原因<textarea v-model.trim="cancelReason" required maxlength="300" rows="3" placeholder="向报名同学说明取消原因"></textarea></label>
            <p v-if="error" class="alert error" role="alert">{{error}}</p>
            <div class="button-row"><button class="primary" :disabled="busy">{{busy?'处理中…':'确认操作'}}</button><button type="button" class="secondary" :disabled="busy" @click="actionConfirm=''">暂不操作</button></div>
          </form>
        </section>
      </div>
      <div
        v-if="confirming"
        class="modal-backdrop"
        @click.self="!busy && (confirming = false)"
      >
        <section
          class="panel modal"
          role="dialog"
          aria-modal="true"
          :aria-label="screening ? '提交简历报名' : '确认加入兼职'"
        >
          <h2>{{ screening ? '报名' : '确认加入' }}「{{ job.title }}」{{screening ? '' : '？'}}</h2>
          <p>
            {{ jobTime(job.starts_at) }} ·
            {{ jobDuration(job.duration_minutes) }}
          </p>
          <p>地点：{{ job.location }}</p>
          <p>报酬：¥{{ job.pay }} / 人 / {{ payUnit(job.pay_unit) }}</p>
          <template v-if="screening">
            <label>在线简历<textarea v-model.trim="resume" maxlength="8000" rows="8" required :disabled="busy" placeholder="介绍你的教育背景、相关技能、项目或兼职经历，以及可以参加的时间"></textarea></label>
            <p class="muted">{{resume.length}} / 8000 字 · 提交后等待企业筛选，录取前不占用名额。简历仅本岗位发布者可见。</p>
          </template>
          <p v-else>确认后占用一个名额。请确认你能按时参加。</p>
          <p v-if="error" class="alert error" role="alert">{{ error }}</p>
          <div class="button-row">
            <button
              class="primary"
              :disabled="busy || !!job.application_status || job.remaining === 0 || started || cancelled || (screening && !resume.trim())"
              @click="join"
            >
              {{ busy ? "提交中…" : screening ? "确认提交简历" : "确认加入" }}</button
            ><button
              class="secondary"
              :disabled="busy"
              @click="confirming = false"
            >
              取消
            </button>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>
