# Docker 启动与部署

项目使用 Docker Compose 启动四个服务：MySQL 8.4、Redis 7.4、Spring Boot 后端、Nginx 前端。前后端在镜像中构建，本机无需安装 Java、Maven、Node.js、MySQL、Redis 或 Nginx。需要 Docker Engine/Desktop 和 Docker Compose v2，Windows 上使用 Linux 容器。

## Windows 本地启动

1. 安装并启动 [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/)，确认 `docker version`、`docker compose version` 能执行。
2. 在 PowerShell 中执行：

```powershell
cd D:\college_student_job_platform
powershell -ExecutionPolicy Bypass -File .\scripts\init-docker-env.ps1
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
```

初始化脚本会从 `.env.example` 生成 `.env`，创建独立随机密码和密钥。已有 `.env` 时保留原文件；如果它来自旧配置，请补齐 `.env.example` 中新增的字段。首次构建需要联网下载基础镜像、Maven 和 npm 依赖。`--wait` 等待服务健康；如果 Compose 版本不支持该选项，可以使用 `docker compose up -d --build`，随后通过 `docker compose ps` 检查状态。

数据库、Redis 的账号和密码统一在根目录 `.env` 配置，不需要编辑两个 `application*.yml` 文件：

| 环境变量 | 用途 |
| --- | --- |
| `DB_NAME` | MySQL 数据库名称，默认 `campusjobs` |
| `DB_USERNAME` | 后端使用的数据库用户，当前配置为 `root` |
| `DB_PASSWORD` | 该数据库用户的密码，MySQL 初始化和后端连接共用 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 初始化密码；使用 root 连接时必须与 `DB_PASSWORD` 一致 |
| `REDIS_PASSWORD` | Redis 服务密码和后端连接密码，共用同一个值 |
| `REDIS_DATABASE`、`REDIS_TIMEOUT` | Redis 数据库编号和连接超时 |
| `APP_ENCRYPTION_KEY`、`APP_LOOKUP_KEY` | 数据加密和查询密钥，由初始化脚本生成 |

Compose 将这些值传入容器，Spring Boot 从环境变量读取。Docker 中的数据库、缓存地址固定为服务名 `mysql`、`redis`；`.env` 中的 `DB_HOST`、`DB_PORT`、`REDIS_HOST`、`REDIS_PORT` 是本机运行时可导入的连接设置。在 IDEA 中单独启动时，需要将 `.env` 配置导入运行环境，Spring Boot 不会自动读取这个文件。

访问：

