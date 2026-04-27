这是一份经过深度优化的 **Kafka (KRaft模式) Windows 11 安装与配置实战指南**。

**优化核心思路：**
1.  **去碎片化**：将原本分散的“避坑指南”、“实测记录”和“常见问题”直接嵌入到对应的操作步骤中，形成“操作 -> 预期结果 -> 异常处理”的闭环。
2.  **防御性编程思维**：在每一步都加入前置检查（Pre-check）和后置验证（Post-check），确保小白用户即使出错也能快速定位。
3.  **标准化路径**：全程强制使用绝对路径变量，彻底解决 PowerShell 相对路径报错问题。
4.  **环境兼容性修复**：针对 Win11 缺失 `wmic` 和 JDK 版本差异提供自动化的脚本修正方案。

---

# Kafka (KRaft) Windows 11 单机部署与 Access 日志链路配置

**适用环境**：Windows 11, JDK 21+, Kafka 4.2.0 (`E:\kafka_2.13-4.2.0`)
**目标**：搭建本地 KRaft 集群，支持项目 Access 日志写入 Kafka 并同步至 ES。

> **💡 给新手的执行心法**
> 1.  **复制即用**：本文所有命令均使用 PowerShell 绝对路径，请直接复制执行，无需切换目录。
> 2.  **步步为营**：每执行完一个大章节，务必执行该章节末尾的【验证步骤】，绿灯亮后再继续。
> 3.  **抓主要矛盾**: 报错时忽略大量 INFO/DEBUG 日志，直接搜索关键词 `ERROR`, `Exception`, `Timeout`。
> 4.  **幂等性意识**：创建 Topic 时使用 `--if-not-exists` 是安全的；但修改分区数只能增不能减。

---

## 1. 前置环境与脚本修复

Kafka Windows 脚本在 Win11 上存在已知兼容性问题（`wmic` 缺失），必须先修复，否则后续启动必败。

### 1.1 检查基础环境
在 PowerShell 中执行：

```powershell
# 1. 检查 Java 版本 (建议 JDK 21 LTS，JDK 25 可能偶发兼容问题)
java -version

# 2. 检查 Kafka 根目录是否存在
Test-Path "E:\kafka_2.13-4.2.0\bin\windows\kafka-server-start.bat"
```
*   **若返回 `False`**：请确认解压路径是否正确。
*   **若 Java 未安装**：请先安装 JDK 21 并配置环境变量。

### 1.2 修复 Win11 `wmic` 缺失问题 (关键步骤)
Win11 默认移除了 `wmic.exe`，而旧版 Kafka 启动脚本依赖它获取内存信息。我们需要硬编码堆内存参数。

执行以下 PowerShell 脚本自动修复 `kafka-server-start.bat`：

```powershell
$batPath = "E:\kafka_2.13-4.2.0\bin\windows\kafka-server-start.bat"
$content = Get-Content $batPath -Raw

# 替换逻辑：如果检测到 wmic 相关逻辑或空 HEAP_OPTS，强制设置为固定值
# 这里采用更稳妥的方式：直接在文件头部或特定位置注入固定 Heap 设置，避开 wmic 调用
# 简单做法：找到 IF ["%KAFKA_HEAP_OPTS%"] EQU [""] 块，强制赋值

if ($content -match 'IF \["%KAFKA_HEAP_OPTS%"*\]') {
    # 备份原文件
    Copy-Item $batPath "$batPath.bak" -Force
  
    # 使用正则替换，将动态获取改为固定值 -Xmx1G -Xms1G
    # 注意：不同版本 Kafka 脚本略有不同，以下是通用修复逻辑
    $newContent = $content -replace '(IF \["%KAFKA_HEAP_OPTS%"\] EQU \[""\] \(\s*)set KAFKA_HEAP_OPTS=.*?(\))', '$1set KAFKA_HEAP_OPTS=-Xmx1G -Xms1G$2'
  
    # 如果正则没匹配到（脚本结构变化），则手动追加一行强制设置到文件开头附近
    if ($newContent -eq $content) {
         Write-Warning "正则匹配失败，尝试备用修复方案..."
         # 备用方案：在 @echo off 后插入
         $lines = Get-Content $batPath
         $newLines = @()
         foreach ($line in $lines) {
             $newLines += $line
             if ($line -match "@echo off") {
                 $newLines += "set KAFKA_HEAP_OPTS=-Xmx1G -Xms1G"
             }
         }
         Set-Content $batPath $newLines
    } else {
        Set-Content $batPath $newContent
    }
    Write-Host "✅ kafka-server-start.bat 已修复 (Heap 设为 1G)" -ForegroundColor Green
} else {
    Write-Host "⚠️ 未发现典型 wmic 逻辑，可能无需修复或脚本版本已更新。" -ForegroundColor Yellow
}
```

