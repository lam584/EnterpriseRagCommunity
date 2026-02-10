请**严格按照以下步骤顺序执行**。每一行代码块中的命令都可以直接复制并在服务器终端中运行。

### 第一步：更新系统并安装基础工具

登录到您的 Ubuntu 服务器后，首先更新软件源并安装必要的工具。

```bash
# 更新系统软件包列表
sudo apt update && sudo apt upgrade -y

# 安装基础工具（git用于下载代码，unzip用于解压，nano用于编辑文件）
sudo apt install git curl wget unzip nano -y
```

### 第二步：配置 GitHub 访问权限 (SSH Key)

因为您的仓库是私有的，服务器需要权限才能下载代码。

1.  **生成 SSH 密钥**（一路回车即可，不要设置密码）：
    ```bash
    ssh-keygen -t ed25519 -C "your_email@example.com"
    ```
    *(注：邮箱可以随便填，不影响功能)*

2.  **查看并复制公钥**：
    ```bash
    cat ~/.ssh/id_ed25519.pub
    ```
    **操作**：复制输出的以 `ssh-ed25519` 开头的一长串字符。

3.  **添加到 GitHub**：
    *   在浏览器打开您的 GitHub 仓库页面。
    *   点击右上角头像 -> **Settings** -> **SSH and GPG keys** -> **New SSH key**。
    *   Title 随便填（例如 "Tencent Cloud"），Key 粘贴刚才复制的内容，点击 **Add SSH key**。

4.  **测试连接**：
    ```bash
    ssh -T git@github.com
    ```
    *(如果提示 "Are you sure you want to continue connecting?"，输入 `yes` 并回车。看到 "Hi username! You've successfully authenticated" 即成功)*

### 第三步：下载项目代码

```bash
# 回到用户主目录
cd ~

# 克隆代码（请将下面的 URL 替换为您实际的仓库 SSH 地址）
# 格式通常是：git@github.com:您的用户名/仓库名.git
git clone git@github.com:YourUsername/EnterpriseRagCommunity.git

# 进入项目目录（如果您的仓库名不是 EnterpriseRagCommunity，请修改目录名）
cd EnterpriseRagCommunity
```

### 第四步：安装与配置 Java 环境 (后端)

项目配置使用了较新的 Java 版本，我们安装目前最稳定的 JDK 25。

1.  **安装 JDK 25**：
    ```bash
    sudo apt install openjdk-25-jdk -y
    
    # 验证安装
    java -version
    ```


3.  **赋予构建脚本执行权限**：
    ```bash
    chmod +x gradlew
    ```

### 第五步：安装与配置 MySQL 数据库

1.  **安装 MySQL**：
    ```bash
    sudo apt install mysql-server -y
    ```

2.  **配置数据库用户和密码**：
    项目默认配置使用 `root` 用户，密码为 `password`。我们需要将数据库密码修改为一致。
    
    请依次执行以下命令（每一行单独执行）：
    ```bash
    sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'password';"
    sudo mysql -u root -ppassword -e "CREATE DATABASE IF NOT EXISTS EnterpriseRagCommunity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    ```

### 第六步：配置后端环境变量

在进行后续步骤之前，我们先配置后端服务所需的关键环境变量。

1.  **生成 APP_MASTER_KEY**：
    这是用于加密数据库敏感配置的主密钥。请运行以下命令生成一个随机密钥：
    ```bash
    openssl rand -base64 32
    ```
    *请复制输出的字符串，稍后会用到。*

2.  **创建配置文件**：
    我们将把环境变量写入 `/etc/default/enterprise-rag` 文件中，这样 Systemd 可以直接读取。

    ```bash
    sudo nano /etc/default/enterprise-rag
    ```

3.  **粘贴以下内容**：
    *请将 `<您的APP_MASTER_KEY>` 替换为第1步生成的字符串。*
    *请确保 `DB_PASSWORD` 与第五步中设置的 MySQL 密码一致。*

    ```ini
    # === 安全配置 ===
    APP_MASTER_KEY=<您的APP_MASTER_KEY>

    # === 数据库配置 ===
    DB_USERNAME=root
    DB_PASSWORD=password
    ```
    **保存退出**：`Ctrl + O`, `Enter`, `Ctrl + X`。

### 第七步：安装 Docker 与 Elasticsearch (搜索引擎)

项目依赖 Elasticsearch。我们将使用 Docker 安装，并配置 4.5GB 内存与 API Key 认证。

1.  **安装 Docker**
    ```bash
    # 安装 Docker
    sudo apt install docker.io -y

    # 将当前用户添加到 docker 用户组 (无需 sudo 即可运行 docker)
    sudo usermod -aG docker $USER
    newgrp docker
    ```

