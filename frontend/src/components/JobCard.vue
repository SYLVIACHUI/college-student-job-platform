<script setup>
import { jobTime, jobDuration } from "../jobFormat";
defineProps({ job: Object, showCompany: { type: Boolean, default: true } });
const emit = defineEmits(["open", "home"]);
</script>
<template>
  <article class="job-card vacancy-card">
    <span class="outline-tag">{{ job.category }}</span>
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
    <div class="job-bottom">
      <strong>{{
        job.remaining > 0 ? `还需 ${job.remaining} 人` : "名额已满"
      }}</strong
      ><button class="text-button" @click="emit('open', job.id)">
        查看详情 →
      </button>
    </div>
  </article>
</template>
