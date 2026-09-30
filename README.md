# 贝鱼校园兼职平台

## 演示图

![主界面](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/login.png)
![发布界面](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/newjob.gif)
![领取界面](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/receive.gif)

前端支持 Nginx 部署：在项目根目录运行 `./scripts/frontend-nginx.ps1`，访问 `http://localhost:8088/publisher`、`/student` 或 `/admin`，后端仍在 IDEA 中运行于 8080。详细说明见 [Nginx 前端部署](docs/Nginx前端部署.md)。

新增个人中心、用户主页、双端历史、岗位接取名单及模拟钱包，使用方式见 [个人中心与钱包](docs/个人中心与钱包.md)。继续使用当前 MySQL/Redis 配置，重启后端自动迁移数据库。模拟充值、发放和提现不涉及真实资金。

第一阶段实现：两类用户、手机号注册登录、实名认证提交/状态流转、发布/领取权限控制，以及一个可验证双端联动的基础岗位流程。

## 技术栈与目录

- Java 17+、Spring Boot 3.5.16、Maven 多模块父工程。
- Vue 3 + Vite，发布端 `/publisher`、手机领取端 `/student`。
- 正式配置：MySQL + Redis；Flyway 自动迁移。
- 显式 `dev` 配置：H2 文件库 + 内存验证码/会话，免数据库安装进行本地演示。
- `backend/src/main/java/cn/campus/jobs`：接口、认证服务、认证审核状态机、加密、持久化与缓存适配器。
- `backend/src/main/resources/db/migration`：新库初始化，企业与学生直接分表；`db/legacy-migration` 保留已运行版本的原始脚本；`db/common-migration` 存放两种数据库共用的后续 SQL。
- `frontend/src`：双端页面、接口封装与响应式样式。
- `docs/API.md`：接口与状态说明。

领取端当前是 Vue 手机网页（H5），可在手机浏览器使用；本阶段没有打包 Android APK 或 iOS 安装包。前后台共享 API，后续可用 Capacitor 或 uni-app 扩展客户端。

## 快速启动（本地演示）

需要 Java 17–25、Maven 3.6.3+、Node.js 22.12+（本次在 Java 25 / Node 26 验证）。

在项目根目录运行后端：

```powershell
./scripts/start-dev.ps1
```

此脚本优先使用 PATH 中的 Maven，也识别当前电脑的 IDEA 内置 Maven。或者直接运行：

```powershell
mvn -pl backend spring-boot:run "-Dspring-boot.run.profiles=dev"
```

在另一个终端运行前端：

```powershell
cd frontend
npm ci
npm run dev
```

- 发布端：<http://localhost:5173/publisher>
- 领取端：<http://localhost:5173/student>
- 后端：<http://localhost:8080/api/config>

手机与电脑连接同一局域网后，将 `localhost` 替换成电脑局域网 IP，访问 `http://电脑IP:5173/student`。Vite 将 `/api` 代理至后端。若系统防火墙拦截，需要允许本地开发端口。

### 完整演示流程

1. 选择发布端，点击“手机号注册”，填写测试手机号，获取页面展示的开发验证码，设置包含字母和数字的 8–64 位 ASCII 密码。
2. 注册完成后用手机号和密码登录。账号默认为手机号；账户信息页面仅展示脱敏手机号。
3. 实名认证填写单位名、姓氏、名字和身份证号。身份证做格式、日期、校验位检查，不能证明真实身份。仅使用测试资料进行演示；测试用号码见后端集成测试。
4. 提交后为“审核中”，发布权限仍为 0。开发环境可点击“模拟通过”或“模拟驳回”。驳回后可重新提交。
5. 模拟通过后发布岗位。
6. 在手机或另一个浏览器注册领取端账号，填写学校、姓名和学号，提交认证并模拟通过后领取岗位。同一手机号可分别注册两种角色。
7. 领取端首页每 15 秒刷新最新岗位，也支持手动刷新。同一岗位不能重复领取。

一个浏览器共享一个 HttpOnly 登录会话，切换身份会退出当前会话；双端同时测试请使用不同浏览器、隐私窗口或手机。演示数据库位于后端运行目录的 `data/campus.mv.db`；重启后账号与岗位保留，内存验证码和登录会话失效。

## MySQL / Redis 配置

默认配置就是 MySQL / Redis，运行时不要启用 `dev`。可自行准备实例，也可安装 Docker 后启动本项目的数据库容器：

```powershell
Copy-Item .env.example .env
# 修改 .env 中两个数据库密码后：
docker compose up -d
```