2.  **创建网络并启动 Elasticsearch**
    ```bash
    # 创建 docker 网络
    docker network create elastic

    # 启动 Elasticsearch 容器
    # 注意：我们开启了安全认证(xpack.security.enabled=true)，但为了简化 Java 连接，关闭了 HTTP SSL
    docker run -d --name elasticsearch \
      --net elastic \
      -p 9200:9200 \
      -e "discovery.type=single-node" \
      -e "xpack.security.enabled=true" \
      -e "xpack.security.http.ssl.enabled=false" \
      -e "ES_JAVA_OPTS=-Xms4g -Xmx4g" \
      -m 4.5GB \
      docker.elastic.co/elasticsearch/elasticsearch:9.2.4
    ```
    ### 确认 Elasticsearch 是否真正就绪

    ```bash
    # 查看容器日志，观察是否启动成功
    docker logs -f elasticsearch
    ```

    等待看到类似以下日志（表示已 ready）：

    ```
    [INFO ][o.e.c.c.Coordinator] [node-name] cluster UUID: xxxxx
    [INFO ][o.e.h.AbstractHttpServerTransport] [node-name] publish_address {172.x.x.x:9200}, bound_addresses {[::]:9200}
    [INFO ][o.e.n.Node] [node-name] started
    ```

    > ⚠️ 如果日志中有 `OutOfMemoryError` 或 `bootstrap checks failed`，说明配置有问题。

3.  **生成 API Key**
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

4.  **验证连接**
    ```bash
    # 替换 <YOUR_API_KEY> 进行测试
    curl -H "Authorization: ApiKey <YOUR_API_KEY>" http://localhost:9200
    ```


### 第八步：安装 Node.js 并构建前端

1.  **安装 Node.js 20 (LTS)**：
    ```bash
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt install -y nodejs
    ```

2.  **构建前端代码**：
    ```bash
    # 进入前端目录
    cd my-vite-app

    # 安装依赖
    npm install

    # 开始构建（这一步可能需要几分钟）
    npm run build

    # 构建完成后，回到项目根目录
    cd ..
    ```

### 第九步：构建后端代码

现在我们编译 Java 后端。

```bash
# 开始构建（第一次运行会下载 Gradle 和依赖，比较慢，请耐心等待）
./gradlew bootWar
```
*如果显示 `BUILD SUCCESSFUL`，说明构建成功。生成的文件在 `build/libs/` 目录下。*

### 第十步：配置 Nginx (Web 服务器)

我们需要 Nginx 来对外提供服务：它负责把用户的请求分发给前端页面或后端 API。

1.  **安装 Nginx**：
    ```bash
    sudo apt install nginx -y
    ```

2.  **创建配置文件**：
    我们将使用 `nano` 编辑器创建一个新的配置。
    ```bash
    sudo nano /etc/nginx/sites-available/enterprise-rag
    ```

3.  **粘贴以下内容**：
    *(请将下面的 `/home/ubuntu/EnterpriseRagCommunity` 替换为您实际的项目路径。如果您使用的是 ubuntu 默认用户，通常路径就是这个。如果不确定，可以用 `pwd` 命令查看当前路径)*

    ```nginx
    server {
        listen 80;
        server_name _;  # 这里可以填域名，没有域名就填 _

        # 前端静态文件
        location / {
            # 注意：确保这个路径指向您项目下的 my-vite-app/dist 目录
            root /home/ubuntu/EnterpriseRagCommunity/my-vite-app/dist;
            index index.html;
            try_files $uri $uri/ /index.html;
        }

        # 后端 API 代理
        location /api {
            proxy_pass http://localhost:8099;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }

        # 上传文件路径代理
        location /uploads {
            proxy_pass http://localhost:8099;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
    ```
    **保存退出方法**：按 `Ctrl + O`，然后按 `Enter` 确认保存，最后按 `Ctrl + X` 退出。

4.  **启用配置并重启 Nginx**：
    ```bash
    # 建立软链接
    sudo ln -s /etc/nginx/sites-available/enterprise-rag /etc/nginx/sites-enabled/

    # 删除默认配置（避免冲突）
    sudo rm /etc/nginx/sites-enabled/default

    # 检查配置是否正确
    sudo nginx -t

    # 重启 Nginx
    sudo systemctl restart nginx
    ```

### 第十一步：启动后端服务 (设置开机自启)

为了让后端程序在后台运行且开机自动启动，我们创建一个系统服务。

1.  **创建服务文件**：
    ```bash
    sudo nano /etc/systemd/system/enterprise-rag.service
    ```