---

## 2. 配置 KRaft 单机模式

编辑配置文件：`E:\kafka_2.13-4.2.0\config\server.properties`。
*建议使用 Notepad++ 或 VS Code 打开，确保保存格式为 UTF-8。*

清理原有内容或注释掉 Zookeeper 相关配置，确保包含以下关键项：

```properties
# --- KRaft 角色定义 ---
process.roles=broker,controller
node.id=1

# --- 网络监听 ---
# 9092: 客户端通信端口
# 9093: 控制器内部通信端口
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://127.0.0.1:9092,CONTROLLER://127.0.0.1:9093
controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT

# --- 控制器选举配置 (关键) ---
# 格式: node.id@host:port
controller.quorum.voters=1@127.0.0.1:9093
# 兼容旧写法，建议保留
controller.quorum.bootstrap.servers=127.0.0.1:9093

# --- 数据存储路径 (Windows 必须用绝对路径，避免权限问题) ---
log.dirs=E:/kafka_2.13-4.2.0/data/kraft-logs
```

> **⚠️ 避坑提示**：
> *   `log.dirs` 不要使用 `/tmp` 或相对路径，Windows 下务必使用盘符如 `E:/...`。
> *   `controller.quorum.voters` 必须与 `node.id` 对应，否则格式化存储时会报错。

---

## 3. 初始化存储与启动服务

### 3.1 清理旧数据 (防止 Cluster ID 冲突)
如果你之前尝试过启动但失败了，或者更换了配置，**必须**清空数据目录，否则会出现 `Invalid cluster.id` 错误。

```powershell
# 强制删除旧数据目录
Remove-Item -Path "E:\kafka_2.13-4.2.0\data\kraft-logs" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "E:\kafka_2.13-4.2.0\data\kraft-logs" | Out-Null
Write-Host "🧹 数据目录已清理" -ForegroundColor Cyan
```

### 3.2 生成 Cluster ID 并格式化存储

```powershell
$KAFKA_HOME = "E:\kafka_2.13-4.2.0"
$CONFIG_FILE = "$KAFKA_HOME\config\server.properties"
$STORAGE_CMD = "$KAFKA_HOME\bin\windows\kafka-storage.bat"

# 1. 生成 UUID
Write-Host "⏳ 正在生成 Cluster ID..."
$clusterId = (& $STORAGE_CMD random-uuid 2>$null).Trim()

# 注意：random-uuid 可能会报 log4j ERROR，只要输出了 UUID 字符串即可忽略
if ([string]::IsNullOrWhiteSpace($clusterId)) {
    Throw "❌ 无法生成 Cluster ID，请检查 Java 环境"
}
Write-Host "✅ Cluster ID: $clusterId" -ForegroundColor Green

# 2. 格式化存储 (--standalone 用于单机模式，规避复杂的 quorum 配置检查)
Write-Host "⏳ 正在格式化存储..."
& $STORAGE_CMD format --standalone -t $clusterId -c $CONFIG_FILE

if ($LASTEXITCODE -ne 0) {
    Throw "❌ 存储格式化失败，请检查 server.properties 配置"
}
Write-Host "✅ 存储格式化成功" -ForegroundColor Green
```

### 3.3 启动 Kafka 服务

**推荐首次使用前台启动**，以便观察实时日志。新开一个 PowerShell 窗口执行：

```powershell
& "E:\kafka_2.13-4.2.0\bin\windows\kafka-server-start.bat" "E:\kafka_2.13-4.2.0\config\server.properties"
```

*看到 `Kafka Server started` 字样且无报错退出，即表示启动成功。保持此窗口开启。*

