
**严格按照以下步骤顺序执行**。每一行代码块中的命令都可以直接复制并在服务器终端中运行。
---

## 第一步：更新系统并安装基础工具

登录到您的 Ubuntu 服务器后，首先更新软件源并安装必要的工具：
```bash
# 更新系统软件包列表：
sudo apt update && sudo apt upgrade -y

# 安装基础工具（git用于下载代码，unzip用于解压，nano用于编辑文件）：
sudo apt install git curl wget unzip nano -y
```

---

## 第三步：下载项目代码

```bash
# 回到用户主目录
cd ~

# 克隆代码
git clone git@github.com:YourUsername/EnterpriseRagCommunity.git

# 进入项目目录
cd EnterpriseRagCommunity
```

---

## 第四步：安装与配置 Java 环境 (后端)

项目配置使用了较新的 Java 版本，我们安装目前最稳定的 JDK 25。
1. **安装 JDK 25**：   
```bash
   sudo apt install openjdk-25-jdk -y

   # 验证安装
   java -version
   ```

2. **赋予构建脚本执行权限**：   
```bash
   chmod +x gradlew
   ```

---

## 第五步：安装与配置 MySQL 数据库
1. **安装 MySQL**：   
```bash
   sudo apt install mysql-server -y
   ```

2. **配置数据库用户和密码**：   项目默认配置使用 `root` 用户，密码为 `password`。我们需要将数据库密码修改为一致。
   请依次执行以下命令（每一行单独执行）：   
   ```bash
   sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'password';"
   sudo mysql -u root -ppassword -e "CREATE DATABASE IF NOT EXISTS EnterpriseRagCommunity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   ```

---

## 第六步：配置后端环境变量

在进行后续步骤之前，我们先配置后端服务所需的关键环境变量。
1. **生成 APP_MASTER_KEY**：   这是用于加密数据库敏感配置的主密钥。请运行以下命令生成一个随机密钥：
   ```bash
   openssl rand -base64 32
   ```
   > 请复制输出的字符串，稍后会用到。
2. **创建 Systemd 环境文件**：   我们将把环境变量写入 `/etc/default/enterprise-rag`，由 Systemd 在启动时注入进程环境。不要把 `APP_MASTER_KEY`、`DB_USERNAME`、`DB_PASSWORD` 写入项目根目录 `.env`。   
```bash
   sudo nano /etc/default/enterprise-rag
   ```

3. **粘贴以下内容**：
   > 请将 `<您的APP_MASTER_KEY>` 替换为第1步生成的字符串。 
   > 请确保 `DB_PASSWORD` 与第五步中设置的 MySQL 密码一致。
   ```ini
   # === 安全配置 ===
   APP_MASTER_KEY=<您的APP_MASTER_KEY>

   # === 数据库配置 ===
   DB_USERNAME=root
   DB_PASSWORD=password

   # === 反向代理 / CORS（重要：使用 Nginx 域名访问时需要）===
   # 说明：
   # - 初始化向导会调用 /api/setup/encrypt 和 api/setup/test-es 的 POST 接口
   # - 浏览器对 POST 通常会携带 Origin 头，后端如果未允许该 Origin 会返回 403（Invalid CORS request）。
   # - 这里填写你对外访问的站点 Origin（协议/域名/端口），多个用逗号分隔
   # 示例：APP_CORS_ALLOWED_ORIGINS=https://example.com,http://example.com
   APP_CORS_ALLOWED_ORIGINS=https://example.com,http://example.com

   # === 反向代理头处理（推荐）===
   # 让 Spring 正确识别 Nginx 转发后的 https/http、host、port
   SERVER_FORWARD_HEADERS_STRATEGY=framework
   ```

   若你不是通过 Systemd 启动，而是临时手工启动，也应先导出环境变量，再执行 `java -jar`，而不是写入项目 `.env`：
   ```bash
   export APP_MASTER_KEY='<您的APP_MASTER_KEY>'
   export DB_USERNAME='root'
   export DB_PASSWORD='password'
   java -jar build/libs/EnterpriseRagCommunity.war --spring.profiles.active=perf
   ```

   **保存退出**：`Ctrl + O`, `Enter`, `Ctrl + X`。
