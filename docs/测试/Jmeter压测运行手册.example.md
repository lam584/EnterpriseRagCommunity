# 双机压测运行手册（Linux + JMeter 分布式内网版）

## 1. 目标与角色

本方案用于：在内网环境下，由压测机运行 JMeter 分布式模式，对被测机上的 EnterpriseRagCommunity 服务进行压测。

请先填写机器信息：

压测控制机 SSH：


压测执行机 SSH（可多台）：


被测机 SSH：


压测控制机内网 IP：


压测执行机内网 IP 列表：


被测机内网 IP：


角色说明：
- 压测控制机（Controller）：下发测试计划、汇总结果。
- 压测执行机（Worker/Server）：接收控制机命令并发压。
- 被测机（SUT）：运行 EnterpriseRagCommunity，由 Nginx 对外暴露 80/443，压测默认打 80。

推荐拓扑：
- Controller(10.x.x.10) -> Worker(10.x.x.11,10.x.x.12,...) -> SUT(10.x.x.20:80)

说明：
- 内网压测时，所有目标地址统一使用内网 IP，不走公网。
- 总并发约等于 单 Worker 线程数 x Worker 数量。

---

## 2. 三类机器从 0 开始的配置分工

为避免把多台机器的步骤混在一起，先明确每台机器各自要做什么。

### 2.1 压测控制机（Controller）

从 0 开始至少完成以下事项：
- 安装基础依赖、JRE/JDK、JMeter。
- 克隆完整项目仓库，确保本地有 `perf/jmeter` 测试计划与结果目录。
- 如机器启用了防火墙，建议固定 JMeter Client 回连端口，避免使用随机高位端口。
- 负责下发分布式压测命令、汇总 `results.jtl`、生成 HTML 报告。

### 2.2 压测执行机（Worker）

从 0 开始至少完成以下事项：
- 安装基础依赖、JRE/JDK、JMeter。
- 配置 `jmeter-server` 的固定监听与 RMI 端口。
- 配置 `java.rmi.server.hostname` 为当前 Worker 的内网 IP。
- 启动 `jmeter-server`，并确保控制机能连通。

### 2.3 被测机（SUT）

从 0 开始至少完成以下事项：
- 按部署文档完成 Java、Node.js、MySQL、Nginx、Docker/Elasticsearch 等基础环境准备。
- 完成项目构建与启动，并确认服务在内网地址可访问。
- 压测前不要直接照搬生产或示例环境的 Docker 资源参数，尤其是 Elasticsearch 容器内存上限与 JVM Heap，必须结合当前机器的 CPU、内存、磁盘规格重新评估。
- 使用 `perf` profile 或其他明确约定的压测口径启动，保证每轮测试口径一致。

---

## 3. 压测控制机（Controller）从 0 开始初始化

以下步骤只在压测控制机执行一次。

### 3.1 更新系统并安装基础依赖

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y git curl wget unzip tar openjdk-21-jre-headless procps
java -version
```

### 3.2 调整 Linux 网络与文件句柄（压测建议）

```bash
cat <<'EOF' | sudo tee /etc/sysctl.d/99-jmeter-load.conf
net.ipv4.ip_local_port_range=1024 65535
net.core.somaxconn=65535
net.ipv4.tcp_fin_timeout=15
net.ipv4.tcp_tw_reuse=1
EOF

sudo sysctl --system
```

查看是否生效：

```bash
sysctl net.ipv4.ip_local_port_range
sysctl net.core.somaxconn
ulimit -n
```

若 `ulimit -n` 太小（例如 1024），建议临时提升后再压测：

```bash
ulimit -n 65535
```

### 3.3 从 GitHub 下载项目

压测控制机至少需要完整仓库（包含 JMeter 脚本与结果目录）。

```bash
cd ~
git clone https://github.com/lam584/EnterpriseRagCommunity.git
cd EnterpriseRagCommunity
ls -al perf/jmeter
```

### 3.4 安装 JMeter

```bash
cd ~
JMETER_VERSION=5.6.3
wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz
tar -xzf apache-jmeter-${JMETER_VERSION}.tgz
ln -sfn ~/apache-jmeter-${JMETER_VERSION} ~/jmeter
~/jmeter/bin/jmeter -v
```

可选：加入 PATH（当前用户）。

```bash
echo 'export PATH=$HOME/jmeter/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### 3.5 建议固定控制机回连端口