### 3.3.1 后台启动 / 停止脚本 (推荐日常开发使用)

仓库已补充两组脚本：`.cmd` 用于日常直接执行，`.ps1` 用于需要传参或二次开发的场景。日常开发优先使用 `.cmd`：

```powershell
# 在项目根目录执行
.\scripts\start-kafka.cmd

# 停止 Kafka
.\scripts\stop-kafka.cmd
```

脚本行为说明：

1. 自动使用 `JAVA_HOME` / `KAFKA_HOME`；未配置时回退到本文默认路径。
2. 自动检查 `9092/9093`，若 Kafka 已在运行则直接返回，不重复启动。
3. 若检测到 `meta.properties` 不存在，会自动执行一次 `kafka-storage.bat format`。
4. 日志输出到项目目录 `logs\kafka\kafka.stdout.log` 与 `logs\kafka\kafka.stderr.log`。
5. 后台启动成功后，会在 `logs\kafka\kafka.pid` 中记录实际 Kafka Java 进程 PID。

### 3.4 验证服务状态

在原 PowerShell 窗口执行：

```powershell
# 检查端口监听
$conn = Get-NetTCPConnection -LocalPort 9092 -State Listen -ErrorAction SilentlyContinue

if ($conn) {
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($conn.OwningProcess)"
    Write-Host "✅ Kafka 正在运行 (PID: $($conn.OwningProcess))" -ForegroundColor Green
    Write-Host "   进程命令: $($proc.CommandLine)"
} else {
    Write-Host "❌ Kafka 未在 9092 端口监听，请检查启动窗口日志" -ForegroundColor Red
}
```

---

## 4. 创建与验证 Access 日志 Topic

### 4.1 创建 Topic
我们创建名为 `access-logs-v1` 的 Topic，6 个分区，1 个副本（单机仅限 1）。

```powershell
$TOPIC_CMD = "E:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat"
$BOOTSTRAP = "127.0.0.1:9092"

# 创建 Topic (如果已存在则跳过，不会报错)
& $TOPIC_CMD --bootstrap-server $BOOTSTRAP --create --if-not-exists --topic access-logs-v1 --partitions 6 --replication-factor 1

# 立即查看详情，确认分区数
Write-Host "--- Topic 详情 ---"
& $TOPIC_CMD --bootstrap-server $BOOTSTRAP --describe --topic access-logs-v1
```

### 4.2 纠偏：如果分区数不对
如果上面的 `describe` 输出显示 `PartitionCount: 1`（说明之前已经以默认配置创建过），`--if-not-exists` 不会修改它。你需要手动扩容：

```powershell
# 仅当分区数不足 6 时执行
& $TOPIC_CMD --bootstrap-server $BOOTSTRAP --alter --topic access-logs-v1 --partitions 6

# 再次确认
& $TOPIC_CMD --bootstrap-server $BOOTSTRAP --describe --topic access-logs-v1
```
*预期输出应包含 `PartitionCount: 6`。*

### 4.3 冒烟测试 (自发自收)
这是验证 Kafka 链路是否通畅的最可靠方法。

> **⚠️ 关键修正**：在 PowerShell 中直接对 `.bat`脚本使用变量管道 (`$msg | & ...`) 极易导致参数解析失败（报错 `Missing required argument "[topic]"`）。请务必使用 `Write-Output` 显式管道或下方提供的命令格式。