---

## 第七步：安装 Docker 和 Elasticsearch (搜索引擎)

项目依赖 Elasticsearch。我们将使用 Docker 安装，并配置 3GB 内存和 API Key 认证。
1. **安装 Docker**
   ```bash
   # 安装 Docker
   sudo apt install docker.io -y

   # 将当前用户添加到 docker 用户组（无需 sudo 即可运行 docker)
   sudo usermod -aG docker $USER
   newgrp docker
   ```

2. **创建网络并启动Elasticsearch**
   ```bash
   # 创建 docker 网络
   docker network create elastic

   # 启动 Elasticsearch 容器
   docker run -d --name elasticsearch \
     --restart unless-stopped \
     --net elastic \
     -p 9200:9200 \
     -e "discovery.type=single-node" \
     -e "xpack.security.enabled=true" \
     -e "xpack.security.http.ssl.enabled=false" \
     -e "ES_JAVA_OPTS=-Xms2560m -Xmx2560m" \
     -m 3GB \
     docker.elastic.co/elasticsearch/elasticsearch:9.2.4
   ```

3. **确认 Elasticsearch 是否真正就绪**
   ```bash
   # 查看容器日志，观察是否启动成功）：
   docker logs -f elasticsearch
   ```

   等待看到类似以下日志（表示已 ready）：

   ```
   [INFO ][o.e.c.c.Coordinator] [node-name] cluster UUID: xxxxx
   [INFO ][o.e.h.AbstractHttpServerTransport] [node-name] publish_address {172.x.x.x:9200}, bound_addresses {[::]:9200}
   [INFO ][o.e.n.Node] [node-name] started
   ```

   > ⚠️ 如果日志中有 `OutOfMemoryError` 或 `bootstrap checks failed`，说明配置有问题。
4. **生成 API Key**
   容器启动需要约 30 秒。等待片刻后，执行以下命令生成连接所需的 API Key。
   ```bash
   # 1. 重置 elastic 超级用户密码 (请保存输出的密码)
   docker exec -it elasticsearch /usr/share/elasticsearch/bin/elasticsearch-reset-password -u elastic -b

   # 2. 生成 API Key (需要使用上一步得到的密码)
   # 将下面的 <PASSWORD> 替换为刚才得到的密码
   # 下面的命令会返回一个 "encoded" 字段，这就是我们需要填入项目的 API Key
   curl -X POST "http://localhost:9200/_security/api_key?pretty" \
        -u elastic:<PASSWORD> \
        -H 'Content-Type: application/json' \
        -d'{"name": "rag_app_key"}'
   ```

   **注意**：请复制返回结果中 `encoded` 字段的值（一串长字符），这将在 **第十一步：系统初始化向导** 中填入。
5. **验证连接**
   ```bash
   # 替换 <YOUR_API_KEY> 进行测试
   curl -H "Authorization: ApiKey <YOUR_API_KEY>" http://localhost:9200
   ```

---

