<script setup>
import PublisherApp from "./pages/publisher/PublisherApp.vue";
import StudentApp from "./pages/student/StudentApp.vue";
import ReviewApp from "./pages/admin/ReviewApp.vue";
const adminPortal = location.pathname.startsWith('/admin');
const studentPortal = location.pathname.startsWith("/student");
</script>

<template>
  <ReviewApp v-if="adminPortal" />
  <StudentApp v-else-if="studentPortal" />
  <PublisherApp v-else />
</template>
