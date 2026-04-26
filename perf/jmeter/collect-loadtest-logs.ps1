param(
  [string]$KeyPath = "E:\DownLoads\test123.pem",
  [string]$SutHost = "root@8.138.17.175",
  [string]$SutInternalIp = "172.20.170.194",
  [string]$ControllerHost = "root@8.166.143.72",
  [string]$ControllerInternalIp = "172.20.170.193",
  [string]$Since = "2 days ago",
  [string]$BundleName = "",
  [string]$ControllerSaveRoot = "/root/loadtest-log-bundles",
  [string]$SutProjectDir = "/root/EnterpriseRagCommunity",
  [string]$ControllerProjectDir = "/root/EnterpriseRagCommunity",
  [string]$ControllerJMeterResultsRoot = "/root/EnterpriseRagCommunity/perf/jmeter/results"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BundleName)) {
  $BundleName = "loadtest-logs-$(Get-Date -Format 'yyyyMMdd_HHmmss')"
}

$sshArgs = @(
  "-i", $KeyPath,
  "-o", "IdentitiesOnly=yes",
  "-o", "PubkeyAuthentication=yes",
  "-o", "StrictHostKeyChecking=accept-new"
)

function Invoke-RemoteCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RemoteHost,
    [Parameter(Mandatory = $true)]
    [string]$Command
  )

  & ssh @sshArgs $RemoteHost $Command
  if ($LASTEXITCODE -ne 0) {
    throw "Remote command failed on ${RemoteHost}: $Command"
  }
}

function Copy-ToRemote {
  param(
    [Parameter(Mandatory = $true)]
    [string]$LocalPath,
    [Parameter(Mandatory = $true)]
    [string]$RemoteTarget
  )

  & scp @sshArgs $LocalPath $RemoteTarget
  if ($LASTEXITCODE -ne 0) {
    throw "Copy to remote failed: $LocalPath -> $RemoteTarget"
  }
}

function New-RemoteScript {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,
    [Parameter(Mandatory = $true)]
    [string]$Content
  )

  $resolved = Resolve-Path -LiteralPath $FilePath
  $remoteTarget = "$SutHost`:/tmp/$BundleName-$([System.IO.Path]::GetFileName($resolved.Path))"
  Copy-ToRemote -LocalPath $resolved.Path -RemoteTarget $remoteTarget
  return "/tmp/$BundleName-$([System.IO.Path]::GetFileName($resolved.Path))"
}

function Write-Utf8NoBom {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [Parameter(Mandatory = $true)]
    [string]$Content
  )

  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  $normalized = $Content -replace "`r`n", "`n"
  [System.IO.File]::WriteAllText($Path, $normalized, $utf8NoBom)
}

$localTempDir = Join-Path $env:TEMP $BundleName
New-Item -ItemType Directory -Force -Path $localTempDir | Out-Null

$sutCollectorPath = Join-Path $localTempDir "sut-collect.sh"
$sutCollector = @'
#!/usr/bin/env bash
set -euo pipefail

BUNDLE_NAME="$1"
SINCE_VALUE="$2"
DEFAULT_PROJECT_DIR="$3"

WORK_DIR="/tmp/${BUNDLE_NAME}-sut"
PAYLOAD_DIR="${WORK_DIR}/payload"
META_DIR="${PAYLOAD_DIR}/metadata"
ARCHIVE_PATH="/tmp/${BUNDLE_NAME}-sut.tar.gz"
HASH_PATH="${ARCHIVE_PATH}.sha256"

rm -rf "$WORK_DIR"
mkdir -p "$META_DIR" "$PAYLOAD_DIR/mysql" "$PAYLOAD_DIR/nginx" "$PAYLOAD_DIR/enterprise-rag" "$PAYLOAD_DIR/elasticsearch" "$PAYLOAD_DIR/kafka" "$PAYLOAD_DIR/jmeter"

touch "$META_DIR/included-paths.txt" "$META_DIR/missing-paths.txt"

record_include() {
  printf '%s\n' "$1" >> "$META_DIR/included-paths.txt"
}

