
## 6. 全链路最终验证

启动你的 Spring Boot 项目，然后执行以下检查：

### 6.1 检查应用日志
在 IDEA 控制台或日志文件中搜索关键字：
*   ✅ **正常**：无 `access_log_kafka_send_failed` 或 `access_log_es_sink_consume_failed`。
*   ⚠️ **警告**：如果出现 `access_log_dual_verify_miss`，说明 Kafka 有数据但 ES 没收到（或延迟高）。初期少量出现是正常的，持续出现需检查 ES 连接。

### 6.1.1 启动项目并挂到本地 Kafka


### 6.2 检查 Kafka 消费情况
再次运行消费者命令，看是否有新的业务日志进入：
```powershell
& "E:\kafka_2.13-4.2.0\bin\windows\kafka-console-consumer.bat" --bootstrap-server 127.0.0.1:9092 --topic access-logs-v1 --from-beginning --max-messages 3
```

更推荐用一个新的消费组，只看刚触发的请求日志：

```powershell
$group = "access-log-check-$(Get-Date -Format yyyyMMddHHmmss)"
& "E:\kafka_2.13-4.2.0\bin\windows\kafka-console-consumer.bat" --bootstrap-server 127.0.0.1:9092 --topic access-logs-v1 --group $group --max-messages 1 --timeout-ms 15000
```

然后在另一个窗口触发一条公开接口请求：

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8099/api/public/site-config" -UseBasicParsing | Out-Null
```

若消费者读到一条 JSON，且其中 `data.path` 为 `/api/public/site-config`，说明 **业务访问日志已经成功写入 Kafka**。

### 6.3 检查 ES 数据 (如果配置了 ES)
```powershell
# 假设 ES 在本地 9200，且无需鉴权
curl "http://127.0.0.1:9200/access-logs-v1/_search?size=1&pretty"
```
*   **成功标志**：返回 JSON 数据，`hits.total.value` > 0。

更精确的验证方式：

```powershell
curl "http://127.0.0.1:9200/access-logs-v1/_search?q=path.keyword:%2Fapi%2Fpublic%2Fsite-config&size=1&pretty"
```

如果能查到刚触发的访问记录，说明 **Kafka -> ES 消费链路也已经打通**。

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

# 后台启动（推荐）
.\scripts\start-kafka.cmd

# 检查 9092 / 9093 端口是否监听
Get-NetTCPConnection -LocalPort 9092,9093 -State Listen -ErrorAction SilentlyContinue

# 查看 Kafka Java 进程
Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java(.exe)?' -and $_.CommandLine -match 'kafka.Kafka' } | Select-Object ProcessId, CommandLine

# 停止 Kafka（推荐）
.\scripts\stop-kafka.cmd

# 查看最近日志
Get-Content ".\logs\kafka\kafka.stdout.log" -Tail 100
Get-Content ".\logs\kafka\kafka.stderr.log" -Tail 100
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

如果你使用的是本文新增的后台脚本，则直接执行：

```powershell
.\scripts\stop-kafka.cmd
```