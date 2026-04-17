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

## 6. 全链路最终验证

启动你的 Spring Boot 项目，然后执行以下检查：

### 6.1 检查应用日志
在 IDEA 控制台或日志文件中搜索关键字：
*   ✅ **正常**：无 `access_log_kafka_send_failed` 或 `access_log_es_sink_consume_failed`。
*   ⚠️ **警告**：如果出现 `access_log_dual_verify_miss`，说明 Kafka 有数据但 ES 没收到（或延迟高）。初期少量出现是正常的，持续出现需检查 ES 连接。

### 6.2 检查 Kafka 消费情况
再次运行消费者命令，看是否有新的业务日志进入：
```powershell
& "E:\kafka_2.13-4.2.0\bin\windows\kafka-console-consumer.bat" --bootstrap-server 127.0.0.1:9092 --topic access-logs-v1 --from-beginning --max-messages 3
```

### 6.3 检查 ES 数据 (如果配置了 ES)
```powershell
# 假设 ES 在本地 9200，且无需鉴权
curl "http://127.0.0.1:9200/access-logs-v1/_search?size=1&pretty"
```
*   **成功标志**：返回 JSON 数据，`hits.total.value` > 0。

---

## 7. Kafka 常用运维命令

下面这些命令适合日常排查 `access-logs-v1` 链路是否正常，默认仍使用本机单节点配置。

### 7.1 预设变量

```powershell
$KAFKA_HOME = "E:\kafka_2.13-4.2.0"
$BOOTSTRAP = "127.0.0.1:9092"
$TOPIC = "access-logs-v1"
```

### 7.2 启动、停止、查看状态

```powershell
# 前台启动（推荐排障时使用）
& "$KAFKA_HOME\bin\windows\kafka-server-start.bat" "$KAFKA_HOME\config\server.properties"

# 后台启动（日志输出到 kafka.log）
Start-Process -FilePath "$KAFKA_HOME\bin\windows\kafka-server-start.bat" `
    -ArgumentList "$KAFKA_HOME\config\server.properties" `
    -RedirectStandardOutput "$KAFKA_HOME\kafka.log" `
    -RedirectStandardError "$KAFKA_HOME\kafka-error.log"

# 检查 9092 / 9093 端口是否监听
Get-NetTCPConnection -LocalPort 9092,9093 -State Listen -ErrorAction SilentlyContinue

# 查看 Kafka Java 进程
Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java(.exe)?' -and $_.CommandLine -match 'kafka.Kafka' } | Select-Object ProcessId, CommandLine

# 停止 Kafka（按命令行匹配）
Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java(.exe)?' -and $_.CommandLine -match 'kafka.Kafka' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

# 查看最近日志
Get-Content "$KAFKA_HOME\kafka.log" -Tail 100
Get-Content "$KAFKA_HOME\kafka-error.log" -Tail 100
```

### 7.3 Topic 管理

```powershell
# 查看所有 Topic
& "$KAFKA_HOME\bin\windows\kafka-topics.bat" --bootstrap-server $BOOTSTRAP --list

# 查看 access-logs-v1 详情
& "$KAFKA_HOME\bin\windows\kafka-topics.bat" --bootstrap-server $BOOTSTRAP --describe --topic $TOPIC

# 增加分区数（只能增不能减）
& "$KAFKA_HOME\bin\windows\kafka-topics.bat" --bootstrap-server $BOOTSTRAP --alter --topic $TOPIC --partitions 6

# 删除 Topic（确认不再需要数据时再执行）
& "$KAFKA_HOME\bin\windows\kafka-topics.bat" --bootstrap-server $BOOTSTRAP --delete --topic $TOPIC
```

### 7.4 消费与堆积排查

```powershell
# 从头读取 5 条消息
& "$KAFKA_HOME\bin\windows\kafka-console-consumer.bat" --bootstrap-server $BOOTSTRAP --topic $TOPIC --from-beginning --max-messages 5

# 仅查看最新到来的消息
& "$KAFKA_HOME\bin\windows\kafka-console-consumer.bat" --bootstrap-server $BOOTSTRAP --topic $TOPIC --max-messages 5 --timeout-ms 10000