record_missing() {
  printf '%s\n' "$1" >> "$META_DIR/missing-paths.txt"
}

copy_path() {
  local src="$1"
  local dest="$2"
  if [ -e "$src" ]; then
    mkdir -p "$(dirname "$dest")"
    cp -a "$src" "$dest"
    record_include "$src"
  else
    record_missing "$src"
  fi
}

capture_cmd() {
  local out="$1"
  shift
  if "$@" > "$out" 2>&1; then
    record_include "$out"
  else
    record_missing "$out"
  fi
}

PROJECT_DIR=""
for candidate in "$DEFAULT_PROJECT_DIR" /root/EnterpriseRagCommunity /home/ubuntu/EnterpriseRagCommunity; do
  if [ -n "$candidate" ] && [ -d "$candidate" ]; then
    PROJECT_DIR="$candidate"
    break
  fi
done

APP_LOG_FILE=""
for candidate in \
  "${PROJECT_DIR}/logs/EnterpriseRagCommunity.log" \
  /logs/EnterpriseRagCommunity.log \
  /root/logs/EnterpriseRagCommunity.log \
  /home/ubuntu/logs/EnterpriseRagCommunity.log
do
  if [ -f "$candidate" ]; then
    APP_LOG_FILE="$candidate"
    break
  fi
done

if [ -z "$APP_LOG_FILE" ]; then
  APP_LOG_FILE="$(find /root /home /logs -maxdepth 4 -type f -name 'EnterpriseRagCommunity.log*' 2>/dev/null | head -n 1 || true)"
fi