## 第八步：安装 Node.js 并构建前端
1. **安装 Node.js 20 (LTS)**：
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
   sudo apt install -y nodejs
   ```

2. **构建前端代码**：
```bash
   # 进入前端目录
   cd my-vite-app

   # 安装依赖
   npm install

   # 开始构建（这一步可能需要几分钟）：
   npm run build

   # 构建完成后，回到项目根目录：
   cd ..
   ```

---

## 第九步：构建后端代码

现在我们编译 Java 后端。
```bash
# 开始构建（第一次运行会下载 Gradle 和依赖，比较慢，请耐心等待）
./gradlew bootWar
```

> 如果显示 `BUILD SUCCESSFUL`，说明构建成功。生成的文件在 `build/libs/` 目录下。
---

## 第十步：配置 Nginx (Web 服务器)

我们需要 Nginx 来对外提供服务：它负责把用户的请求分发给前端页面或后端 API。
1. **安装 Nginx**：
```bash
   sudo apt install nginx -y
   ```

2. **创建配置文件**：我们将使用 `nano` 编辑器创建一个新的配置文件：
```bash
   sudo nano /etc/nginx/sites-available/enterprise-rag
   ```

3. **粘贴以下内容**：
   > 请将下面的 `/home/ubuntu/EnterpriseRagCommunity` 替换为您实际的项目路径。如果您使用的是 ubuntu
   默认用户，通常路径就是这个。如果不确定，可以用 `pwd` 命令查看当前路径。
   > 该配置已对大文件上传与长连接反向代理做增强：`upstream + keepalive + HTTP/1.1 + 关闭请求缓冲`。
   ```nginx
   upstream erc_backend {
       server 127.0.0.1:8099;
       keepalive 64;
   }

   server {
       listen 80 default_server;
       server_name _;

       client_max_body_size 2048g;

         location ~* (\.env|\.git|\.bak|\.sql|\.zip|\.tar\.gz|\.php|\.DS_Store|/wp-admin|/wp-login|/vendor|/actuator|/swagger-ui) {
             return 444;
         }

         location / {
           root /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist;
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
   ```

4. **启用站点并重启 Nginx**
   ```bash
   sudo ln -s /etc/nginx/sites-available/enterprise-rag /etc/nginx/sites-enabled/enterprise-rag

   sudo rm -f /etc/nginx/sites-enabled/default

   sudo nginx -t
   sudo systemctl restart nginx

   curl -I http://localhost
   ```
   > 如果 `ln -s` 提示 `File exists`，说明软链接已存在：先执行 `sudo rm -f /etc/nginx/sites-enabled/enterprise-rag` 再重新执行`ln -s`。
5. **配置 SSL 证书（HTTPS，Nginx）**

   > 前提：您已经在腾讯云/CA 平台下载了 Nginx 证书（通常包含 `*.crt` / `*.pem` / `*.key`）。
   > 注意：公有 CA 证书一般要求域名；如果您只有公网 IP，没有域名，浏览器访问会出现证书不受信任，属于正常现象。
   **5.1 上传并放置证书文件**

   建议将证书放到系统目录，权限更安全（示例：`example.com` 为目录名）：
   ```bash
   sudo mkdir -p /etc/nginx/ssl/example.com

   sudo cp /path/to/your_cert.crt /etc/nginx/ssl/example.com/server.crt
   sudo cp /path/to/your_private.key /etc/nginx/ssl/example.com/server.key

   sudo chown -R root:root /etc/nginx/ssl/example.com
   sudo chmod 700 /etc/nginx/ssl/example.com
   sudo chmod 600 /etc/nginx/ssl/example.com/server.key
   sudo chmod 644 /etc/nginx/ssl/example.com/server.crt
   ```

   > 如果您下载的是压缩包（例如 `cert.zip`），可以先上传到服务器后解压，再按上面方法 `cp` 到 `/etc/nginx/ssl/...`。
   ```bash
   unzip cert.zip -d cert_unzip
   ls -al cert_unzip
   ```

   **5.2 修改 Nginx 配置文件：HTTPS**

   打开配置文件：   
   ```bash
   sudo nano /etc/nginx/sites-available/enterprise-rag
   ```

   将内容替换为以下示例（把 `server_name` 与证书路径改成您的实际值）：   
   ```nginx
   upstream erc_backend {
       server 127.0.0.1:8099;
       keepalive 64;
   }

   server {
       listen 80 default_server;
       server_name example.com;
       return 301 https://$host$request_uri;
   }

   server {
       listen 443 ssl http2;
       server_name example.com;

       ssl_certificate /etc/nginx/ssl/example.com/server.crt;
       ssl_certificate_key /etc/nginx/ssl/example.com/server.key;

       ssl_protocols TLSv1.2 TLSv1.3;
       ssl_ciphers HIGH:!aNULL:!MD5;
       ssl_prefer_server_ciphers off;
       client_max_body_size 2048g;

         location ~* (\.env|\.git|\.bak|\.sql|\.zip|\.tar\.gz|\.php|\.DS_Store|/wp-admin|/wp-login|/vendor|/actuator|/swagger-ui) {
             return 444;
         }

         location / {
           root /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist;
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
   ```

   **5.3 验证 HTTPS 与重启**
   ```bash
   sudo nginx -t
   sudo systemctl restart nginx

   curl -Ik https://localhost

   curl -Ik https://example.com
   ```

   ### 常见错误：如果 80 端口出现 500，但 8099 正常
      500 的根因一定会写进错误日志（例如重定向循环、open() 失败、配置命中异常等）。可以运行命令行查看日志：
      ```bash
      sudo tail -n 200 /var/log/nginx/error.log
      ```

      如果 Nginx 错误日志显示权限被拒绝（Permission denied）的问题，导致网站无法正常访问，以下是详细分析和解决方案：
      方法一：修改目录权限（推荐）：
      给 Nginx 用户（通常是 www-data）赋予读取和执行权限：
      ```bash
      # 1. 确保父目录有执行权限（必须！）：
      sudo chmod +x /home/ubuntu
      sudo chmod +x /home/ubuntu/EnterpriseRagCommunity
      sudo chmod +x /home/ubuntu/EnterpriseRagCommunity/my-vite-app
      sudo chmod +x /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist

      # 2. 给 dist 目录及内容添加读权限：
      sudo chmod -R 755 /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist

      # 或者更安全的方式：只给需要的用户组授权：
      sudo chgrp -R www-data /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist
      sudo chmod -R 750 /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist


      # 3. 重启 nginx
      sudo systemctl restart nginx

      # 4. 测试访问
      curl -I http://localhost
      ```
---

## 第十一步：启动后端服务 (设置开机自启)

为了让后端程序在后台运行且开机自动启动，我们创建一个 systemd 服务。

1. **创建服务文件**：
```bash
   sudo nano /etc/systemd/system/enterprise-rag.service
   ```

2. **粘贴以下内容**：
   > 同样注意修改 `/home/ubuntu/...` 路径。 
   > 我们通过 `EnvironmentFile` 指令引入了第六步创建的配置文件：
   ```ini
   [Unit]
   Description=Enterprise Rag Community Backend
   After=syslog.target network.target mysql.service

   [Service]
   User=root

   # === 引入环境变量 ===
   # 从第六步创建的文件中读取 APP_MASTER_KEY、数据库配置
   EnvironmentFile=/etc/default/enterprise-rag

   # === 启动命令 ===
   # 这里指向刚才构建生成的 war 包
   ExecStart=/usr/bin/java -jar /home/ubuntu/EnterpriseRagCommunity/build/libs/EnterpriseRagCommunity.war
   SuccessExitStatus=143
   Restart=always

   [Install]
   WantedBy=multi-user.target
   ```

   **保存退出**：`Ctrl + O`, `Enter`, `Ctrl + X`。
3. **启动服务**：  
```bash
   # 重新加载服务列表
   sudo systemctl daemon-reload

   # 启动服务
   sudo systemctl start enterprise-rag

   # 设置开机自启：
   sudo systemctl enable enterprise-rag

   # 查看服务状态（按 q 退出）
   sudo systemctl status enterprise-rag
   ```

---

## 验证与系统初始化

现在，您应该可以通过浏览器访问服务器的公网 IP 地址来看到您的应用了。 
如果无法访问，请检查腾讯云控制台的**安全组**设置：

- 未配置 SSL：确保 **TCP 80 端口** 开放。
- 已配置 SSL：确保 **TCP 443 端口** 开放（若启用了 80 转 443 跳转，也需开放 80）。
---

## 第十二步：系统初始化向导 (System Initialization)

首次访问系统时，应用会自动检测初始化状态，并跳转至 **系统初始化向导** (`/admin-setup`)。 
请在向导中完成以下配置：

1. **管理员账号创建**：设置系统的初始超级管理员。
2. **Elasticsearch 配置**：
    - **主机**: `localhost`
   - **端口**: `9200`
    - **协议**: `http`（因为我们在 Step 7 中关闭了 SSL）
    - **API Key**: 填入 **第七步** 中生成的 `encoded` 字符串。
3. **其他服务**：
   - 根据向导提示配置邮件服务 (SMTP)、AI 模型密钥，以及 Kafka 日志链路配置（Broker、Topic、sink-mode、鉴权开关、AccessKey/Secret 等）。
配置完成后，点击“完成设置”，系统将自动加载配置并进入登录页面。
---

## 第十三步：日常维护与更新指南

部署完成后，您可能需要经常查看服务状态或更新代码。以下是常用操作命令：
### 1. 查看后端服务状态与日志

- **查看运行状态**（查看是否正在运行、启动时间等）：
  ```bash
  sudo systemctl status enterprise-rag
  ```
  > 按 `q` 退出查看。
- **查看实时应用日志**（非常重要，用于排查报错）：
  ```bash
   # -f 表示 follow（实时刷新），-u 指定服务名
   sudo journalctl -u enterprise-rag -f
  ```
  > 按 `Ctrl + C` 停止查看

### 2. 管理后端服务

- **停止服务**：  
```bash
  sudo systemctl stop enterprise-rag
  ```

- **启动服务**：  
```bash
  sudo systemctl start enterprise-rag
  ```

- **重启服务**（修改配置后常用）：
  ```bash
  sudo systemctl restart enterprise-rag
  ```

### 3. 管理 Docker 容器 (Elasticsearch)

如果您需要手动控制 Elasticsearch 容器：
- **停止容器**：  
```bash
  docker stop elasticsearch
  ```

- **启动容器**：  
```bash
  docker start elasticsearch
  ```

- **查看容器日志**：  
```bash
  docker logs -f elasticsearch
  ```

### 4. 拉取最新代码并更新部署

当您在本地开发并 Push 了新代码到 GitHub 后，服务器端需要执行以下步骤来应用更新：
```bash
# 1. 进入项目目录
cd ~/EnterpriseRagCommunity

# 2. 拉取最新代码：
git pull

# 3. 重新构建后端 (如果修改了 Java 代码）
./gradlew bootWar

# 重启服务以应用更新：
sudo systemctl restart enterprise-rag

# 4. 重新构建前端 (如果修改了 React 代码）
cd my-vite-app
npm install  # 以防有新的依赖
npm run build
cd ..        # 回到根目录
```

前端构建完成后立即生效（无需重启 Nginx，除非您修改了 Nginx 配置文件）。

### 5. 重置数据库 (测试环境专用)

如果您在测试过程中需要清空数据库重新开始，可以使用以下命令删除并重新创建数据库：
```bash
# 1. 删除旧数据库
sudo mysql -u root -p -e "DROP DATABASE IF EXISTS EnterpriseRagCommunity;"

# 2. 重启后端服务（确保连接池重置）：
sudo systemctl restart enterprise-rag
```

> ⚠️ 警告：此操作会永久删除所有业务数据，请谨慎操作！

---

# 修改端口

由于 80 端口在国内服务器上必须完成 ICP 备案才能对外开放，未备案时会被云厂商自动拦截。
您可以通过将 Nginx 的监听端口修改为其他非标准端口（如**8080**）来绕过此限制，以便于进行测试。以下是具体操作步骤，请直接在您的腾讯云 Ubuntu 服务器终端中执行：
## 第一步：修改 Nginx 监听端口

我们可以使用 `sed` 命令直接将配置文件中的 `80` 端口替换为 `8080`。请复制并执行以下命令：

```bash
sudo cp /etc/nginx/sites-available/enterprise-rag /etc/nginx/sites-available/enterprise-rag.bak

sudo sed -i \
  -e 's/listen 80 default_server;/listen 8080 default_server;/g' \
  -e 's/listen 80;/listen 8080;/g' \
  /etc/nginx/sites-available/enterprise-rag
```

> 如果您已经按上面的步骤启用了 HTTPS：通常还需要把 `listen 443 ssl http2;` 改成 `listen 8443 ssl http2;`（示例端口），并在安全组放行 8443。
```bash
sudo sed -i \
  -e 's/listen 443 ssl http2;/listen 8443 ssl http2;/g' \
  -e 's/listen 443 ssl;/listen 8443 ssl;/g' \
  /etc/nginx/sites-available/enterprise-rag
```

## 第二步：检查并重启 Nginx

修改完成后，需要验证配置文件的语法是否正确，并重启 Nginx 使更改生效：
```bash
sudo nginx -t

sudo systemctl restart nginx
```

## 第三步：在腾讯云控制台开放 8080 端口

仅仅在服务器上修改是不够的，您还需要在腾讯云的防火墙（安全组）中放行 8080 端口。如果您同时把 HTTPS 端口改成了 8443，也需要放行
`TCP:8443`。
1. 登录 [腾讯云控制台](https://console.cloud.tencent.com/cvm/index)。
2. 找到您的实例，点击“安全组”选项卡。
3. 点击**“编辑规则”** -> **“入站规则”**。
4. 点击**“添加规则”**：
   - **类型**：自定义
   - **来源**：`0.0.0.0/0`（允许所有 IP 访问）
   - **协议端口**：`TCP:8080`
   - **策略**：允许
5. 保存规则。

## 第四步：验证访问

现在，您应该可以通过 IP 加端口的方式访问您的网站了：

**访问地址（HTTP）**：`http://<您的服务器公网IP>:8080`

如果您同时把 HTTPS 端口改成了 8443：
**访问地址（HTTPS）**：`https://<您的域名或公网IP>:8443`

## 补充说明

- **关于备案**：如果您后续购买了域名并完成了备案，可以随时按照上述步骤将端口改为 80（只需将 `sed` 命令中的 `8080` 与 `80`
  对调即可）。
- **常用端口**：如果 8080 也被占用，您还可以尝试 8081、8088 等端口。
---

# Docker 容器维护

Elasticsearch 的内存配置（特别是 JVM 堆内存`ES_JAVA_OPTS`）是启动时参数，**无法在容器运行时直接动态修改**。
您必须**删除旧容器并使用新参数重新启动**。
由于之前的启动命令中**没有挂载数据卷**（`-v`），直接删除容器会导致**索引数据丢失**。
请根据您的需求选择以下两种方案之一：

## 方案一：数据不重要（直接重建）

如果您还在测试阶段，不在乎数据丢失，可以直接删除重建。
1. **停止并删除容器**
   ```bash
   docker stop elasticsearch
   docker rm elasticsearch
   ```

2. **使用新内存配置启动**（例如改为 2GB 堆内存，容器限额 2.5GB）：
   ```bash
   docker run -d --name elasticsearch \
     --restart unless-stopped \
     --net elastic \
     -p 9200:9200 \
     -e "discovery.type=single-node" \
     -e "xpack.security.enabled=true" \
     -e "xpack.security.http.ssl.enabled=false" \
     -e "ES_JAVA_OPTS=-Xms2g -Xmx2g" \
     -m 2.5GB \
     docker.elastic.co/elasticsearch/elasticsearch:9.2.4
   ```
   > 注：本文档面向 Ubuntu Bash，换行符使用 `\`。

---

## 方案二：保留数据修改（推荐）

如果您已经有了重要数据，请先将数据从容器中拷贝出来，再挂载回去。
1. **创建本地数据目录**
   ```bash
   # 在当前目录下创建 es_data 目录
   mkdir -p es_data
   ```

2. **将容器内的数据复制出来**
   ```bash
   docker cp elasticsearch:/usr/share/elasticsearch/data ./es_data
   ```

3. **停止并删除旧容器**
   ```bash
   docker stop elasticsearch
   docker rm elasticsearch
   ```

4. **启动新容器（挂载刚才复制的数据卷）**
   注意新增了 `-v` 参数挂载本地目录：
   ```bash
   docker run -d --name elasticsearch \
     --restart unless-stopped \
     --net elastic \
     -p 9200:9200 \
     -v "${PWD}/es_data/data:/usr/share/elasticsearch/data" \
     -e "discovery.type=single-node" \
     -e "xpack.security.enabled=true" \
     -e "xpack.security.http.ssl.enabled=false" \
     -e "ES_JAVA_OPTS=-Xms2g -Xmx3g" \
     -m 3GB \
     docker.elastic.co/elasticsearch/elasticsearch:9.2.4
   ```

## 验证修改

启动后，您可以进入容器检查 JVM 参数是否生效：

```bash
docker exec elasticsearch env | grep "ES_JAVA_OPTS"
```
或者查看容器内存限制：

```bash
docker stats elasticsearch --no-stream
```

---

# Kafka（Access 日志链路）安装与配置（Linux）

当你要启用 Access 日志 KAFKA 或 DUAL 模式时，按下面步骤执行。

## 1. 安装 JDK 与基础目录

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk wget tar

sudo mkdir -p /opt/kafka
sudo chown -R $USER:$USER /opt/kafka
```

## 2. 下载并解压 Kafka 4.2.0

```bash
cd /opt/kafka
wget https://downloads.apache.org/kafka/4.2.0/kafka_2.13-4.2.0.tgz
tar -xzf kafka_2.13-4.2.0.tgz
cd kafka_2.13-4.2.0
```

## 3. 配置 KRaft 单机模式

编辑 `config/server.properties`，至少确认以下参数：

```properties
process.roles=broker,controller
node.id=1
controller.quorum.bootstrap.servers=127.0.0.1:9093
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://127.0.0.1:9092,CONTROLLER://127.0.0.1:9093
controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
log.dirs=/opt/kafka/kafka_2.13-4.2.0/data/kraft-logs
```

## 4. 初始化存储并启动 Kafka

```bash
cd /opt/kafka/kafka_2.13-4.2.0

mkdir -p data/kraft-logs
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
bin/kafka-storage.sh format -t "$KAFKA_CLUSTER_ID" -c config/server.properties

nohup bin/kafka-server-start.sh config/server.properties > kafka.log 2>&1 &
```

检查进程与监听：

```bash
ps -ef | grep kafka
ss -lntp | grep 9092
tail -n 100 kafka.log
```

## 5. 创建 Access 日志 Topic

```bash
cd /opt/kafka/kafka_2.13-4.2.0

bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 \
   --create --if-not-exists \
   --topic access-logs-v1 \
   --partitions 6 \
   --replication-factor 1

bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --describe --topic access-logs-v1
```

## 6. 在系统初始化向导中配置 Kafka

Kafka 相关配置统一在系统初始化向导页面配置，不再手工维护大量 Kafka 环境变量。

向导入口：`/admin-setup` -> `ES 初始化` -> `Kafka 配置`


重启服务：

```bash
sudo systemctl daemon-reload
sudo systemctl restart enterprise-rag
sudo systemctl status enterprise-rag
```

## 7. 验证链路

1. 触发应用请求后，检查 Kafka 是否有消息：

```bash
cd /opt/kafka/kafka_2.13-4.2.0
bin/kafka-console-consumer.sh --bootstrap-server 127.0.0.1:9092 --topic access-logs-v1 --from-beginning --max-messages 5
```

2. 检查服务日志关键字：

```bash
sudo journalctl -u enterprise-rag -f | grep -E "access_log_kafka|access_log_es_sink|access_log_dual_verify"
```

3. 检查 ES 索引模板与索引：

```bash
curl -H "Authorization: ApiKey <YOUR_API_KEY>" "http://127.0.0.1:9200/_index_template/access-logs-template-v1?pretty"
curl -H "Authorization: ApiKey <YOUR_API_KEY>" "http://127.0.0.1:9200/access-logs-v1/_search?size=1&pretty"
```

## 8. Kafka 常用运维命令

下面这些命令适合在 Linux 上日常排查 `access-logs-v1` Topic、Broker 和消费堆积。

### 8.1 预设变量

```bash
cd /opt/kafka/kafka_2.13-4.2.0
export BOOTSTRAP=127.0.0.1:9092
export TOPIC=access-logs-v1
export GROUP=access-log-es-sink-v1
```

### 8.2 启动、停止、查看状态

```bash
# 前台启动（推荐排障时使用）
bin/kafka-server-start.sh config/server.properties

# 后台启动
nohup bin/kafka-server-start.sh config/server.properties > kafka.log 2>&1 &

# 查看 Kafka 进程
ps -ef | grep kafka | grep -v grep

# 查看监听端口
ss -lntp | grep -E '9092|9093'

# 查看最近日志
tail -n 100 kafka.log

# 停止 Kafka
pkill -f kafka.Kafka || true
```

### 8.3 Topic 管理

```bash
# 查看所有 Topic
bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP --list

# 查看 access-logs-v1 详情
bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP --describe --topic $TOPIC

# 增加分区数（只能增不能减）
bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP --alter --topic $TOPIC --partitions 6

# 删除 Topic（确认不再需要数据时再执行）
bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP --delete --topic $TOPIC
```

### 8.4 消费与堆积排查

```bash
# 从头读取 5 条消息
bin/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP --topic $TOPIC --from-beginning --max-messages 5

# 仅读取最新到来的消息
bin/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP --topic $TOPIC --max-messages 5 --timeout-ms 10000

# 查看所有 Consumer Group
bin/kafka-consumer-groups.sh --bootstrap-server $BOOTSTRAP --list

# 查看 access-log-es-sink-v1 消费组堆积
bin/kafka-consumer-groups.sh --bootstrap-server $BOOTSTRAP --describe --group $GROUP
```

### 8.5 发送测试消息

```bash
echo "ops-check-$(date +%Y%m%d_%H%M%S)" | bin/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP --topic $TOPIC
```

如果这条消息能被消费者读到，说明 Kafka Broker、Topic 和基础读写链路正常。

## 9. KRaft 避坑指南（重点注意）

下面这几条是单机 KRaft 最容易踩坑的位置，建议严格按顺序执行。

1. `kafka-storage format` 报错要求 `--standalone` / `--initial-controllers`

- 现象：`Because controller.quorum.voters is not set ...`
- 原因：配置只写了 `controller.quorum.bootstrap.servers`，但未写 `controller.quorum.voters`
- 推荐修复（二选一）：
   - 在 `config/server.properties` 增加：`controller.quorum.voters=1@127.0.0.1:9093`
   - 或保持现配置不变，执行 format 时追加：`--standalone`

示例（推荐兼容写法）：

```bash
cd /opt/kafka/kafka_2.13-4.2.0
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid 2>/dev/null)"
bin/kafka-storage.sh format --standalone -t "$KAFKA_CLUSTER_ID" -c config/server.properties
```

2. `Invalid cluster.id ... Expected xxx, but read yyy`

- 现象：重复生成新 `cluster.id` 并重新 format，导致 `meta.properties` 中旧 ID 与新 ID 冲突
- 处理原则：`log.dirs` 已有元数据时，不要直接换新的 `cluster.id` 再 format

安全处理步骤：

```bash
# 先停 Kafka（如果在跑）
pkill -f kafka.Kafka || true

cd /opt/kafka/kafka_2.13-4.2.0
rm -rf data/kraft-logs/*

KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid 2>/dev/null)"
bin/kafka-storage.sh format --standalone -t "$KAFKA_CLUSTER_ID" -c config/server.properties
```

3. 9092 端口在监听但不确定是否当前实例

- 现象：端口已监听，但可能是旧进程占用
- 建议：先查 PID 和命令行，再决定是否重启，避免重复启动

```bash
ss -lntp | grep 9092
ps -fp <PID>
```

4. `kafka-storage random-uuid` 出现 log4j ERROR

- 现象：`main ERROR Reconfiguration failed: No configuration found for 'xxxx'`
- 说明：通常是日志配置初始化噪声，不一定是致命错误
- 建议：重点检查命令是否正常返回 UUID，并继续执行 format 与启动验证

5. JDK 版本建议

- 优先使用 JDK 21 LTS
- 若使用更高版本（如 25）出现兼容性问题，优先回退到 JDK 21 再排查