若控制机开启了防火墙，建议额外固定 JMeter Client 回连端口，避免 Worker 回连控制机时命中随机端口。

```bash
cp ~/jmeter/bin/jmeter.properties ~/jmeter/bin/jmeter.properties.bak
nano ~/jmeter/bin/jmeter.properties
```

追加或修改：

```properties
client.rmi.localport=50001
server.rmi.ssl.disable=true
```

说明：
- `client.rmi.localport=50001` 不是 JMeter 默认必填项，但在开启防火墙的内网环境下强烈建议固定。
- 后续需在控制机防火墙放行 `50001/tcp`，来源限定为各 Worker 内网 IP。

---

## 4. 压测执行机（Worker）从 0 开始初始化

以下步骤在每一台 Worker 上都执行一次。

### 4.1 更新系统并安装基础依赖

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y git curl wget unzip tar openjdk-21-jre-headless procps
java -version
```

### 4.2 调整 Linux 网络与文件句柄（压测建议）

```bash
cat <<'EOF' | sudo tee /etc/sysctl.d/99-jmeter-load.conf
net.ipv4.ip_local_port_range=1024 65535
net.core.somaxconn=65535
net.ipv4.tcp_fin_timeout=15
net.ipv4.tcp_tw_reuse=1
EOF

sudo sysctl --system
ulimit -n 65535
```

### 4.3 安装 JMeter

```bash
cd ~
JMETER_VERSION=5.6.3
wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz
tar -xzf apache-jmeter-${JMETER_VERSION}.tgz
ln -sfn ~/apache-jmeter-${JMETER_VERSION} ~/jmeter
~/jmeter/bin/jmeter -v
```

### 4.4 配置 Worker 固定端口

```bash
cp ~/jmeter/bin/jmeter.properties ~/jmeter/bin/jmeter.properties.bak
nano ~/jmeter/bin/jmeter.properties
```

追加或修改以下项：

```properties
server_port=1099
server.rmi.localport=50000
server.rmi.ssl.disable=true
java.rmi.server.hostname=<当前Worker内网IP>
```

说明：
- `server_port=1099`：控制机连接 Worker 的 JMeter Server 注册端口。
- `server.rmi.localport=50000`：固定 Worker RMI 通信端口，便于防火墙精确放行。
- `java.rmi.server.hostname`：必须写当前 Worker 内网 IP，不要写 `127.0.0.1`。

### 4.5 启动并验证 Worker

```bash
nohup ~/jmeter/bin/jmeter-server > ~/jmeter-server.log 2>&1 &
ps -ef | grep jmeter-server | grep -v grep
tail -n 50 ~/jmeter-server.log
```

---

## 5. 被测机（SUT）从 0 开始部署与调优

### 5.1 先盘点当前机器资源，不要直接套示例值

被测机通常与生产环境规格不同，压测环境又经常是单机同时承载应用、数据库、Elasticsearch、Nginx，因此在部署前先检查：

```bash
nproc
free -h
df -h
lsblk
```

至少确认：
- CPU 核数是否足够支撑应用线程池、数据库和 Elasticsearch 并行运行。
- 可用内存是否足够同时承载应用 JVM、MySQL、Elasticsearch、系统缓存。
- 磁盘容量与磁盘类型是否能支撑索引写入、日志和压测数据增长。

### 5.2 以部署文档为基础，但按当前机器规格调整参数

被测机若是初始环境，请先按部署文档完成服务部署：
- docs/Ubuntu 24.04 项目部署方案.md

但压测环境不要直接照抄该文档中的默认 Docker 内存参数，尤其不要机械使用 Elasticsearch 示例中的：

```bash
-e "ES_JAVA_OPTS=-Xms2560m -Xmx2560m"
-m 3GB
```

原因：
- 该示例值只适用于有足够余量的机器，不适用于所有被测机。
- 若被测机 CPU、内存、磁盘小于生产环境，直接套用可能导致容器 OOM、宿主机内存争抢、系统抖动，最终压测结果失真。

调整原则：
- 先给操作系统预留内存，再给 MySQL、应用 JVM、Elasticsearch 分配资源。
- Elasticsearch Heap 一般不要超过容器内存上限的大约一半。
- 若应用、数据库、Elasticsearch 共机部署，优先保证宿主机仍有稳定余量，不要把物理内存一次性吃满。

一个保守起步示例：
- 8GB 机器：可先尝试 `ES_JAVA_OPTS=-Xms2g -Xmx2g`，容器限制 `-m 3GB`。
- 16GB 机器：可先尝试 `ES_JAVA_OPTS=-Xms6g -Xmx6g`，容器限制 `-m 7GB` 或 `-m 8GB`。

如果被测机资源更小：
- 优先降低 Elasticsearch Heap 与容器内存限制。
- 评估是否将 MySQL、Elasticsearch 拆到独立机器，避免单机资源争抢把压测结果带偏。

### 5.3 从 0 部署的最低检查项

至少保证以下组件已按当前机器规格完成安装与启动：
- Java 运行环境。
- MySQL。
- Docker 与 Elasticsearch。
- Node.js 与前端构建产物。
- Nginx（负责对外暴露 80/443）。

### 5.4 启动前验收

至少保证：
- 服务已正常启动。
- 内网可通过 Nginx 访问 `http://<被测机内网IP>`；若启用 HTTPS，也可访问 `https://<被测机内网IP>`。
- 使用 `perf` profile 压测口径启动。
- Elasticsearch、MySQL、应用日志中无明显 OOM、连接失败、磁盘不足等异常。