```powershell
$PRODUCER_CMD = "E:\kafka_2.13-4.2.0\bin\windows\kafka-console-producer.bat"
$CONSUMER_CMD = "E:\kafka_2.13-4.2.0\bin\windows\kafka-console-consumer.bat"
$BOOTSTRAP = "127.0.0.1:9092"

# 1. 生成唯一测试消息
$msg = "smoke-test-$(Get-Date -Format yyyyMMddHHmmss)"
Write-Host "📤 发送测试消息: $msg" -ForegroundColor Cyan

# 2. 发送消息 
# 【重要】使用 Write-Output 确保字符串正确通过管道传递给 bat 脚本
Write-Output $msg | & $PRODUCER_CMD --bootstrap-server $BOOTSTRAP --topic access-logs-v1

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 生产者发送失败，请检查 Kafka 服务状态" -ForegroundColor Red
} else {
    Write-Host "✅ 消息已送入 Kafka缓冲区" -ForegroundColor Green
}

# 3. 接收消息 (超时 10 秒，只收 1 条)
Write-Host "📥 等待接收消息..." -ForegroundColor Yellow
& $CONSUMER_CMD --bootstrap-server $BOOTSTRAP --topic access-logs-v1 --from-beginning --max-messages 1 --timeout-ms 10000

# 4. 结果判断
if ($LASTEXITCODE -eq 0) {
    Write-Host "🎉 全链路测试通过！Kafka 读写正常。" -ForegroundColor Green
} else {
    Write-Host "⚠️ 消费者未收到消息或超时。" -ForegroundColor Red
    Write-Host "   排查建议："
    Write-Host "   1. 确认 Kafka 启动窗口无 ERROR 日志。"
    Write-Host "   2. 确认 Topic 'access-logs-v1' 已存在且分区正常。"
    Write-Host "   3. 尝试增加 --timeout-ms 到 30000。"
}
```

*   **成功标志**：
    1.  生产者执行后无报错退出。
    2.  消费者控制台打印出 `smoke-test-xxxx`。
    3.  最后显示 `Processed a total of 1 messages`。

---

## 5. 项目应用配置

在项目 `application.properties` 或环境变量中配置以下参数。

### 5.1 基础连接配置
```properties
# Kafka 地址
spring.kafka.bootstrap-servers=127.0.0.1:9092

# 日志链路模式：DUAL (双写验证) 或 KAFKA (仅 Kafka)
# 建议开发阶段先用 DUAL，观察 verify_miss 指标
app.logging.access.sink-mode=DUAL
app.logging.access.kafka-topic=access-logs-v1
```

### 5.1.1 当前仓库的本地默认值

这次已将项目默认配置调整为适合本机单机链路：

```properties
spring.kafka.bootstrap-servers=127.0.0.1:9092
app.logging.access.sink-mode=KAFKA
app.logging.access.kafka-topic=access-logs-v1
app.logging.access.force-mysql-during-setup=false
APP_KAFKA_AUTH_ENABLED=false
APP_KAFKA_SECURITY_PROTOCOL=PLAINTEXT
app.logging.access.es-sink.enabled=true
app.logging.access.es-sink.consumer-enabled=true
```

这意味着：

1. Spring Boot 应用默认会把 Access 日志写入本机 Kafka。
2. 即使当前实例还处于 `isInitialized=false` 的初始化阶段，也不会被强制回退到 MySQL。
3. Kafka -> ES 的消费端默认也会启动，前提是本机 `9200` 有可用 ES。
4. 若你临时不想消费写 ES，可将 `app.logging.access.es-sink.consumer-enabled=false` 覆盖到环境变量或启动参数中。

### 5.2 ES Sink 配置 (如果需要写入 ES)
```properties
app.logging.access.es-sink.enabled=true
app.logging.access.es-sink.consumer-enabled=true
app.logging.access.es-sink.index=access-logs-v1
app.logging.access.es-sink.consumer-group=access-log-es-sink-v1

# 双写验证开关
app.logging.access.es-sink.dual-verify-enabled=true
app.logging.access.es-sink.dual-verify-log-on-success=false
```

### 5.3 本地开发免鉴权配置
由于是本地单机 Kafka，**不需要** SASL 鉴权。请确保以下配置为 false 或空：

```properties
APP_KAFKA_AUTH_ENABLED=false
APP_KAFKA_API_KEY=
APP_KAFKA_API_SECRET=
# 确保没有启用 SSL/SASL 协议
# spring.kafka.properties.security.protocol=PLAINTEXT (通常默认即可)
```

> **注意**：如果你的项目代码中默认开启了 `SASL_SSL`，请在本地 Profile 中显式覆盖为 `PLAINTEXT`。

### 5.4 生产者性能调优 (可选)
```properties
app.logging.access.kafka.producer.acks=all
app.logging.access.kafka.producer.idempotence=true
app.logging.access.kafka.producer.retries=10
app.logging.access.kafka.producer.compression-type=lz4
```

---
