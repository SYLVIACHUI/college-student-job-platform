# 使用 Nginx 运行前端

Vue 源码仍在 `frontend/src` 中开发。`npm run build` 生成 `frontend/dist`，Nginx 提供这些静态文件，并把 `/api/` 请求转发到 Spring Boot 的 8080 端口。企业、学生、审核端共用一个 Nginx 站点。

## 注意

以下操作均在项目根目录中运行，需自行配置 MYSQL Redis Nginx Java等

## 运行

./scripts/frontend-nginx.ps1

访问：

- 企业：`http://localhost:8088/publisher`
- 学生：`http://localhost:8088/student`
- 审核：`http://localhost:8088/admin`

## 修改 Vue 源码后需要重新构建：

./scripts/frontend-nginx.ps1 -Action reload
./scripts/frontend-nginx.ps1 -Action test
./scripts/frontend-nginx.ps1 -Action stop

已经构建且仅修改配置时，可以使用 `-Action reload -SkipBuild`。首次启动用 start，已启动用 reload。停止命令只使用本项目的 PID 文件。

## 配置说明

配置文件为 `deploy/nginx/nginx.conf`：

- `listen 127.0.0.1:8088`：本机前端端口，避免与 phpStudy 的 80 和后端 8080 冲突。
- `root ../../frontend/dist`：相对于脚本设置的 Nginx 前缀目录。
- `proxy_pass http://127.0.0.1:8080`：后端地址；末尾不要加 `/`，后端需要保留 `/api` 前缀。
- `/publisher`、`/student`、`/admin` 刷新时回退到 `index.html`。
- 带哈希文件名的 `/assets/` 使用长期缓存；HTML 重新验证；接口不缓存。
- 请求体限制 3 MB，与头像上传的后端请求上限一致。

日志位于 `deploy/nginx/logs/`。502 通常说明后端未启动或代理端口不一致；不要修改数据库配置来解决 502。缺失的静态资源返回 404，接口错误不会被转换成前端 HTML。