设置后端进程环境变量（`.env` 仅供 Docker Compose 使用，Spring 不会自动读取它）：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/campus_jobs?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USER='campus'
$env:DB_PASSWORD='与数据库配置一致'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
# 如 Redis 启用了密码：$env:REDIS_PASSWORD='你的Redis密码'
$encryptionBytes = [byte[]]::new(32)
$lookupBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($encryptionBytes)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($lookupBytes)
$env:APP_ENCRYPTION_KEY=[Convert]::ToBase64String($encryptionBytes)
$env:APP_LOOKUP_KEY=[Convert]::ToBase64String($lookupBytes)
mvn -pl backend spring-boot:run
```

密钥首次生成后应保存到部署环境的密钥管理系统并在重启时复用，不能每次启动重新生成。更换加密密钥需要迁移历史密文；更换索引密钥需要重建账号索引。

正式环境会话 Cookie 为 `Secure; HttpOnly; SameSite=Strict`，前端和 `/api` 应通过同一 HTTPS 域名反向代理。不要在正式环境启用 `dev`。Compose 示例仅将数据库绑定到本机回环地址；生产 Redis 应配置访问控制。

前端部署：执行 `npm run build`，将 `frontend/dist` 交给 Nginx 等静态服务器，配置 `/publisher`、`/student` 回退到 `index.html`，`/api` 反向代理到后端。后端打包执行 `mvn package` 后运行 `java -jar backend/target/backend-0.1.0.jar`。

## 短信与审核接入

### 短信

开发验证码只在 `dev` 下返回，无固定通用验证码，不写日志。正式实现是 `WebhookSmsSender`，通过你自有的 HTTPS 短信网关对接短信供应商：

```text
SMS_GATEWAY_URL=https://你的网关/sms/send
SMS_GATEWAY_TOKEN=网关鉴权令牌
```

请求使用 `Authorization: Bearer ...`，JSON 为 `{"phone":"手机号","code":"六位验证码","expiresIn":300}`。网关必须在供应商确认接受短信后返回 2xx。也可以实现 `SmsSender` 替换成阿里云/腾讯云 SDK。未配置网关时正式环境返回 503，不会偷偷使用演示验证码。

验证码有效期 5 分钟、一次性消费；同一身份/手机号 60 秒限发一次、每天最多 10 次；验证码校验限制每 5 分钟 5 次；同时限制来源 IP 和登录尝试。多实例限流由 Redis Lua 保证原子性。反向代理部署时需要按受信任代理配置真实客户端 IP，不能直接信任客户端传入的转发头。

### AI 审核（待接入）

当前已实现认证资料存储、提交记录、待审核/通过/驳回状态机与审核结果落库边界，没有调用任何外部 AI，也没有把格式检查当成实名认证。

待审核任务由 `publisher_user` / `student_user` 的 `verification_status=PENDING` 与 `review_id` 定位，`verification_event` 记录历史。未来受信任的审核 worker 读取资料并通过 `VerificationService.applyDecision(userId, reviewId, approved, note)` 应用结果。该方法会锁行并校验任务版本，拒绝重复或过期结果。`note` 最多 500 字符且不能包含敏感原文。

真实 AI 服务、提示词、证件/学校核验来源、人工复核规则及异步任务重试由后续阶段接入。现在正式环境提交认证后保持待审核。开发模拟审核控制器仅在 `dev` Profile 注册，且只能作用于当前用户，不是管理后台或正式审核接口。

## 数据模型与隐私

企业与学生现已分为独立实体、数据库表和前端入口，文件位置及升级步骤见 [企业与学生分表说明](docs/企业与学生分表说明.md)。

| 需求字段 | 存储方式 |
| --- | --- |
| 单位名、姓氏、学校名、邮箱 | `publisher_user` / `student_user` 对应字段，邮箱可选 |
| 发布人名字 / 学生姓名 | AES-256-GCM `name_cipher` |
| 身份证号 | AES-256-GCM `identity_cipher` |
| 学号 | AES-256-GCM `student_number_cipher` |
| 手机号（同时为默认账号） | AES-256-GCM `phone_cipher`，随机 nonce |
| 账号索引 | 独立密钥的 HMAC-SHA256 `account_hash`，避免账号字段重复保存明文手机号 |
| 密码 | BCrypt 单向哈希，不可解密 |
| 发布事件 | `job` 表关联发布人、岗位及发布时间，主页显示发布数量 |
| 上次登录时间 | `last_login_at`，成功登录后更新 |
| 发布/领取状态 0/1 | `can_publish` / `can_accept`，默认 0，通过审核后仅开启对应角色权限 |
| 审核历史 | `verification_event`，关联用户、状态、审核说明与时间 |

会话令牌为随机 256 位值，通过 HttpOnly Cookie 传输，缓存中只用令牌 HMAC 作索引，12 小时过期。退出后立即失效。前端不存储令牌，个人资料响应不包含原始姓名、身份证、学号、密码哈希和密文。

## 验证

```powershell
mvn test
cd frontend
npm run build
```

当前已通过 14 项后端集成测试、前端构建及后端 JAR 打包：覆盖原有注册/认证/岗位权限，以及新个人资料、头像、主页隐私、历史记录、模拟钱包、幂等结算和并发余额保护。详情见个人中心与钱包说明。

浏览器联调也已实际跑通发布端注册/登录/认证/发布岗位，以及领取端登录/认证/看到新岗位/领取/查看我的领取。已检查 390×844 手机布局。演示库中保留了明确标记为“演示”的测试单位、学生与岗位，均不是真实招聘数据。

自动化测试使用 H2 和内存缓存。已在当前 MySQL/Redis 实例上验证新迁移、两类测试账号登录，以及个人资料、主页、历史和钱包读取；模拟资金的写入与并发流程由隔离测试库验证，没有向现有用户钱包填充测试余额。真实资金服务尚未接入。

框架参考：[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Vue 官方快速开始](https://vuejs.org/guide/quick-start)。