- 企业端：[http://localhost:8088/publisher](http://localhost:8088/publisher)
- 学生端：[http://localhost:8088/student](http://localhost:8088/student)
- 审核后台：[http://localhost:8088/admin](http://localhost:8088/admin)

默认 `.env` 设置 `APP_MODE=dev`，适合本机演示：注册页面显示模拟验证码，实名认证页面提供模拟审核。这个模式仍使用 Docker MySQL 和 Redis，不使用 H2 或内存会话。企业和学生可以自行注册，管理员账号需要按下文显式创建。

如果之前使用项目的本地 Nginx 占用了 8088，先执行 `powershell -ExecutionPolicy Bypass -File .\scripts\frontend-nginx.ps1 -Action stop`。也可在初始化时用 `-Port 8090`，或者修改 `.env` 中的 `WEB_PORT=8090`；之后用新端口访问。容器中的 MySQL、Redis、后端仅在 Compose 网络中访问，不占本机 3306、6379 或 8080。

## 日常命令

```powershell
# 查看状态和日志
docker compose ps
docker compose logs -f --tail=100 backend frontend

# 修改代码后重新构建、启动
docker compose up -d --build --wait --wait-timeout 300

# 停止并移除容器，保留数据库和 Redis 数据卷
docker compose down

# 再次启动
docker compose up -d --wait --wait-timeout 300
```

MySQL 数据保存在 `mysql-data` 卷，Redis AOF 数据保存在 `redis-data` 卷。Flyway 在后端启动时自动创建、升级数据库，包含简历筛选功能的 V9 迁移和原子扣减剩余名额的 V10 迁移。普通 `down` 会保留这些数据；`down -v` 会删除数据卷，不能作为日常停止命令使用。

保留 `.env` 并与数据库一起备份。账号、实名资料、简历等已加密数据依赖原有 `APP_ENCRYPTION_KEY`，账号及会话索引依赖 `APP_LOOKUP_KEY`，已有数据时不要重新生成这两个密钥。已有 MySQL 卷的数据库用户密码也不会因修改 `.env` 自动更新。

## 创建审核管理员

在 PowerShell 中读取 `.env` 的管理员配置，再执行一次性创建命令：

```powershell
$adminConfig = @{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^(REVIEW_ADMIN_USERNAME|REVIEW_ADMIN_PASSWORD)=(.+)$') {
        $adminConfig[$matches[1]] = $matches[2]
    }
}
if (!$adminConfig.REVIEW_ADMIN_USERNAME -or !$adminConfig.REVIEW_ADMIN_PASSWORD) {
    throw '请先在 .env 中设置管理员用户名和密码'
}
$env:REVIEW_ADMIN_USERNAME = $adminConfig.REVIEW_ADMIN_USERNAME
$env:REVIEW_ADMIN_PASSWORD = $adminConfig.REVIEW_ADMIN_PASSWORD
try {
    docker compose run --rm --no-deps -e REVIEW_ADMIN_USERNAME -e REVIEW_ADMIN_PASSWORD backend --spring.main.web-application-type=none --app.bootstrap-review-admin=true
} finally {
    Remove-Item Env:REVIEW_ADMIN_USERNAME, Env:REVIEW_ADMIN_PASSWORD
}
```

需先启动 MySQL 和 Redis。创建程序完成后退出，不覆盖现有账号，也不重置已有管理员的密码。使用 `.env` 中的管理员账号登录 `/admin`，可处理实名认证。

## 查看管理员、企业和学生数据库

先在项目根目录打开 PowerShell，查看容器状态：

```powershell
cd D:\college_student_job_platform
docker compose ps
```

确认 `mysql` 服务正在运行，然后进入容器里的 MySQL：

```powershell
docker compose exec mysql mysql -u root -p
```

出现 `Enter password:` 时，输入 `.env` 中 `MYSQL_ROOT_PASSWORD` 对应的密码并按回车。输入时不会显示字符，这是正常的。登录后会出现 `mysql>`，下面的 SQL 命令都在这个提示符后执行，每条语句以分号结束。

### 进入项目数据库

```sql
-- 查看数据库列表
SHOW DATABASES;

-- 进入项目数据库
USE campusjobs;

-- 查看项目中的所有表
SHOW TABLES;
```

如果 `.env` 中的 `DB_NAME` 不是 `campusjobs`，将 `USE campusjobs;` 改为你的数据库名称。如果找不到项目数据库，先检查 MySQL、后端是否正常启动，以及后端的 Flyway 迁移是否成功。

### 查看管理员账号

```sql
SELECT id, username, display_name
FROM review_admin;
```

管理员账号保存在 `review_admin` 表。密码采用 BCrypt 单向哈希保存，不能通过数据库查看原始登录密码。

### 查看企业和学生用户

```sql
-- 查看企业用户，最多显示 20 条
SELECT id, organization, verification_status, created_at
FROM publisher_user
LIMIT 20;

-- 查看学生用户，最多显示 20 条
SELECT id, nickname, school, verification_status, created_at
FROM student_user
LIMIT 20;
```

`verification_status` 是实名认证状态：`UNVERIFIED` 为未认证，`PENDING` 为待审核，`APPROVED` 为已通过，`REJECTED` 为未通过。手机号、真实姓名、身份证、学号和简历等敏感资料经过加密，数据库不会直接显示其原文。

### 查看表结构和退出

```sql
-- 查看管理员表有哪些字段
DESCRIBE review_admin;

-- 查看企业和学生表有哪些字段
DESCRIBE publisher_user;
DESCRIBE student_user;

-- 退出 MySQL，返回 PowerShell
EXIT;
```

以上命令只查看数据和表结构，不会修改数据库。

## 正式部署

本地演示完成后，在正式服务器配置独立的 `.env`：

```dotenv
APP_MODE=prod
WEB_BIND_ADDRESS=127.0.0.1
APP_SECURE_COOKIE=true
SMS_GATEWAY_URL=https://your-sms-gateway.example/send
SMS_GATEWAY_TOKEN=your-gateway-token
```

正式模式关闭模拟验证码和模拟审核。短信网关需要支持项目已有协议：HTTPS POST JSON `phone`、`code` 字段，`Authorization: Bearer <token>` 认证，成功返回 2xx。然后创建审核管理员。未配置真实短信时，正式模式不能发送注册验证码。

通过服务器上的 HTTPS 反向代理转发到 `127.0.0.1:8088`；`APP_SECURE_COOKIE=true` 需要浏览器使用 HTTPS，否则登录 Cookie 不会发送。本地 HTTP 演示使用 `false`。如果需要局域网访问，修改 `WEB_BIND_ADDRESS=0.0.0.0` 并按服务器地址访问；对外公开前必须切换 `APP_MODE=prod`。钱包目前仍是模拟结算。

Linux 可复制 `.env.example` 到 `.env`，用 `openssl rand -base64 32` 生成加密密钥，用 `openssl rand -hex 32` 分别生成查询密钥及其他密码，填好后执行相同的 `docker compose` 命令。

## 常见排查

- 提示缺少环境变量：检查根目录 `.env`，不要直接用示例中的占位值。
- 端口已被占用：停止原 Nginx，或更改 `WEB_PORT`。
- 服务启动超时：用 `docker compose logs --tail=100 mysql redis backend` 查看原因，再运行 `docker compose up -d --wait --wait-timeout 300`。
- 镜像或依赖下载失败：检查 Docker Desktop 的网络、代理或镜像源，再重新构建。
- 原本机 MySQL/H2 数据不会自动导入 Docker 的新数据卷；需要保留原数据时，先备份并显式导入，并沿用原加密、查询密钥。

Compose 使用 `service_healthy` 等待依赖可用后启动后端与前端，详见 [Docker 官方启动顺序说明](https://docs.docker.com/compose/how-tos/startup-order/)。`.env` 通过 Compose 的 `environment` 配置传入各容器，详见 [环境变量配置说明](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)。
