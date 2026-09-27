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
    <button class="text-button" @click="emit('back')">← 返回岗位列表</button>
    <p v-if="error" class="alert error" role="alert">
      {{ error }}<button class="text-button" @click="refresh">刷新</button>
    </p>
    <p v-if="success" class="alert success" role="status">{{ success }}</p>
    <div v-if="!job" class="empty-state">正在加载岗位详情…</div>
    <template v-else>
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
          <dd>{{ job.required_count }} 人 · 还需 {{ job.remaining }} 人</dd>
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
      <p v-if="student" class="muted">
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
          v-for="slot in job.remaining"
          :key="`vacant-${slot}`"
          class="participant-slot"
          :disabled="!student || job.applied || busy || started"
          @click="choose"
          :aria-label="`加入兼职，空位${slot}`"
        >
          <span class="vacant-circle">+</span><span>空缺名额</span>
        </button>
      </div>
      <p v-if="job.applied" class="status-pill approved">你已加入该兼职</p>
      <p v-else-if="started" class="muted">兼职已开始，停止接取。</p>
      <p v-else-if="!job.remaining" class="muted">名额已满。</p>
      <button
        v-if="user.id === job.publisher_id"
        class="primary"
        @click="emit('applicants', job.id)"
      >
        查看接取人资料 / 发放兼职费
      </button>
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
              :disabled="busy || job.applied || job.remaining === 0 || started"
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
