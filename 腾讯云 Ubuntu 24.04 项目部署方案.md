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

项目配置使用了较新的 Java 版本，我们安装目前最稳定的 JDK 21。

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

### 第六步：安装 Docker 与 Elasticsearch (搜索引擎)

项目依赖 Elasticsearch。使用 Docker 安装是最简单的。

1.  **安装 Docker**：
    ```bash
    sudo apt install docker.io -y
    ```

2.  **启动 Elasticsearch 容器**：
    此命令会下载并启动一个单节点的 Elasticsearch。
    ```bash
    sudo docker run -d --name elasticsearch \
      -p 9200:9200 \
      -e "discovery.type=single-node" \
      -e "xpack.security.enabled=false" \
      elasticsearch:9.2.4
    ```

### 第七步：安装 Node.js 并构建前端

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

### 第八步：构建后端代码

现在我们编译 Java 后端。

```bash
# 开始构建（第一次运行会下载 Gradle 和依赖，比较慢，请耐心等待）
./gradlew bootWar
```
*如果显示 `BUILD SUCCESSFUL`，说明构建成功。生成的文件在 `build/libs/` 目录下。*

### 第九步：配置 Nginx (Web 服务器)

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

### 第十步：启动后端服务 (设置开机自启)

为了让后端程序在后台运行且开机自动启动，我们创建一个系统服务。

1.  **创建服务文件**：
    ```bash
    sudo nano /etc/systemd/system/enterprise-rag.service
    ```

2.  **粘贴以下内容**：
    *(同样注意修改 `/home/ubuntu/...` 路径)*

    ```ini
    [Unit]
    Description=Enterprise Rag Community Backend
    After=syslog.target network.target mysql.service

    [Service]
    User=root
    # 强制覆盖 ES 地址为本地 Docker 地址
    Environment="SPRING_ELASTICSEARCH_URIS=http://localhost:9200"
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

### 验证

现在，您应该可以通过浏览器访问服务器的公网 IP 地址来看到您的应用了。
如果无法访问，请检查腾讯云控制台的**安全组**设置，确保 **TCP 80 端口**是开放的。
