<script setup>
import { jobTime, jobDuration } from "../jobFormat";
defineProps({ job: Object, showCompany: { type: Boolean, default: true } });
const emit = defineEmits(["open", "home"]);
</script>
<template>
  <article class="job-card vacancy-card">
    <span class="outline-tag">{{ job.category }}<span v-if="job.status==='CANCELLED'"> · 已取消</span></span>
    <span v-if="job.recruitment_mode === 'SCREENING'" class="status-pill screening-tag">需简历筛选</span>
    <h3>
      <button class="job-title-link" @click="emit('open', job.id)">
        {{ job.title }}
      </button>
    </h3>
    <button v-if="showCompany" class="user-home-trigger" @click="emit('home', job.publisher_id)">
      {{ job.organization || "企业主页" }} ↗
    </button>
    <dl class="vacancy-summary">
      <div>
        <dt>兼职时间</dt>
        <dd>{{ jobTime(job.starts_at) }}</dd>
      </div>
      <div>
        <dt>持续时间</dt>
        <dd>{{ jobDuration(job.duration_minutes) }}</dd>
      </div>
    </dl>
    <dl class="enrollment-counts">
      <div><dt>已报名</dt><dd>{{ job.applications ?? 0 }} 人</dd></div>
      <div><dt>需要人数</dt><dd>{{ job.required_count ?? 0 }} 人</dd></div>
      <div><dt>已录取</dt><dd>{{ job.accepted_count ?? 0 }} 人</dd></div>
    </dl>
    <p v-if="job.application_status" class="muted">{{ {PENDING:'你的简历待筛选',ACTIVE:job.recruitment_mode==='SCREENING'?'你已录取':'你已加入',REJECTED:'本次未录取',WITHDRAWN:'你已退出'}[job.application_status] }}</p>
    <div class="job-bottom">
      <strong>{{
        job.status==='CANCELLED' ? "活动已取消" : job.remaining > 0 ? `还需 ${job.remaining} 人` : "名额已满"
      }}</strong
      ><button class="text-button" @click="emit('open', job.id)">
        查看详情 →
      </button>
    </div>
  </article>
</template>