KAFKA_HOME=""
for candidate in /opt/kafka /opt/kafka/kafka_2.13-4.2.0 /opt/kafka/*; do
  if [ -d "$candidate" ] && [ -f "$candidate/config/server.properties" ]; then
    KAFKA_HOME="$candidate"
    break
  fi
done

JMETER_RESULTS_ROOT=""
for candidate in \
  "${PROJECT_DIR}/perf/jmeter/results" \
  /root/EnterpriseRagCommunity/perf/jmeter/results \
  /home/ubuntu/EnterpriseRagCommunity/perf/jmeter/results
do
  if [ -d "$candidate" ]; then
    JMETER_RESULTS_ROOT="$candidate"
    break
  fi
done

printf 'bundle_name=%s\nsince=%s\nproject_dir=%s\napp_log_file=%s\nkafka_home=%s\njmeter_results_root=%s\n' \
  "$BUNDLE_NAME" "$SINCE_VALUE" "$PROJECT_DIR" "$APP_LOG_FILE" "$KAFKA_HOME" "$JMETER_RESULTS_ROOT" > "$META_DIR/discovery.env"

capture_cmd "$META_DIR/hostname.txt" hostname
capture_cmd "$META_DIR/uname.txt" uname -a
capture_cmd "$META_DIR/date.txt" date -Is
capture_cmd "$META_DIR/df.txt" df -h
capture_cmd "$META_DIR/free.txt" free -h
capture_cmd "$META_DIR/ss-listen.txt" ss -lntp
capture_cmd "$META_DIR/ps-java.txt" ps -ef
capture_cmd "$META_DIR/docker-ps.txt" docker ps -a

copy_path /var/log/mysql "$PAYLOAD_DIR/mysql/var-log-mysql"
capture_cmd "$PAYLOAD_DIR/mysql/journalctl-mysql.log" journalctl -u mysql --since "$SINCE_VALUE" --no-pager
capture_cmd "$PAYLOAD_DIR/mysql/journalctl-mysqld.log" journalctl -u mysqld --since "$SINCE_VALUE" --no-pager

copy_path /var/log/nginx "$PAYLOAD_DIR/nginx/var-log-nginx"
copy_path /etc/nginx/nginx.conf "$PAYLOAD_DIR/nginx/nginx.conf"
copy_path /etc/nginx/conf.d "$PAYLOAD_DIR/nginx/conf.d"
copy_path /etc/nginx/sites-available/enterprise-rag "$PAYLOAD_DIR/nginx/sites-available-enterprise-rag"
copy_path /etc/nginx/sites-enabled/enterprise-rag "$PAYLOAD_DIR/nginx/sites-enabled-enterprise-rag"
capture_cmd "$PAYLOAD_DIR/nginx/journalctl-nginx.log" journalctl -u nginx --since "$SINCE_VALUE" --no-pager
capture_cmd "$PAYLOAD_DIR/nginx/nginx-t.txt" nginx -t

if [ -n "$PROJECT_DIR" ]; then
  copy_path "$PROJECT_DIR/logs" "$PAYLOAD_DIR/enterprise-rag/project-logs"
  copy_path "$PROJECT_DIR/bin/main/application.properties" "$PAYLOAD_DIR/enterprise-rag/application.properties"
  copy_path "$PROJECT_DIR/src/main/resources/application.properties" "$PAYLOAD_DIR/enterprise-rag/src-application.properties"
  copy_path "$PROJECT_DIR/src/main/resources/application-perf.properties" "$PAYLOAD_DIR/enterprise-rag/src-application-perf.properties"
  copy_path "$PROJECT_DIR/src/main/resources/logback-spring.xml" "$PAYLOAD_DIR/enterprise-rag/logback-spring.xml"
fi

if [ -n "$APP_LOG_FILE" ]; then
  copy_path "$APP_LOG_FILE" "$PAYLOAD_DIR/enterprise-rag/$(basename "$APP_LOG_FILE")"
fi

copy_path /etc/systemd/system/enterprise-rag.service "$PAYLOAD_DIR/enterprise-rag/enterprise-rag.service"
copy_path /etc/default/enterprise-rag "$PAYLOAD_DIR/enterprise-rag/etc-default-enterprise-rag"
capture_cmd "$PAYLOAD_DIR/enterprise-rag/journalctl-enterprise-rag.log" journalctl -u enterprise-rag --since "$SINCE_VALUE" --no-pager
capture_cmd "$PAYLOAD_DIR/enterprise-rag/systemctl-status.txt" systemctl status enterprise-rag --no-pager

capture_cmd "$PAYLOAD_DIR/elasticsearch/docker-logs-elasticsearch.log" docker logs --timestamps elasticsearch
capture_cmd "$PAYLOAD_DIR/elasticsearch/docker-inspect-elasticsearch.json" docker inspect elasticsearch
capture_cmd "$PAYLOAD_DIR/elasticsearch/docker-stats-elasticsearch.txt" docker stats --no-stream elasticsearch
if docker ps -a --format '{{.Names}}' | grep -qx elasticsearch; then
  mkdir -p "$PAYLOAD_DIR/elasticsearch/container-logs"
  if docker cp elasticsearch:/usr/share/elasticsearch/logs/. "$PAYLOAD_DIR/elasticsearch/container-logs" >/dev/null 2>&1; then
    record_include "elasticsearch:/usr/share/elasticsearch/logs"
  else
    record_missing "elasticsearch:/usr/share/elasticsearch/logs"
  fi
fi

if [ -n "$KAFKA_HOME" ]; then
  copy_path "$KAFKA_HOME/config/server.properties" "$PAYLOAD_DIR/kafka/server.properties"
  copy_path "$KAFKA_HOME/kafka.log" "$PAYLOAD_DIR/kafka/kafka.log"
  copy_path "$KAFKA_HOME/kafka-error.log" "$PAYLOAD_DIR/kafka/kafka-error.log"
  copy_path "$KAFKA_HOME/logs" "$PAYLOAD_DIR/kafka/kafka-home-logs"
  copy_path /var/log/kafka.log "$PAYLOAD_DIR/kafka/var-log-kafka.log"
  copy_path /var/log/kafka-error.log "$PAYLOAD_DIR/kafka/var-log-kafka-error.log"
  copy_path /var/log/kafka-startup.log "$PAYLOAD_DIR/kafka/var-log-kafka-startup.log"
  capture_cmd "$PAYLOAD_DIR/kafka/kafka-process.txt" sh -lc "ps -ef | grep kafka | grep -v grep"
  capture_cmd "$PAYLOAD_DIR/kafka/kafka-ports.txt" sh -lc "ss -lntp | grep -E '9092|9093' || true"
fi

capture_cmd "$PAYLOAD_DIR/jmeter/jmeter-process.txt" sh -lc "ps -ef | grep ApacheJMeter.jar | grep -v grep || true"
if [ -n "$JMETER_RESULTS_ROOT" ]; then
  mkdir -p "$PAYLOAD_DIR/jmeter/results"
  found_recent=0
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    cp -a "$dir" "$PAYLOAD_DIR/jmeter/results/"
    record_include "$dir"
    found_recent=1
  done < <(find "$JMETER_RESULTS_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime -2 | sort)
  if [ "$found_recent" -eq 0 ]; then
    latest_dir="$(find "$JMETER_RESULTS_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1 || true)"
    if [ -n "$latest_dir" ]; then
      cp -a "$latest_dir" "$PAYLOAD_DIR/jmeter/results/"
      record_include "$latest_dir"
    else
      record_missing "$JMETER_RESULTS_ROOT"
    fi
  fi
fi

find "$PAYLOAD_DIR" -type f -printf '%P\t%s\t%TY-%Tm-%Td %TH:%TM:%TS\n' | sort > "$META_DIR/file-list.tsv"

rm -f "$ARCHIVE_PATH" "$HASH_PATH"
tar -C "$WORK_DIR" -czf "$ARCHIVE_PATH" payload
sha256sum "$ARCHIVE_PATH" > "$HASH_PATH"

printf 'archive_path=%s\nhash_path=%s\n' "$ARCHIVE_PATH" "$HASH_PATH"
'@
Write-Utf8NoBom -Path $sutCollectorPath -Content $sutCollector

$remoteCollectorPath = New-RemoteScript -FilePath $sutCollectorPath -Content $sutCollector

$sutResult = Invoke-RemoteCommand -RemoteHost $SutHost -Command "chmod +x $remoteCollectorPath ; $remoteCollectorPath $BundleName '$Since' $SutProjectDir"

$sutArchivePath = (($sutResult | Where-Object { $_ -match '^archive_path=' }) -replace '^archive_path=', '').Trim()
$sutHashPath = (($sutResult | Where-Object { $_ -match '^hash_path=' }) -replace '^hash_path=', '').Trim()

if ([string]::IsNullOrWhiteSpace($sutArchivePath) -or [string]::IsNullOrWhiteSpace($sutHashPath)) {
  throw "SUT collector did not return archive/hash paths. Output: $($sutResult -join [Environment]::NewLine)"
}

$tempControllerKey = Join-Path $localTempDir "controller-pull-key.pem"
Copy-Item -LiteralPath $KeyPath -Destination $tempControllerKey -Force
$remoteControllerKey = "/tmp/$BundleName-controller-pull-key.pem"
Copy-ToRemote -LocalPath $tempControllerKey -RemoteTarget "$ControllerHost`:$remoteControllerKey"

$controllerScriptPath = Join-Path $localTempDir "controller-pull.sh"
$controllerScript = @'
#!/usr/bin/env bash
set -euo pipefail

BUNDLE_NAME="$1"
CONTROLLER_SAVE_ROOT="$2"
REMOTE_KEY_PATH="$3"
SUT_INTERNAL_IP="$4"
SUT_ARCHIVE_PATH="$5"
SUT_HASH_PATH="$6"
CONTROLLER_PROJECT_DIR="$7"
CONTROLLER_JMETER_RESULTS_ROOT="$8"

BUNDLE_DIR="${CONTROLLER_SAVE_ROOT}/${BUNDLE_NAME}"
SUT_DIR="${BUNDLE_DIR}/sut"
CONTROLLER_DIR="${BUNDLE_DIR}/controller"

mkdir -p "$SUT_DIR" "$CONTROLLER_DIR/jmeter-results" "$CONTROLLER_DIR/jmeter-config"
chmod 600 "$REMOTE_KEY_PATH"

scp -i "$REMOTE_KEY_PATH" -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new root@${SUT_INTERNAL_IP}:"${SUT_ARCHIVE_PATH}" "$SUT_DIR/"
scp -i "$REMOTE_KEY_PATH" -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new root@${SUT_INTERNAL_IP}:"${SUT_HASH_PATH}" "$SUT_DIR/"

ARCHIVE_FILE="${SUT_DIR}/$(basename "$SUT_ARCHIVE_PATH")"
HASH_FILE="${SUT_DIR}/$(basename "$SUT_HASH_PATH")"

EXPECTED_HASH="$(awk '{print $1}' "$HASH_FILE")"
ACTUAL_HASH="$(sha256sum "$ARCHIVE_FILE" | awk '{print $1}')"
if [ "$EXPECTED_HASH" != "$ACTUAL_HASH" ]; then
  printf 'sha256_mismatch expected=%s actual=%s\n' "$EXPECTED_HASH" "$ACTUAL_HASH" | tee "$SUT_DIR/transfer-verify.txt"
  exit 1
fi
printf 'sha256_ok %s  %s\n' "$ACTUAL_HASH" "$(basename "$ARCHIVE_FILE")" | tee "$SUT_DIR/transfer-verify.txt"

tar -xzf "$ARCHIVE_FILE" -C "$SUT_DIR"

if [ -d "$CONTROLLER_JMETER_RESULTS_ROOT" ]; then
  found_recent=0
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    cp -a "$dir" "$CONTROLLER_DIR/jmeter-results/"
    found_recent=1
  done < <(find "$CONTROLLER_JMETER_RESULTS_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime -2 | sort)

  if [ "$found_recent" -eq 0 ]; then
    latest_dir="$(find "$CONTROLLER_JMETER_RESULTS_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1 || true)"
    if [ -n "$latest_dir" ]; then
      cp -a "$latest_dir" "$CONTROLLER_DIR/jmeter-results/"
    fi
  fi
fi

for candidate in \
  /root/jmeter/bin/jmeter.properties \
  /root/jmeter/bin/setenv.sh \
  /root/jmeter-server.log \
  /root/jmeter.log
do
  if [ -e "$candidate" ]; then
    cp -a "$candidate" "$CONTROLLER_DIR/jmeter-config/"
  fi
done

find "$BUNDLE_DIR" -type f -printf '%P\t%s\t%TY-%Tm-%Td %TH:%TM:%TS\n' | sort > "$BUNDLE_DIR/manifest.tsv"
sha256sum "$ARCHIVE_FILE" > "$SUT_DIR/controller-recomputed.sha256"
rm -f "$REMOTE_KEY_PATH"

printf 'bundle_dir=%s\narchive_file=%s\nhash_file=%s\n' "$BUNDLE_DIR" "$ARCHIVE_FILE" "$HASH_FILE"
'@
Write-Utf8NoBom -Path $controllerScriptPath -Content $controllerScript

Copy-ToRemote -LocalPath $controllerScriptPath -RemoteTarget "$ControllerHost`:/tmp/$BundleName-controller-pull.sh"

$controllerResult = Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "chmod +x /tmp/$BundleName-controller-pull.sh ; /tmp/$BundleName-controller-pull.sh $BundleName $ControllerSaveRoot $remoteControllerKey $SutInternalIp $sutArchivePath $sutHashPath $ControllerProjectDir $ControllerJMeterResultsRoot"

Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "rm -f /tmp/$BundleName-controller-pull.sh"
Invoke-RemoteCommand -RemoteHost $SutHost -Command "rm -f $remoteCollectorPath"

$bundleDir = (($controllerResult | Where-Object { $_ -match '^bundle_dir=' }) -replace '^bundle_dir=', '').Trim()
$archiveFile = (($controllerResult | Where-Object { $_ -match '^archive_file=' }) -replace '^archive_file=', '').Trim()
$hashFile = (($controllerResult | Where-Object { $_ -match '^hash_file=' }) -replace '^hash_file=', '').Trim()

if ([string]::IsNullOrWhiteSpace($bundleDir)) {
  throw "Controller collector did not return bundle_dir. Output: $($controllerResult -join [Environment]::NewLine)"
}

Write-Host "Bundle saved on controller: $bundleDir"
Write-Host "Transferred archive: $archiveFile"
Write-Host "Hash file: $hashFile"