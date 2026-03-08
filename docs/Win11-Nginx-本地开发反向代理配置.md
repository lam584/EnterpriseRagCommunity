# Win11 本地开发：为 EnterpriseRagCommunity 配置 Nginx（无 SSL）

本文档面向 Win11 本地开发/测试：用 Nginx 给“前端 + 后端”提供一个统一入口（例如 `http://localhost/`），避免跨端口带来的 CORS/调试不便。不包含 SSL 配置。

## 端口与路径约定（来自项目配置）

- 后端（Spring Boot）：`http://127.0.0.1:8099`
  - 端口：`server.port=8099`
  - 上传：URL 前缀 `/uploads`
  - API：多数 Controller 以 `/api/...` 作为入口
- 前端（Vite dev server）：默认 `http://127.0.0.1:5173`
  - 未显式指定端口时，Vite 默认是 5173
  - dev 期代理：`/api`、`/uploads`、`/admin` -> `http://localhost:8099`

## 选择一种本地方案

### 方案 A（推荐）：Nginx 反代到 Vite，API 直连后端（开发态热更新）

适用场景：你在本地跑 `npm run dev` 做前端开发，同时跑后端；希望浏览器始终访问 `http://localhost/`，并且保留 Vite 的 HMR（热更新）。

把下面这个 `server { ... }` 放进你的 `conf/nginx.conf` 的 `http { ... }` 里（替换原来的 `server` 块即可）：

```nginx
server {
    listen 80;
    server_name localhost;

    client_max_body_size 2048g;

    location /api/ {
        proxy_pass http://127.0.0.1:8099/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_connect_timeout 60s;
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;
    }

    location /uploads/ {
        proxy_pass http://127.0.0.1:8099/uploads/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_connect_timeout 60s;
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;
    }

    location /admin/ {
        proxy_pass http://127.0.0.1:8099/admin/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:5173/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
```


你需要同时启动：

- 后端：监听 8099
- 前端：`my-vite-app` 的 dev server（默认 5173）

### 方案 B：Nginx 直接托管前端 dist，API 反代后端（更接近“部署形态”）

适用场景：你不需要热更新，只想本地用 Nginx 跑一个“接近上线”的形态。

前提：先构建前端，使 `my-vite-app/dist` 存在（后端也能自己托管 dist，见“方案 C”）。

把下面配置放进 Nginx（注意 Windows 路径建议用正斜杠 `/`）：

```nginx
    server {
    listen 80;
    server_name localhost;

    client_max_body_size 2048g;

    location / {
        root E:/EnterpriseRagCommunity-main/my-vite-app/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8099/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_connect_timeout 60s;
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
    }

    location /uploads/ {
        proxy_pass http://127.0.0.1:8099/uploads/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_http_version 1.1;
        proxy_connect_timeout 60s;
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;
    }
```

#### 方案 B（增强版，推荐用于大文件上传）：upstream keepalive 复用到 8099 的连接

你已经遇到过 `connect() failed (10060) while connecting to upstream` 这类 502，它属于“连接上游失败/连接超时”。在 Win11 本地 + 大文件分片上传场景下，一个很有效的缓解手段是：把后端配置成 `upstream`，开启 `keepalive`，并确保到上游使用 HTTP/1.1 且不强制 `Connection: close`。

把下面片段合并到你的 `conf/nginx.conf`（注意：这个是完整示例，包含 `worker_processes/events/http/server`，你也可以只拷贝其中的 `upstream` + `location /api/`、`location /uploads/` 部分）：

```nginx
worker_processes auto;

events {
    worker_connections 8192;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65;

    upstream erc_backend {
        server 127.0.0.1:8099;
        keepalive 64;
    }

    server {
        listen 80;
        server_name localhost;

        client_max_body_size 2048g;

        location / {
            root E:/EnterpriseRagCommunity-main/my-vite-app/dist;
            index index.html;
            try_files $uri $uri/ /index.html;
        }

        location /api/ {
            proxy_pass http://erc_backend/api/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header X-Forwarded-Host $host;
            proxy_set_header X-Forwarded-Port $server_port;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_connect_timeout 60s;
            proxy_buffering off;
            proxy_request_buffering off;
            proxy_send_timeout 3600s;
            proxy_read_timeout 3600s;
        }

        location /uploads/ {
            proxy_pass http://erc_backend/uploads/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header X-Forwarded-Host $host;
            proxy_set_header X-Forwarded-Port $server_port;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_connect_timeout 60s;
            proxy_buffering off;
            proxy_request_buffering off;
            proxy_send_timeout 3600s;
            proxy_read_timeout 3600s;
        }
    }
}
```

同样的配置我也单独保存了一份，方便你直接复制： [nginx.win11.local.keepalive.conf](file:///e:/EnterpriseRagCommunity-main/docs/nginx.win11.local.keepalive.conf)。

参考：仓库里已有 Linux 部署示例 [enterprise-rag.static-and-api.conf](file:///e:/EnterpriseRagCommunity-main/deploy/nginx/enterprise-rag.static-and-api.conf)。

### 方案 C：Nginx 只反代到后端 8099（最省事，但依赖后端托管前端 dist）

适用场景：你愿意用后端来提供前端静态文件（项目里 `SpaController` 会从 `my-vite-app/dist` 提供资源与 SPA 回退）。

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        proxy_pass http://127.0.0.1:8099;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```


## 常用命令行（PowerShell）

### Nginx（在 Nginx 安装目录执行）

```powershell
# 测试配置是否可用
nginx -t

# 指定配置文件测试
nginx -t -c "E:\nginx-1.29.5\conf\nginx.conf"

# 重载配置（不中断连接地热重载）
nginx -s reload

# 快速停止（可能会中断连接）
nginx -s stop

# 优雅退出（等待连接处理完成）
nginx -s quit

# 查看版本与编译参数
nginx -V
```

### 快速连通性验证

```powershell
# 通过 Nginx 入口访问（注意端口按你的 listen 配置调整）
curl -i http://localhost/

# 验证后端 API 是否可达（初始化状态接口）
curl -i http://localhost/api/setup/status

# 验证上传路径是否被正确反代（会返回 404 或文件内容，重点看不是 502）
curl -i http://localhost/uploads/
```

## 502（connect() failed 10060）专项排查

如果 Nginx `error.log` 出现类似：

```
connect() failed (10060) while connecting to upstream, upstream: "http://127.0.0.1:8099/..."
```

含义是：Nginx 在尝试与后端 8099 建立 TCP 连接时超时/失败，属于“连接上游失败”，不是后端业务正常返回。

建议按优先级排查：

- 先确认后端是否活着：失败瞬间访问 `http://127.0.0.1:8099/api/setup/status` 是否会卡住/超时。
- 若后端偶发卡死：重点查看后端日志是否有 OOM、长时间 GC、磁盘写入阻塞（大文件上传更容易触发）。
- 若后端没卡但仍偶发：确认是否被防火墙/杀软拦截回环连接，或本机资源耗尽导致 accept 变慢。
- 配置层面兜底：确保上传相关 location 设置了 `proxy_connect_timeout`，并建议对上传接口关闭 `proxy_request_buffering`，减少代理层落盘与阻塞。
