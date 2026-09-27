import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { api } from "../api";

// Shared session and request logic; each portal owns its page template.
export function usePortal(roleName) {
  const role = ref(roleName);
  const publisher = computed(() => role.value === "PUBLISHER");
  const user = ref(null),
    ready = ref(false),
    dev = ref(false),
    page = ref("home"),
    mode = ref("login");
  const busy = ref(false),
    sending = ref(false),
    notice = ref(""),
    error = ref(""),
    codeHint = ref(""),
    cooldown = ref(0),
    jobs = ref([]),
    events = ref([]);
  const authForm = reactive({ phone: "", code: "", password: "" });
  const verifyForm = reactive({
    organization: "",
    surname: "",
    name: "",
    identityNumber: "",
    school: "",
    studentNumber: "",
    email: "",
  });
  const jobForm = reactive({
    category: "",
    requiredCount: 1,
    requirements: "",
    startsAt: "",
    durationMinutes: 240,
    title: "",
    description: "",
    location: "",
    pay: 120,
  });
  const showJob = ref(false),
    search = ref("");
  const statusNames = {
    UNVERIFIED: "未实名认证",
    PENDING: "审核中",
    APPROVED: "已认证",
    REJECTED: "审核未通过",
  };
  const verified = computed(
    () => user.value?.verification_status === "APPROVED",
  );
  const canAct = computed(
    () =>
      verified.value &&
      (publisher.value ? user.value?.can_publish : user.value?.can_accept) ===
        1,
  );
  const filteredJobs = computed(() =>
    jobs.value.filter((j) =>
      (j.title + j.category + j.organization).includes(search.value),
    ),
  );
  const pending = computed(() => user.value?.verification_status === "PENDING");
  const profileUserId = ref(""),
    selectedJobId = ref(""),
    homeReturn = ref("profile");
  const jobReturn = ref("home");
  function openJob(id) {
    jobReturn.value = page.value;
    selectedJobId.value = id;
    navigate("jobDetail");
  }
  const pageTitle = computed(
    () =>
      ({
        profile: "个人中心",
        jobDetail: "兼职详情",
        userHome: "个人主页",
        history: publisher.value ? "岗位发布记录" : "兼职接取历史",
        applicants: "岗位接取人",
        wallet: publisher.value ? "企业结算钱包" : "我的钱包",
        verify: "实名认证",
        jobs: publisher.value ? "岗位管理" : "我的领取",
      })[page.value] ||
      (publisher.value ? "今天，也有新的可能。" : "发现值得出发的机会。"),
  );
  function openUser(id) {
    homeReturn.value = page.value;
    profileUserId.value = id;
    navigate("userHome");
  }
  function openApplicants(id) {
    selectedJobId.value = id;
    navigate("applicants");
  }
  function sessionExpired() {
    user.value = null;
    clearDrafts();
    error.value = "登录已过期，请重新登录";
  }
  let timer, poll;
  async function run(action) {
    if (busy.value) return;
    busy.value = true;
    error.value = "";
    notice.value = "";
    try {
      await action();
    } catch (e) {
      error.value = e.message;
      if (e.status === 401 && user.value) user.value = null;
    } finally {
      busy.value = false;
    }
  }
  async function loadUser() {
    const current = await api("/me");
    if (current.role !== roleName) {
      location.replace(
        current.role === "PUBLISHER" ? "/publisher" : "/student",
      );
      return;
    }
    user.value = current;
    history.replaceState({}, "", publisher.value ? "/publisher" : "/student");
  }
  async function loadJobs() {
    jobs.value = await api("/jobs");
  }
  function clearDrafts() {
    Object.keys(verifyForm).forEach((k) => (verifyForm[k] = ""));
    Object.assign(jobForm, {
      category: "",
      requiredCount: 1,
      requirements: "",
      startsAt: "",
      durationMinutes: 240,
      title: "",
      description: "",
      location: "",
      pay: 120,
    });
    showJob.value = false;
    search.value = "";
    authForm.password = "";
    authForm.code = "";
    codeHint.value = "";
  }
  async function refresh() {
    await loadUser();
    await loadJobs();
    if (page.value === "verify")
      events.value = await api("/verification/events");
  }
  async function switchRole(next) {
    if (role.value === next) return;
    await run(async () => {
      if (user.value) await api("/auth/logout", { method: "POST" });
      user.value = null;
      jobs.value = [];
      events.value = [];
      page.value = "home";
      clearDrafts();
      location.assign(next === "PUBLISHER" ? "/publisher" : "/student");
    });
  }
  async function sendCode() {
    if (sending.value || cooldown.value) return;
    if (!/^1[3-9]\d{9}$/.test(authForm.phone)) {
      error.value = "请输入正确的11位手机号";
      return;
    }
    sending.value = true;
    error.value = "";
    try {
      const result = await api("/auth/code", {
        method: "POST",
        body: { role: role.value, phone: authForm.phone },
      });
      codeHint.value = result.debugCode || "";
      notice.value = result.message;
      cooldown.value = 60;
    } catch (e) {
      error.value = e.message;
    } finally {
      sending.value = false;
    }
  }
  function submitAuth() {
    run(async () => {
      if (mode.value === "register") {
        await api("/auth/register", {
          method: "POST",
          body: { ...authForm, role: role.value },
        });
        mode.value = "login";
        authForm.code = "";
        codeHint.value = "";
        notice.value = "注册成功！账号默认为手机号，请登录。";
      } else {
        await api("/auth/login", {
          method: "POST",
          body: { ...authForm, role: role.value },
        });
        authForm.password = "";
        await refresh();
      }
    });
  }
  function logout() {
    run(async () => {
      await api("/auth/logout", { method: "POST" });
      user.value = null;
      jobs.value = [];
      events.value = [];
      page.value = "home";
      clearDrafts();
    });
  }
  function navigate(next) {
    page.value = next;
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (next === "verify")
      run(async () => {
        events.value = await api("/verification/events");
      });
  }
  function submitVerification() {
    run(async () => {
      await api("/verification", { method: "POST", body: verifyForm });
      await refresh();
      Object.keys(verifyForm).forEach((k) => (verifyForm[k] = ""));
      notice.value = "资料提交成功，审核通过后将开放操作权限。";
    });
  }
  function mockReview(approved) {
    run(async () => {
      await api("/dev/review", {
        method: "POST",
        body: { approved, reviewId: user.value.review_id },
      });
      await refresh();
    });
  }
  function publish() {
    run(async () => {
      await api("/jobs", { method: "POST", body: jobForm });
      showJob.value = false;
      Object.assign(jobForm, {
        category: "",
        requiredCount: 1,
        requirements: "",
        startsAt: "",
        durationMinutes: 240,
        title: "",
        description: "",
        location: "",
        pay: 120,
      });
      await refresh();
      notice.value = "岗位发布成功，领取端主页已可查看。";
    });
  }
  function apply(job) {
    run(async () => {
      await api(`/jobs/${job.id}/apply`, { method: "POST" });
      await loadJobs();
      notice.value = "领取成功，可在“我的领取”中查看。";
    });
  }
  function date(value) {
    return value
      ? new Date(value).toLocaleString("zh-CN", { hour12: false })
      : "暂无记录";
  }
  onMounted(async () => {
    try {
      dev.value = (await api("/config")).development;
      await refresh();
    } catch (e) {
      if (e.status !== 401) error.value = e.message;
    } finally {
      ready.value = true;
    }
    timer = setInterval(() => {
      if (cooldown.value > 0) cooldown.value--;
    }, 1000);
    poll = setInterval(async () => {
      if (user.value && document.visibilityState === "visible" && !busy.value) {
        try {
          await refresh();
        } catch (e) {
          if (e.status === 401) user.value = null;
        }
      }
    }, 15000);
  });
  onUnmounted(() => {
    clearInterval(timer);
    clearInterval(poll);
  });

  return {
    openJob,
    jobReturn,
    user,
    ready,
    dev,
    page,
    mode,
    busy,
    sending,
    notice,
    error,
    codeHint,
    cooldown,
    jobs,
    events,
    authForm,
    verifyForm,
    jobForm,
    showJob,
    search,
    statusNames,
    verified,
    canAct,
    filteredJobs,
    pending,
    profileUserId,
    selectedJobId,
    homeReturn,
    pageTitle,
    openUser,
    openApplicants,
    sessionExpired,
    run,
    refresh,
    switchRole,
    sendCode,
    submitAuth,
    logout,
    navigate,
    submitVerification,
    mockReview,
    publish,
    apply,
    date,
  };
}