# 查看所有 Consumer Group
& "$KAFKA_HOME\bin\windows\kafka-consumer-groups.bat" --bootstrap-server $BOOTSTRAP --list

# 查看 access-log-es-sink-v1 消费组堆积
& "$KAFKA_HOME\bin\windows\kafka-consumer-groups.bat" --bootstrap-server $BOOTSTRAP --describe --group access-log-es-sink-v1

# 如果出现负 lag（CURRENT-OFFSET > LOG-END-OFFSET），先停止消费端进程后重置 offset
# 例如先停掉应用，再执行：
& "$KAFKA_HOME\bin\windows\kafka-consumer-groups.bat" --bootstrap-server $BOOTSTRAP --group access-log-es-sink-v1 --topic $TOPIC --reset-offsets --to-latest --execute
```

### 7.5 发送测试消息

```powershell
$msg = "ops-check-$(Get-Date -Format yyyyMMddHHmmss)"
Write-Output $msg | & "$KAFKA_HOME\bin\windows\kafka-console-producer.bat" --bootstrap-server $BOOTSTRAP --topic $TOPIC
```

如果这条消息能被消费者读到，说明 Kafka Broker、Topic 和基础读写链路正常。

---

## 8. 常见问题速查表 (Troubleshooting)

| 现象 | 可能原因 | 解决方案 |
| :--- | :--- | :--- |
| **`wmic 不是内部或外部命令`** | Win11 移除 wmic | 执行 **[1.2]** 中的脚本修复 `kafka-server-start.bat`。 |
| **`Invalid cluster.id`** | 数据目录残留旧 ID | 执行 **[3.1]** 清空 `data/kraft-logs` 目录后重新 Format。 |
| **`Timed out waiting for node assignment`** | Kafka 未启动或端口错 | 1. 确认 Kafka 启动窗口无报错。<br>2. 确认 `get-nettcpconnection` 能看到 9092 监听。<br>3. 检查 `server.properties` 中 `advertised.listeners` 是否为 `127.0.0.1`。 |
| **`Error while renaming dir ... AccessDeniedException` 且 `all log dirs ... have failed`** | `log.dirs` 根目录下混入了 Kafka 不识别的目录（如手工隔离目录），或旧分区目录在重命名为 stray/delete 时被拒绝 | 1. 停止 Kafka 进程。<br>2. 仅保留合法分区目录（形如 `topic-partition`）和 Kafka 元数据目录。<br>3. 将手工隔离目录移到 `log.dirs` 外部（例如 `data/manual-quarantine`）。<br>4. 重启 Kafka 后再执行 topic/group 校验命令。 |
| **Topic 分区数一直是 1** | `--if-not-exists` 不修改现有 Topic | 执行 **[4.2]** 中的 `--alter --partitions 6`。 |
| **消费组出现负 lag（例如 `-57`）** | Topic 曾被删除/重建，历史 committed offset 高于新日志末尾 | 1. 先停掉消费端（group 必须非 Stable）。<br>2. 执行 **[7.4]** 的 `--reset-offsets --to-latest --execute`。<br>3. 重启消费端并复查 group。 |
| **PowerShell 报“无法识别命令”** | 相对路径失效 | **永远使用绝对路径**，如 `E:\kafka...\bin\windows\xxx.bat`。 |
| **Java 版本冲突** | JDK 25 某些 API 变更 | 建议切换到 **JDK 21 LTS**，这是目前 Kafka 4.x 最稳定的搭配。 |
| **ES 连不上** | 鉴权或地址错误 | 检查 `APP_ES_API_KEY` 是否正确；本地开发确保 ES 允许匿名访问或配置了正确的 Basic Auth。 |

---

## 9. 停止服务

调试完成后，在启动 Kafka 的 PowerShell 窗口按 `Ctrl + C` 停止。
如果需要彻底清理环境重新开始，删除 `E:\kafka_2.13-4.2.0\data\kraft-logs` 目录即可。