启动命令建议：

```bash
java -jar build/libs/EnterpriseRagCommunity.war --spring.profiles.active=perf
```

---

## 6. 防火墙与端口放行清单

若三类机器启用了 `ufw`、云安全组或机房防火墙，至少按下表放行。

| 机器角色 | 方向 | 端口 | 来源 | 用途 |
| --- | --- | --- | --- | --- |
| Controller | 入站 | 22/tcp | 运维终端 IP | SSH 登录（可选） |
| Controller | 入站 | 50001/tcp | 各 Worker 内网 IP | Worker 回连控制机，前提是已配置 `client.rmi.localport=50001` |
| Worker | 入站 | 22/tcp | 运维终端 IP / Controller | SSH 登录（可选） |
| Worker | 入站 | 1099/tcp | Controller 内网 IP | JMeter Server 注册端口 |
| Worker | 入站 | 50000/tcp | Controller 内网 IP | Worker 固定 RMI 通信端口 |
| SUT | 入站 | 22/tcp | 运维终端 IP | SSH 登录（可选） |
| SUT | 入站 | 80/tcp | 各 Worker 内网 IP，必要时含 Controller | 默认压测入口，经 Nginx 走 HTTP |
| SUT | 入站 | 443/tcp | 各 Worker 内网 IP，必要时含 Controller | 若经 Nginx 走 HTTPS 压测 |

特别说明：
- 若控制机未配置 `client.rmi.localport`，Worker 回连控制机时可能使用随机高位端口，这会让防火墙策略很难收敛。
- 当前测试环境由 Nginx 统一承接压测流量，默认只需向 Worker 放行 `80`；若采用 HTTPS 再额外放行 `443`。
- MySQL `3306`、Elasticsearch `9200` 若仅本机使用，不建议对 Worker 或其他外部机器开放。