2.  **粘贴以下内容**：
    *(同样注意修改 `/home/ubuntu/...` 路径)*
    
    我们通过 `EnvironmentFile` 指令引入了第六步创建的配置文件。

    ```ini
    [Unit]
    Description=Enterprise Rag Community Backend
    After=syslog.target network.target mysql.service

    [Service]
    User=root
    
    # === 引入环境变量 ===
    # 从第六步创建的文件中读取 APP_MASTER_KEY 和 数据库配置
    EnvironmentFile=/etc/default/enterprise-rag
    
    # === 其他配置 ===
    # 注意：项目已启用动态密钥管理器。
    # Elasticsearch API Key、邮件配置、AI Key 等敏感信息
    # 请在服务启动后，访问 Web 界面进入「系统初始化向导」进行配置，无需在此处通过环境变量设置。
    
    # === 启动命令 ===
    # 这里指向刚才构建生成的 war 包
    ExecStart=/usr/bin/java -jar /home/ubuntu/EnterpriseRagCommunity/build/libs/EnterpriseRagCommunity.war
    SuccessExitStatus=143
    Restart=always

    [Install]
    WantedBy=multi-user.target
    ```
    **保存退出**：`Ctrl + O`, `Enter`, `Ctrl + X`。

3.  **启动服务**：
    ```bash
    # 重新加载服务列表
    sudo systemctl daemon-reload

    # 启动服务
    sudo systemctl start enterprise-rag

    # 设置开机自启
    sudo systemctl enable enterprise-rag

    # 查看服务状态（按 q 退出）
    sudo systemctl status enterprise-rag
    ```

### 验证与系统初始化
现在，您应该可以通过浏览器访问服务器的公网 IP 地址来看到您的应用了。
如果无法访问，请检查腾讯云控制台的**安全组**设置，确保 **TCP 80 端口**是开放的。

### 第十二步：系统初始化向导 (System Initialization)

首次访问系统时，应用会自动检测初始化状态，并跳转至 **系统初始化向导** (`/admin-setup`)。
请在向导中完成以下配置：

1.  **管理员账号创建**：设置系统的初始超级管理员。
2.  **Elasticsearch 配置**：
    *   **主机**: `localhost`
    *   **端口**: `9200`
    *   **协议**: `http` (因为我们在 Step 7 中关闭了 SSL)
    *   **API Key**: 填入 **第七步** 中生成的 `encoded` 字符串。
3.  **其他服务**：
    *   根据向导提示配置邮件服务 (SMTP) 和 AI 模型密钥。
    
配置完成后，点击“完成设置”，系统将自动加载配置并进入登录页。
### 第十三步：日常维护与更新指南

部署完成后，您可能需要经常查看服务状态或更新代码。以下是常用操作命令：

#### 1. 查看后端服务状态与日志

*   **查看运行状态**（查看是否正在运行、启动时间等）：
    ```bash
    sudo systemctl status enterprise-rag
    ```
    *(按 `q` 退出查看)*

*   **查看实时应用日志**（非常重要，用于排查报错）：
    ```bash
    # -f 表示 follow (实时刷新)，-u 指定服务名
    sudo journalctl -u enterprise-rag -f
    ```
    *(按 `Ctrl + C` 停止查看)*

#### 2. 管理后端服务

*   **停止服务**：
    ```bash
    sudo systemctl stop enterprise-rag
    ```
*   **启动服务**：
    ```bash
    sudo systemctl start enterprise-rag
    ```
*   **重启服务**（修改配置后常用）：
    ```bash
    sudo systemctl restart enterprise-rag
    ```

#### 3. 管理 Docker 容器 (Elasticsearch)

如果您需要手动控制 Elasticsearch 容器：

*   **停止容器**：
    ```bash
    docker stop elasticsearch
    ```
*   **启动容器**：
    ```bash
    docker start elasticsearch
    ```
*   **查看容器日志**：
    ```bash
    docker logs -f elasticsearch
    ```

#### 4. 拉取最新代码并更新部署

当您在本地开发并 Push 了新代码到 GitHub 后，服务器端需要执行以下步骤来应用更新：

```bash
# 1. 进入项目目录
cd ~/EnterpriseRagCommunity

# 2. 拉取最新代码
git pull

# 3. 重新构建后端 (如果修改了 Java 代码)
./gradlew bootWar
# 重启服务以应用更改
sudo systemctl restart enterprise-rag

# 4. 重新构建前端 (如果修改了 React 代码)
cd my-vite-app
npm install  # 以防有新的依赖
npm run build
cd ..        # 回到根目录
# 前端构建完成后立即生效（无需重启 Nginx，除非修改了 Nginx 配置）
```

#### 4. 重置数据库 (测试环境专用)

如果您在测试过程中需要清空数据库重新开始，可以使用以下命令删除并重新创建数据库：

```bash
# 1. 删除旧数据库
sudo mysql -u root -p -e "DROP DATABASE IF EXISTS EnterpriseRagCommunity;"

# 2. 重启后端服务（确保连接池重置）
sudo systemctl restart enterprise-rag
```
**警告**：此操作会永久删除所有业务数据，请谨慎操作！


