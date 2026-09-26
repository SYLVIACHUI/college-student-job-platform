<script setup>
defineProps({ src: String, name: String, size: { type: Number, default: 44 } })
</script>
<template>
  <span class="user-avatar" :style="{ width: size + 'px', height: size + 'px', fontSize: Math.max(14, size / 3) + 'px' }">
    <img v-if="src" :src="src" :alt="name ? name + '的头像' : '用户头像'" />
    <span v-else aria-hidden="true">{{ (name || '禾').slice(0, 1) }}</span>
  </span>
</template>
<style scoped>
.user-avatar{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;border-radius:50%;overflow:hidden;background:#e5eee1;color:#547549;border:2px solid #fff;box-shadow:0 0 0 1px #e4ebe1;font-weight:600}.user-avatar img{width:100%;height:100%;object-fit:cover}
</style>
