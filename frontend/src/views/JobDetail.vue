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
async function changeParticipation(){
  if(busy.value)return
  busy.value=true;error.value='';success.value=''
  try{
    const cancelling=actionConfirm.value==='cancel'
    await api(`/jobs/${props.jobId}/${cancelling?'cancel':'withdraw'}`,{method:'POST',...(cancelling?{body:{reason:cancelReason.value}}:{})})
    actionConfirm.value='';success.value=cancelling?'活动已取消，已通知报名学生':'已退出兼职，名额已释放，已通知企业'
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
    await api(`/jobs/${props.jobId}/apply`, { method: "POST" });
    confirming.value = false;
    success.value = "加入成功，可在接取历史中查看";
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
      <div class="record-heading">
        <span class="outline-tag">{{ job.category }}</span>
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
          <dt>接收状态</dt>
          <dd>
            {{ job.acceptance_status === "ACCEPTED" ? "已接受" : "未接受"
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
          一起兼职的同学 · {{ job.applications }} / {{ job.required_count }}
        </h3>
        <button class="text-button" @click="refresh">刷新名额</button>
      </div>
      <p v-if="student && !cancelled && !withdrawn" class="muted">
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
          v-for="slot in (cancelled ? 0 : job.remaining)"
          :key="`vacant-${slot}`"
          class="participant-slot"
          :disabled="!student || job.applied || busy || started || cancelled || withdrawn"
          @click="choose"
          :aria-label="`加入兼职，空位${slot}`"
        >
          <span class="vacant-circle">+</span><span>空缺名额</span>
        </button>
      </div>
      <p v-if="job.applied && !cancelled" class="status-pill approved">你已加入该兼职</p>
      <p v-else-if="started" class="muted">兼职已开始，停止接取。</p>
      <p v-else-if="!job.remaining" class="muted">名额已满。</p>
      <button
        v-if="user.id === job.publisher_id"
        class="primary"
        @click="emit('applicants', job.id)"
      >
        查看接取人资料 / 发放兼职费
      </button>
      <div class="button-row" style="margin-top:20px" v-if="!cancelled && !started && job.starts_at">
        <button v-if="student && job.applied && !job.paid" class="secondary" :disabled="busy" @click="actionConfirm='withdraw';error=''">退出兼职</button>
        <button v-if="user.id===job.publisher_id" class="secondary" :disabled="busy" @click="actionConfirm='cancel';error=''">取消活动</button>
      </div>
      <div v-if="actionConfirm" class="modal-backdrop" @click.self="!busy && (actionConfirm='')">
        <section class="panel modal" role="dialog" aria-modal="true" :aria-label="actionConfirm==='cancel'?'取消活动':'退出兼职'">
          <h2>{{actionConfirm==='cancel'?'确认取消活动？':'确认退出兼职？'}}</h2>
          <p>{{actionConfirm==='cancel'?'取消后停止报名，已报名学生将收到通知。已有结算或已开工的活动不能取消。':'退出后释放名额并通知企业，本次报名不能恢复。已结算或已开工的兼职不能退出。'}}</p>
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
          aria-label="确认加入兼职"
        >
          <h2>确认加入「{{ job.title }}」？</h2>
          <p>
            {{ jobTime(job.starts_at) }} ·
            {{ jobDuration(job.duration_minutes) }}
          </p>
          <p>地点：{{ job.location }}</p>
          <p>报酬：¥{{ job.pay }} / 人 / {{ payUnit(job.pay_unit) }}</p>
          <p>确认后占用一个名额。请确认你能按时参加。</p>
          <p v-if="error" class="alert error" role="alert">{{ error }}</p>
          <div class="button-row">
            <button
              class="primary"
              :disabled="busy || job.applied || job.remaining === 0 || started || cancelled || withdrawn"
              @click="join"
            >
              {{ busy ? "加入中…" : "确认加入" }}</button
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