### 6.1 UFW 示例

控制机：

```bash
sudo ufw allow from <Worker1内网IP> to any port 50001 proto tcp
sudo ufw allow from <Worker2内网IP> to any port 50001 proto tcp
```

每台 Worker：

```bash
sudo ufw allow from <Controller内网IP> to any port 1099 proto tcp
sudo ufw allow from <Controller内网IP> to any port 50000 proto tcp
```

被测机（默认经 Nginx 走 80）：

```bash
sudo ufw allow from <Worker1内网IP> to any port 80 proto tcp
sudo ufw allow from <Worker2内网IP> to any port 80 proto tcp
```

若使用 HTTPS，再补充：

```bash
sudo ufw allow from <Worker1内网IP> to any port 443 proto tcp
sudo ufw allow from <Worker2内网IP> to any port 443 proto tcp
```

---

## 7. 控制机执行分布式压测（内网）

在控制机项目根目录执行：

```bash
cd ~/EnterpriseRagCommunity
RUN_ID=$(date +%Y%m%d_%H%M%S)
OUT_DIR=perf/jmeter/results/${RUN_ID}
mkdir -p "${OUT_DIR}"

~/jmeter/bin/jmeter -n \
  -t perf/jmeter/EnterpriseRagCommunity_basic_load.jmx \
  -R <Worker1内网IP>,<Worker2内网IP> \
  -l "${OUT_DIR}/results.jtl" \
  -j "${OUT_DIR}/jmeter.log" \
  -e -o "${OUT_DIR}/html" \
  -Ghost=<被测机内网IP> \
  -Gport=80 \
  -Gprotocol=http \
  -Gthreads=1000 \
  -GrampSeconds=60 \
  -GdurationSeconds=600 \
  -GthinkTimeMs=1000
```

参数说明：
- `-R`：Worker 列表（内网 IP，逗号分隔）。
- `-Gxxx`：将参数广播到所有 Worker。
- `threads=1000` 为单 Worker 线程数，若 2 台 Worker，总并发约 2000。

建议按“三阶段”推进，不要一开始就直接做长时间高并发压测。

### 7.1 第一阶段：1 分钟快速摸底

目标：
- 用短压快速摸清当前硬件在 `100% SuccessRate` 前提下的大致承载区间。
- 同步记录 CPU、内存、磁盘资源占用，先看哪一类资源最先逼近瓶颈。

建议梯度（总并发）：
- 500 -> 1000 -> 2000 -> 3000 -> 4000 -> 6000 -> 8000 -> 10000
- 每档持续 1 分钟，档间观察 1 分钟；若机器规格较高，再继续向上加档。

快速摸底时建议同时在被测机记录：

```bash
mpstat -P ALL 1
free -m
iostat -dx 1
df -h
```

快速摸底的判定标准：
- `SuccessRate = 100%`，不接受接口报错、超时、连接失败、Nginx 5xx。
- 记录每档的 P90、平均响应时间、TPS。
- 记录 CPU 利用率、可用内存、是否出现 Swap/频繁 Full GC、磁盘 util/await/队列长度。

若出现以下任一情况，说明该档位已经接近或超过当前机器上限：
- SuccessRate 低于 `100%`。
- CPU 长时间接近打满，且响应时间明显抬升。
- 内存持续吃紧，出现 Swap、频繁 GC 或明显抖动。
- 磁盘 util 持续偏高，await 明显升高，日志/索引写入出现堆积。

### 7.2 第二阶段：根据瓶颈收敛并发上限范围

方法：
- 取“最后一个 100% 成功档位”作为下界。
- 取“第一个失败档位”或“第一个明显逼近硬件瓶颈的档位”作为上界。
- 在上下界之间继续补点，例如 `6000 -> 6500 -> 7000 -> 7500 -> 8000`，每档仍先跑 1 分钟。

