<script setup>
import JobCard from "../../components/JobCard.vue";
import JobDetail from "../../views/JobDetail.vue";

import {
  Sprout,
  ArrowUpRight,
  ArrowRight,
  LayoutDashboard,
  BriefcaseBusiness,
  ShieldCheck,
  LogOut,
  Building2,
  Smartphone,
  Check,
  MapPin,
  Clock3,
  RefreshCw,
  ChevronRight,
  LockKeyhole,
  X,
} from "lucide-vue-next";
import ProfileCenter from "../../views/ProfileCenter.vue";
import UserHome from "../../views/UserHome.vue";
import HistoryPage from "../../views/HistoryPage.vue";
import WalletPage from "../../views/WalletPage.vue";
import UserAvatar from "../../components/UserAvatar.vue";
import { usePortal } from "../../composables/usePortal";
const {
  backDetail,
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
  apply,
  date,
} = usePortal("STUDENT");
</script>

<template>
  <div class="app-shell" :class="{ 'student-shell': true }">
    <aside class="sidebar">
      <a class="brand" href="/student"
        ><span class="brand-icon"><Sprout :size="25" /></span
        ><span>贝鱼<span class="brand-sub">校园兼职</span></span></a
      >
      <div class="workspace-label">学生成长空间</div>
      <nav>
        <button
          v-if="user"
          :class="{ active: page === 'profile' }"
          @click="navigate('profile')"
        >
          <UserAvatar
            :src="user.avatar_url"
            :name="user.display_name"
            :size="20"
          />个人中心
        </button>
        <button
          v-if="user"
          :class="{ active: page === 'history' || page === 'applicants' }"
          @click="navigate('history')"
        >
          <Clock3 :size="19" />接取历史
        </button>
        <button
          v-if="user"
          :class="{ active: page === 'wallet' }"
          @click="navigate('wallet')"
        >
          <span style="width: 19px; font-size: 19px">¥</span>我的钱包
        </button>
        <button :class="{ active: page === 'home' }" @click="navigate('home')">
          <LayoutDashboard :size="19" />发现兼职<span class="nav-dot" />
        </button>
        <button :class="{ active: page === 'jobs' }" @click="navigate('jobs')">
          <BriefcaseBusiness :size="19" />我的领取
        </button>
        <button
          :class="{ active: page === 'verify' }"
          @click="
            user ? navigate('verify') : (error = '请先登录，再进行实名认证')
          "
        >
          <ShieldCheck :size="19" />实名认证<span
            v-if="user && !verified"
            class="tiny-dot"
          />
        </button>
      </nav>
      <div class="side-note">
        <div class="note-sprout"><Sprout :size="30" /></div>
        <strong>让每一份努力，都有回响</strong>
        <p>连接校园与机会<br />从一份安心的兼职开始。</p>
        <span>GROW TOGETHER <ArrowUpRight :size="14" /></span>
      </div>
      <div class="side-footer">
        <ShieldCheck :size="16" /> 实名保障 · 安心连接
      </div>
    </aside>

    <div class="main-wrap">
      <header class="topbar">
        <div class="breadcrumb">
          贝鱼校园 <ChevronRight :size="14" /> <span>领取端</span>
        </div>
        <div class="top-actions">
          <div class="role-switch">
            <button
              :class="{ selected: false }"
              :disabled="busy"
              @click="switchRole('PUBLISHER')"
            >
              发布端</button
            ><button
              :class="{ selected: true }"
              :disabled="busy"
              @click="switchRole('STUDENT')"
            >
              <Smartphone :size="14" />领取端
            </button>
          </div>
          <span class="top-divider"></span
          ><button
            v-if="user"
            class="icon-button"
            aria-label="刷新数据"
            @click="run(refresh)"
          >
            <RefreshCw :size="18" /></button
          ><button
            v-if="user"
            class="personal-header-button"
            aria-label="个人中心"
            @click="navigate('profile')"
          >
            <UserAvatar
              :src="user.avatar_url"
              :name="user.display_name"
              :size="32"
            /><span class="top-account">{{ user.display_name }}</span></button
          ><span v-else class="avatar">学</span
          ><button
            v-if="user"
            class="icon-button"
            aria-label="退出登录"
            @click="logout"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main>
        <div v-if="dev" class="dev-label">
          本地演示环境 · 验证码可见 · 审核为开发模拟
        </div>
        <div v-if="error" class="alert error" role="alert">
          {{ error
          }}<button aria-label="关闭提示" @click="error = ''">
            <X :size="16" />
          </button>
        </div>
        <div v-if="notice" class="alert success" role="status">
          {{ notice
          }}<button aria-label="关闭提示" @click="notice = ''">
            <X :size="16" />
          </button>
        </div>
        <div v-if="!ready" class="loading">正在连接贝鱼校园…</div>

        <template v-else-if="!user">
          <div class="page-heading">
            <div>
              <div class="eyebrow">A LITTLE WORK. A BIG BEGINNING.</div>
              <h1>把课余时光，变成新的可能。</h1>
              <p>发现身边的靠谱兼职，让成长与收获一起发生。</p>
            </div>
            <span class="outline-tag"><span></span> 学生机会中心</span>
          </div>
          <div class="welcome-grid">
            <section class="hero-card">
              <div class="hero-top">
                <span class="hero-label">贝鱼 · 校园成长计划</span
                ><ArrowUpRight :size="22" />
              </div>
              <h2>走出课堂，<br />遇见更好的自己。</h2>
              <p>{{ "灵活的时间、真实的岗位，\n每一小步，都算成长。" }}</p>
              <div class="hero-art" aria-hidden="true">
                <div class="art-orbit orbit-one"></div>
                <div class="art-orbit orbit-two"></div>
                <div class="art-card card-back">
                  <span class="art-mini-line"></span
                  ><span class="art-mini-line short"></span>
                  <div class="art-bars"><i></i><i></i><i></i><i></i></div>
                </div>
                <div class="art-card card-front">
                  <span class="art-icon"><Sprout :size="40" /></span
                  ><strong>新的机会，正在萌芽</strong
                  ><small>YOUR NEXT CHAPTER</small>
                  <div class="art-check">
                    <Check :size="16" /> 实名认证 · 安心同行
                  </div>
                </div>
                <span class="floating-plus">+</span
                ><span class="floating-star">✳</span>
              </div>
              <div class="hero-bottom">
                <span class="hero-avatars"><i>贝</i><i>鱼</i><i>＋</i></span
                ><span>连接每一份值得期待的可能</span><ArrowRight :size="18" />
              </div>
            </section>
            <section class="auth-card">
              <span class="card-kicker">STUDENT ACCESS</span>
              <h2>
                {{ mode === "login" ? "欢迎回来" : "加入贝鱼校园"
                }}<span class="greeting-dot">.</span>
              </h2>
              <p>
                {{
                  mode === "login"
                    ? "登录账号，开启今天的新连接"
                    : "手机号注册，让机会从此触手可及"
                }}
              </p>
              <div class="auth-tabs">
                <button
                  :class="{ active: mode === 'login' }"
                  @click="
                    mode = 'login';
                    error = '';
                  "
                >
                  账号登录</button
                ><button
                  :class="{ active: mode === 'register' }"
                  @click="
                    mode = 'register';
                    error = '';
                  "
                >
                  手机号注册
                </button>
              </div>
              <form @submit.prevent="submitAuth">
                <label
                  >手机号
                  <input
                    v-model.trim="authForm.phone"
                    type="tel"
                    inputmode="numeric"
                    autocomplete="username"
                    placeholder="请输入11位手机号码"
                    pattern="1[3-9][0-9]{9}"
                    maxlength="11"
                    required /></label
                ><label v-if="mode === 'register'"
                  >短信验证码
                  <div class="code-input">
                    <input
                      v-model.trim="authForm.code"
                      inputmode="numeric"
                      autocomplete="one-time-code"
                      placeholder="6位验证码"
                      pattern="[0-9]{6}"
                      maxlength="6"
                      required
                    /><button
                      type="button"
                      :disabled="sending || cooldown > 0"
                      @click="sendCode"
                    >
                      {{
                        cooldown
                          ? cooldown + "秒后重试"
                          : sending
                            ? "发送中…"
                            : "获取验证码"
                      }}
                    </button>
                  </div></label
                >
                <div v-if="codeHint && mode === 'register'" class="debug-code">
                  开发验证码：<strong>{{ codeHint }}</strong
                  >（非真实短信）
                </div>
                <label
                  >密码<input
                    v-model="authForm.password"
                    type="password"
                    :autocomplete="
                      mode === 'login' ? 'current-password' : 'new-password'
                    "
                    :placeholder="
                      mode === 'register'
                        ? '8–64位，包含字母和数字'
                        : '请输入登录密码'
                    "
                    :minlength="mode === 'register' ? 8 : 1"
                    maxlength="64"
                    required
                /></label>
                <div class="form-info">
                  <LockKeyhole :size="13" /> 账号信息加密保护
                </div>
                <button class="primary auth-submit" :disabled="busy">
                  {{ busy ? "请稍候…" : mode === "login" ? "登 录" : "注 册"
                  }}<ArrowRight :size="17" />
                </button>
              </form>
              <div class="auth-foot">
                <ShieldCheck :size="16" /><span
                  >注册后完成实名认证，即可领取兼职</span
                >
              </div>
            </section>
          </div>
          <section class="steps-row">
            <article>
              <span class="step-icon"><Smartphone :size="22" /></span>
              <div>
                <small>01 / 创建账号</small>
                <h3>手机号快捷注册</h3>
                <p>一个手机号，开启新的连接</p>
              </div>
            </article>
            <article>
              <span class="step-icon"><ShieldCheck :size="22" /></span>
              <div>
                <small>02 / 实名认证</small>
                <h3>认证你的学生身份</h3>
                <p>真实身份，让每一次选择更安心</p>
              </div>
            </article>
            <article>
              <span class="step-icon"><BriefcaseBusiness :size="22" /></span>
              <div>
                <small>03 / 开始连接</small>
                <h3>领取你的第一份兼职</h3>
                <p>审核通过，解锁更多可能</p>
              </div>
            </article>
          </section>
        </template>

        <template v-else>
          <div class="page-heading">
            <div>
              <div class="eyebrow">YOUR CAMPUS, YOUR OPPORTUNITIES</div>
              <h1>{{ pageTitle }}</h1>
              <p>
                {{
                  page === "verify"
                    ? "真实的身份，是每一份信任的开始。"
                    : "欢迎来到贝鱼，让每一份努力都被看见。"
                }}
              </p>
            </div>
            <span class="status-pill" :class="{ approved: verified }"
              ><ShieldCheck :size="16" />{{
                statusNames[user.verification_status]
              }}</span
            >
          </div>
          <ProfileCenter
            v-if="page === 'profile'"
            :key="user.id"
            :user="user"
            @updated="user = $event"
            @home="openUser"
            @history="navigate('history')"
            @wallet="navigate('wallet')"
            @expired="sessionExpired"
          />
          <JobDetail
            v-else-if="page === 'jobDetail'"
            :key="selectedJobId"
            :job-id="selectedJobId"
            :user="user"
            :can-act="canAct"
            @back="backDetail"
            @home="openUser"
            @verify="navigate('verify')"
            @joined="run(refresh)"
            @expired="sessionExpired"
          />
          <UserHome
            @detail="openJob"
            v-else-if="page === 'userHome'"
            :user-id="profileUserId"
            :own-id="user.id"
            @back="backDetail"
            @edit="navigate('profile')"
            @expired="sessionExpired"
          />
          <HistoryPage
            @detail="openJob"
            v-else-if="page === 'history'"
            :key="user.id"
            :user="user"
            @home="openUser"
            @expired="sessionExpired"
          />
          <WalletPage
            v-else-if="page === 'wallet'"
            :key="user.id"
            :user="user"
            @expired="sessionExpired"
          />
          <template v-else-if="page === 'verify'">
            <div class="verification-layout">
              <section class="panel verification-panel">
                <div class="section-title">
                  <h2>学生身份认证</h2>
                  <span> 在校学生 </span>
                </div>
                <div class="progress-steps">
                  <span class="done">1 填写资料</span><i></i
                  ><span :class="{ done: pending || verified }">2 资料审核</span
                  ><i></i><span :class="{ done: verified }">3 开通权限</span>
                </div>
                <div v-if="pending || verified" class="review-state">
                  <span class="review-icon"
                    ><ShieldCheck v-if="verified" :size="42" /><Clock3
                      v-else
                      :size="42"
                  /></span>
                  <h2>{{ verified ? "身份认证已通过" : "你的资料已提交" }}</h2>
                  <p>{{ user.review_note }}</p>
                  <p v-if="pending">审核结果更新后，将在此处展示。</p>
                  <button
                    class="secondary"
                    :disabled="busy"
                    @click="run(refresh)"
                  >
                    <RefreshCw :size="15" />刷新状态
                  </button>
                  <div v-if="dev && pending" class="dev-review">
                    <strong>开发测试工具 · 非真实 AI 审核</strong>
                    <p>仅用于验证通过、驳回与权限控制流程。</p>
                    <button
                      class="secondary"
                      :disabled="busy"
                      @click="mockReview(true)"
                    >
                      模拟通过</button
                    ><button
                      class="secondary"
                      :disabled="busy"
                      @click="mockReview(false)"
                    >
                      模拟驳回
                    </button>
                  </div>
                </div>
                <form
                  v-else
                  class="verification-form"
                  @submit.prevent="submitVerification"
                >
                  <div
                    v-if="user.verification_status === 'REJECTED'"
                    class="rejection"
                  >
                    {{ user.review_note }}
                  </div>
                  <label
                    >学校名称<input
                      v-model.trim="verifyForm.school"
                      placeholder="请输入学校完整名称"
                      maxlength="150"
                      required /></label
                  ><label
                    >真实姓名<input
                      v-model.trim="verifyForm.name"
                      placeholder="请输入你的真实姓名"
                      maxlength="80"
                      required /></label
                  ><label
                    >学号<input
                      v-model.trim="verifyForm.studentNumber"
                      placeholder="请输入学校学号"
                      maxlength="50"
                      required /></label
                  ><label
                    >邮箱 <span class="optional">选填</span
                    ><input
                      v-model.trim="verifyForm.email"
                      type="email"
                      placeholder="用于后续联系"
                      maxlength="254"
                  /></label>
                  <p class="privacy-note">
                    <LockKeyhole
                      :size="16"
                    />姓名、证件号码、学号等敏感资料加密保存，提交后不回显原文。
                  </p>
                  <button class="primary" :disabled="busy">
                    {{ busy ? "正在提交…" : "提交认证资料"
                    }}<ArrowRight :size="17" />
                  </button>
                </form>
              </section>
              <aside class="panel info-panel">
                <span class="step-icon"><ShieldCheck :size="25" /></span>
                <h3>为每一次连接，多一份保障</h3>
                <p>请填写本人真实资料。学校名称和学号请与在校信息一致。</p>
                <ul>
                  <li>提交后进入待审核状态</li>
                  <li>审核通过后开放领取权限</li>
                  <li>审核未通过可修改并重新提交</li>
                </ul>
                <div class="integration-note">
                  审核服务尚待接入，正式环境不会自动通过。
                </div>
                <h4>认证记录</h4>
                <div v-if="!events.length" class="muted">暂无提交记录</div>
                <div v-for="event in events" :key="event.id" class="event">
                  <strong>{{ statusNames[event.status] }}</strong
                  ><small>{{ date(event.created_at) }}</small>
                  <p>{{ event.note }}</p>
                </div>
              </aside>
            </div>
          </template>
          <template v-else>
            <div v-if="!canAct" class="verify-banner">
              <span class="banner-icon"><ShieldCheck :size="28" /></span>
              <div>
                <h3>
                  {{
                    pending
                      ? "认证审核中，请耐心等待"
                      : verified
                        ? "当前操作权限尚未开放"
                        : "完成实名认证，开启更多机会"
                  }}
                </h3>
                <p>你可以浏览岗位，领取前需通过身份审核并开通权限。</p>
              </div>
              <button class="secondary" @click="navigate('verify')">
                {{ pending ? "查看进度" : "查看认证"
                }}<ArrowUpRight :size="16" />
              </button>
            </div>
            <div class="stats-grid">
              <article class="panel stat">
                <span>可浏览岗位</span
                ><strong
                  >{{ jobs.length.toString().padStart(2, "0")
                  }}<BriefcaseBusiness :size="23" /></strong
                ><small>最新100条兼职机会</small>
              </article>
              <article class="panel stat">
                <span>已领取岗位</span
                ><strong
                  >{{
                    jobs
                      .filter((j) => j.applied)
                      .length.toString()
                      .padStart(2, "0")
                  }}<Check :size="23" /></strong
                ><small>每一份连接，都是新的开始</small>
              </article>
              <article class="panel stat">
                <span>账号状态</span
                ><strong class="status-text"
                  >{{ statusNames[user.verification_status]
                  }}<ShieldCheck :size="23" /></strong
                ><small>上次登录：{{ date(user.last_login_at) }}</small>
              </article>
            </div>
            <section class="panel jobs-panel">
              <div class="section-title">
                <div>
                  <h2>{{ page === "jobs" ? "我的领取" : "最新兼职" }}</h2>
                  <p>每15秒自动更新，让好机会及时抵达。</p>
                </div>
                <button
                  class="secondary"
                  :disabled="busy"
                  @click="run(refresh)"
                >
                  <RefreshCw :size="16" />刷新
                </button>
              </div>
              <input
                v-model="search"
                class="search"
                placeholder="搜索标题、类别或发布单位"
                aria-label="搜索岗位"
              />
              <div class="job-list">
                <JobCard
                  v-for="job in filteredJobs.filter(
                    (j) => page !== 'jobs' || j.applied,
                  )"
                  :key="job.id"
                  :job="job"
                  @open="openJob"
                  @home="openUser"
                />
              </div>
              <div
                v-if="
                  !filteredJobs.filter((j) => page !== 'jobs' || j.applied)
                    .length
                "
                class="empty-state"
              >
                <BriefcaseBusiness :size="38" />
                <h3>
                  {{
                    search
                      ? "没有找到匹配的岗位"
                      : page === "jobs"
                        ? "还没有领取兼职"
                        : "新的机会正在路上"
                  }}
                </h3>
                <p>
                  {{
                    search
                      ? "试试其他关键词。"
                      : "发布端发布岗位后，会在这里显示。"
                  }}
                </p>
              </div>
            </section>
          </template>
        </template>
        <footer class="page-footer">
          <span>© {{ new Date().getFullYear() }} 夏倪 · 让成长有迹可循</span
          ><span
            ><LockKeyhole :size="13" />
            我们将严格按照《中华人民共和国个人信息保护法》及相关法律法规的要求处理您的个人信息。您的个人信息将被认真保密，我们不会将其用于与您使用本服务无关的目的。
          </span>
        </footer>
      </main>
    </div>
    <nav v-if="user" class="mobile-nav">
      <button @click="navigate('home')" :class="{ active: page === 'home' }">
        <LayoutDashboard :size="21" />发现</button
      ><button
        @click="navigate('history')"
        :class="{ active: page === 'history' }"
      >
        <BriefcaseBusiness :size="21" />接取记录</button
      ><button
        @click="navigate('wallet')"
        :class="{ active: page === 'wallet' }"
      >
        <span style="font-size: 20px; line-height: 21px">¥</span>钱包</button
      ><button
        @click="navigate('profile')"
        :class="{ active: page === 'profile' }"
      >
        <UserAvatar
          :src="user.avatar_url"
          :name="user.display_name"
          :size="21"
        />我的</button
      ><button
        @click="navigate('verify')"
        :class="{ active: page === 'verify' }"
      >
        <ShieldCheck :size="21" />认证
      </button>
    </nav>
  </div>
</template>
