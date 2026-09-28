# 使用 Nginx 运行前端

Vue 源码仍在 `frontend/src` 中开发。`npm run build` 生成 `frontend/dist`，Nginx 提供这些静态文件，并把 `/api/` 请求转发到 Spring Boot 的 8080 端口。企业、学生、审核端共用一个 Nginx 站点。

## 当前 Windows 电脑

在 IDEA 中照常启动后端，沿用原 MySQL、Redis 和加密密钥配置。在项目根目录的 PowerShell 中运行：

```powershell
./scripts/frontend-nginx.ps1
```

脚本会构建 Vue、检查 Nginx 配置，再启动本站点。优先使用 PATH 中的 nginx.exe，也识别当前电脑 `D:/phpstudy_pro/Extensions/Nginx1.15.11/nginx.exe`。项目有自己的配置、日志和 PID，不修改 phpStudy 的站点配置。可以手动指定其他已安装版本：

```powershell
./scripts/frontend-nginx.ps1 -NginxExe 'D:/nginx/nginx.exe'
```

访问：

- 企业：`http://localhost:8088/publisher`
- 学生：`http://localhost:8088/student`
- 审核：`http://localhost:8088/admin`

使用 Nginx 时不需要运行 `npm run dev`。修改 Vue 源码后需要重新构建：

```powershell
./scripts/frontend-nginx.ps1 -Action reload
./scripts/frontend-nginx.ps1 -Action test
./scripts/frontend-nginx.ps1 -Action stop
```

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

## 手机访问与正式部署

当前配置仅监听本机，适合先在电脑上运行。手机局域网访问需要改为 `listen 8088`，配置网络访问，并使用 HTTPS。后端正常环境的登录 Cookie 带 Secure 属性，普通局域网 IP 的 HTTP 页面可能无法维持登录；不要为了上线而关闭它，也不要因此切换到会使用 H2 的 dev 环境。

正式部署应使用维护中的 Nginx 版本与域名证书：将 `root` 改为服务器上的 dist 目录，配置 443/SSL，80 跳转 HTTPS，保留 `/api/` 代理到仅内网可访问的 Spring Boot。当前脚本复用本机旧版 Nginx 用于本地运行，没有覆盖或升级 phpStudy 的组件。

官方说明：[Windows 版 Nginx](https://nginx.org/en/docs/windows.html)、[proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)、[try_files](https://nginx.org/en/docs/http/ngx_http_core_module.html#try_files)。