示例：
- 若 `6000` 并发 1 分钟内 `SuccessRate = 100%`，而 `8000` 已出现 CPU 持续 90%+ 且响应时间明显恶化，则当前可疑上限区间可先定为 `6000~8000`。
- 再在该区间补点，找出更接近真实上限且仍满足 `100% SuccessRate` 的档位。

### 7.3 第三阶段：再做更长时间压测

在确定候选上限后，再挑 2 到 3 个档位做长压验证：
- 保守档：候选上限的约 `70%`。
- 工作档：候选上限的约 `85%`。
- 冲刺档：最接近上限、但 1 分钟快速测试仍保持 `100% SuccessRate` 的档位。

长压建议：
- 每档持续 `10~30` 分钟。
- 长压期间继续记录 CPU、内存、磁盘、GC、数据库连接池、Elasticsearch 状态。
- 只要长压过程中 `SuccessRate` 无法维持 `100%`，或资源出现持续性瓶颈，该档位就不能作为稳定上限写入结论。

---

## 8. 结果采集与判读

每轮结果目录：
- perf/jmeter/results/<timestamp>/results.jtl
- perf/jmeter/results/<timestamp>/jmeter.log
- perf/jmeter/results/<timestamp>/html/index.html

重点指标：
- SuccessRate
- P90 / Avg
- TPS
- TopError 与错误数量
- SUT CPU、内存、磁盘、GC、数据库连接池占用

建议按下表记录每一档结果：

| 档位总并发 | 持续时长 | SuccessRate | P90 / Avg | TPS | CPU 峰值/均值 | 内存占用 | 磁盘 util/await | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 500 | 1 分钟 | 100% |  |  |  |  |  | 继续加压 |
| 1000 | 1 分钟 | 100% |  |  |  |  |  | 继续加压 |

判读原则：
- 第一优先级是 `SuccessRate = 100%`，不是单纯追求更高 TPS。
- 若某档位吞吐还在提升，但 CPU、内存或磁盘已经持续逼近瓶颈，应先判定硬件约束，再决定是否继续加压。
- 长压结论以“稳定运行 + 100% 成功率 + 资源无持续性失控”为准，不以单次峰值为准。

---

## 9. 常见问题排查

1. `Connection refused`（控制机到 Worker）
- Worker 上 `jmeter-server` 未启动，或端口不通。
- 检查 Worker 的 `1099`、`50000` 端口是否已向控制机放行。
- 若控制机开启了防火墙，还要检查控制机的 `50001` 端口是否已向各 Worker 放行。

2. `Address already in use`
- 压测机临时端口耗尽或 TIME_WAIT 过高。
- 降低单机并发，增加 Worker 数量分摊。

3. 分布式已启动但无流量
- `-R` IP 填错。
- `-Ghost` 不是被测机内网 IP。
- 被测机 `80` 未监听，或 Nginx 未正常转发到后端应用。
- 若经 HTTPS 压测，被测机 `443` 未监听或未向 Worker 放行。

4. 压测时业务日志过多影响性能
- 使用 `perf` profile（保留能力但降干扰）。

---

## 10. 停止与清理

在 Worker 停止 JMeter Server：

```bash
pkill -f ApacheJMeter.jar
```

在控制机保留结果并打包：

```bash
cd ~/EnterpriseRagCommunity/perf/jmeter/results
tar -czf latest-results.tar.gz <timestamp目录>
```

---

## 11. 推荐实践

- 压测报告保留两套口径：
  - 业务极限口径（perf profile）
  - 全日志口径（默认 profile）
- 每次压测必须记录：
  - Git 提交版本
  - Worker 数量
  - 单 Worker 线程数
  - 持续时长
  - 关键系统参数（sysctl、ulimit）
  - 各档位的 CPU、内存、磁盘占用记录
  - 最后一个 100% 成功档位与判定出的候选上限区间
