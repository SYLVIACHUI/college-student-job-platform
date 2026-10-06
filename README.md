# 贝鱼闲置-贝鱼儿自助二手交易平台

## 演示图

![主界面](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/login.png)
![发布界面](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/newjob.gif)
![领取界面](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/receive.gif)
![钱包](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/wallet.png)
![审核](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/review.png)
![审核](https://github.com/SYLVIACHUI/college-student-job-platform/blob/master/img_folder/identify.png)

## 运行方法

推荐使用 Docker Compose，一起启动前端 Nginx、后端、MySQL 和 Redis。安装并启动 Docker Desktop（Linux 容器）后，在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\init-docker-env.ps1
docker compose up -d --build --wait --wait-timeout 300
```

访问 `http://localhost:8088/publisher`、`/student`、`/admin`。默认是本地演示模式，提供模拟验证码和审核，并使用容器中的 MySQL、Redis。正式部署、管理员创建、日志与停止命令见 [Docker 部署说明](docs/Docker部署.md)。

也可以保留原本的 IDEA + 本机 MySQL、Redis、Nginx 启动方式：

在 IDEA 中打开 pom.xml 打开项目 运行后端'Application'后，在终端输入./scripts/frontend-nginx.ps1后，访问 'http://localhost:8088/publisher'、'/student' 或 '/admin'
后端仍在 IDEA 中运行于 8080,详细说明见 [Nginx 前端部署](docs/Nginx前端部署.md)。

## 技术栈与目录

- Java 17+、Spring Boot 3.5.16、Maven 多模块父工程。
- MyBatis 3.0.5 Starter：Mapper 接口在 `backend/src/main/java/cn/campus/jobs/mapper`，SQL 和实体映射在 `backend/src/main/resources/mapper`。
- Mapper 分工、事务与数据库升级说明见 [MyBatis 数据访问说明](docs/MyBatis数据访问说明.md)。
- 报名/录取使用 MySQL 原子条件更新扣减剩余名额，联合唯一约束防止重复报名，详见 [报名名额并发控制](docs/报名名额并发控制.md)。
- Vue 3 + Vite，发布端 `/publisher`、手机领取端 `/student`。
- 正式配置：MySQL + Redis；Flyway 自动迁移。
- `dev` 模式提供模拟验证码和审核；Docker 演示模式仍使用容器里的 MySQL、Redis。
- `backend/src/main/java/cn/campus/jobs`：根包保留 `Application.java`；`controller` 存放接口、异常处理与 Web 配置，`service` 存放业务服务及辅助组件，`mapper` 存放数据库操作、迁移配置与缓存适配器，`entity` 存放实体。
- `backend/src/main/resources/db/migration`：新库初始化，企业与学生直接分表；`db/legacy-migration` 保留已运行版本的原始脚本；`db/common-migration` 存放两种数据库共用的后续 SQL。
- `frontend/src`：双端页面、接口封装与响应式样式。
- `docs/API.md`：接口与状态说明。

### 完整演示流程

企业发布岗位时可选择“简历筛选”：学生提交在线简历后，由企业录取或不录取；岗位展示已报名、需要及已录取人数。操作流程和接口见 [简历筛选岗位说明](docs/简历筛选岗位说明.md)。

1. 选择发布端，点击“手机号注册”，填写测试手机号，获取页面展示的开发验证码，设置包含字母和数字的 8–64 位 ASCII 密码。
2. 注册完成后用手机号和密码登录。账号默认为手机号；账户信息页面仅展示脱敏手机号。
3. 实名认证填写单位名、姓氏、名字和身份证号。身份证做格式、日期、校验位检查，不能证明真实身份。仅使用测试资料进行演示；测试用号码见后端集成测试。
4. 提交后为“审核中”，发布权限仍为 0。开发环境可点击“模拟通过”或“模拟驳回”。驳回后可重新提交。
5. 模拟通过后发布岗位。
6. 在手机或另一个浏览器注册领取端账号，填写学校、姓名和学号，提交认证并模拟通过后领取岗位。同一手机号可分别注册两种角色。
7. 领取端首页每 15 秒刷新最新岗位，也支持手动刷新。同一岗位不能重复领取。

一个浏览器共享一个 HttpOnly 登录会话，切换身份会退出当前会话；双端同时测试请使用不同浏览器、隐私窗口或手机。Docker 数据库和 Redis 使用持久化数据卷；账号、岗位和报名记录保存在 MySQL，会话按有效期存放在 Redis。

## 数据模型与隐私

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

框架参考：[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Vue 官方快速开始](https://vuejs.org/guide/quick-start)。
项目讲解视频（未更新）可关注B站账号