# 修改端口
          
由于 80 端口在国内服务器上必须完成 ICP 备案才能对外开放，未备案时会被云厂商自动拦截。

您可以通过将 Nginx 的监听端口修改为其他非标准端口（如 **8080**）来绕过此限制，以便于进行测试。以下是具体操作步骤，请直接在您的腾讯云 Ubuntu 服务器终端中执行：

### 第一步：修改 Nginx 监听端口
我们可以使用 `sed` 命令直接将配置文件中的 `80` 端口替换为 `8080`。请复制并执行以下命令：

```bash
# 备份原配置文件（养成好习惯）
sudo cp /etc/nginx/sites-available/enterprise-rag /etc/nginx/sites-available/enterprise-rag.bak

# 将 listen 80; 修改为 listen 8080;
sudo sed -i 's/listen 80;/listen 8080;/g' /etc/nginx/sites-available/enterprise-rag
```

### 第二步：检查并重启 Nginx
修改完成后，需要验证配置文件的语法是否正确，并重启 Nginx 使更改生效。

```bash
# 检查 Nginx 配置语法
sudo nginx -t

# 如果显示 "syntax is ok" 和 "test is successful"，则重启 Nginx
sudo systemctl restart nginx
```

### 第三步：在腾讯云控制台开放 8080 端口
仅仅在服务器上修改是不够的，您还需要在腾讯云的防火墙（安全组）中放行 8080 端口：

1. 登录 [腾讯云控制台](https://console.cloud.tencent.com/cvm/index)。
2. 找到您的实例，点击**“安全组”**选项卡。
3. 点击**“编辑规则”** -> **“入站规则”**。
4. 点击**“添加规则”**：
   - **类型**：自定义
   - **来源**：`0.0.0.0/0` (允许所有 IP 访问)
   - **协议端口**：`TCP:8080`
   - **策略**：允许
5. 保存规则。

### 第四步：验证访问
现在，您应该可以通过 IP 加端口的方式访问您的网站了：

**访问地址**：`http://<您的服务器公网IP>:8080`

### 补充说明
- **关于备案**：如果您后续购买了域名并完成了备案，可以随时按照上述步骤将端口改回 80（只需将 `sed` 命令中的 `8080` 和 `80` 对调即可）。
- **常用端口**：如果 8080 也被占用，您还可以尝试 8081、8088 等端口。



# docker 容器维护

Elasticsearch 的内存配置（特别是 JVM 堆内存 `ES_JAVA_OPTS`）是启动时参数，**无法在容器运行时直接动态修改**。

您必须**删除旧容器并使用新参数重新启动**。

由于之前的启动命令中**没有挂载数据卷**（`-v`），直接删除容器会导致**索引数据丢失**。

请根据您的需求选择以下两种方案之一：

### 方案一：数据不重要（直接重建）
如果您还在测试阶段，不在乎数据丢失，可以直接删除重建。

1.  **停止并删除容器**
    ```powershell
    docker stop elasticsearch
    docker rm elasticsearch
    ```

2.  **使用新内存配置启动**（例如改为 2GB 堆内存，容器限额 2.5GB）
    ```powershell
    docker run -d --name elasticsearch `
      --net elastic `
      -p 9200:9200 `
      -e "discovery.type=single-node" `
      -e "xpack.security.enabled=true" `
      -e "xpack.security.http.ssl.enabled=false" `
      -e "ES_JAVA_OPTS=-Xms2g -Xmx2g" `
      -m 2.5GB `
      docker.elastic.co/elasticsearch/elasticsearch:9.2.4
    ```
    *(注：PowerShell 中换行符为反引号 ` ` ，如果您使用 CMD 请改用 `^`，Bash 使用 `\`)*

---

### 方案二：保留数据修改（推荐）
如果您已经有了重要数据，请先将数据从容器中拷贝出来，再挂载回去。

1.  **创建本地数据目录**
    ```powershell
    # 在当前目录下创建 es_data 文件夹
    mkdir es_data
    ```

2.  **将容器内的数据复制出来**
    ```powershell
    docker cp elasticsearch:/usr/share/elasticsearch/data ./es_data
    ```

3.  **停止并删除旧容器**
    ```powershell
    docker stop elasticsearch
    docker rm elasticsearch
    ```

4.  **启动新容器（挂载刚才复制的数据卷）**
    注意新增了 `-v` 参数挂载本地目录。
    ```powershell
    docker run -d --name elasticsearch \
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

### 验证修改
启动后，您可以进入容器检查 JVM 参数是否生效：
```powershell
docker exec elasticsearch env | grep "ES_JAVA_OPTS"
```
或者查看容器内存限制：
```powershell
docker stats elasticsearch --no-stream
```