<script setup>
defineProps({ form: Object, busy: Boolean });
const emit = defineEmits(["submit"]);
const categories = [
  "校园服务",
  "活动执行",
  "餐饮服务",
  "零售促销",
  "文案设计",
  "家教辅导",
  "技术开发",
  "其他",
];
</script>
<template>
  <form @submit.prevent="emit('submit')" class="job-publish-form">
    <label
      >标题<input
        v-model.trim="form.title"
        maxlength="100"
        required
        placeholder="如：周末活动助理"
    /></label>
    <div class="field-grid">
      <label
        >兼职类别<select v-model="form.category" required>
          <option value="" disabled>请选择类别</option>
          <option v-for="item in categories" :key="item">{{ item }}</option>
        </select></label
      ><label
        >需要人数<input
          v-model.number="form.requiredCount"
          type="number"
          min="1"
          max="200"
          step="1"
          required
      /></label>
    </div>
    <label
      >工作地点<input
        v-model.trim="form.location"
        maxlength="150"
        required
        placeholder="请填写具体地址"
    /></label>
    <label
      >报酬金额（元 / 人 / 次）<input
        v-model.number="form.pay"
        type="number"
        min="0.01"
        max="100000"
        step="0.01"
        required
    /></label>
    <div class="field-grid">
      <label
        >兼职开始时间<input
          v-model="form.startsAt"
          type="datetime-local"
          required /></label
      ><label
        >持续时间（分钟）<input
          v-model.number="form.durationMinutes"
          type="number"
          min="1"
          max="525600"
          step="1"
          required
      /></label>
    </div>
    <label
      >具体工作内容<textarea
        v-model.trim="form.description"
        maxlength="2000"
        rows="4"
        required
        placeholder="描述具体职责、任务和安排"
      />
    </label>
    <label
      >工作要求<textarea
        v-model.trim="form.requirements"
        maxlength="2000"
        rows="3"
        required
        placeholder="描述技能、着装和其他必要要求"
      />
    </label>
    <p class="muted">
      发布单位自动关联你的企业认证信息。时间按本地时间填写，报酬为每人完成此次兼职的总额。
    </p>
    <button class="primary" :disabled="busy">
      {{ busy ? "发布中…" : "确认发布" }}
    </button>
  </form>
</template>
