param(
  [string]$KeyPath = "E:\DownLoads\test123.pem",
  [string]$ControllerHost = "root@8.166.143.72",
  [string]$SutInternalIp = "172.20.170.194",
  [string]$ControllerSaveRoot = "/root/loadtest-log-bundles"
)

$ErrorActionPreference = "Stop"

function Write-Utf8LfNoBom {
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

function Invoke-Scp {
  param(
    [Parameter(Mandatory = $true)]
    [string]$LocalPath,
    [Parameter(Mandatory = $true)]
    [string]$RemoteTarget
  )

  & scp -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $LocalPath $RemoteTarget
  if ($LASTEXITCODE -ne 0) {
    throw "SCP failed: $LocalPath -> $RemoteTarget"
  }
}

function Invoke-Ssh {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RemoteHost,
    [Parameter(Mandatory = $true)]
    [string]$Command
  )

  & ssh -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $RemoteHost $Command
  if ($LASTEXITCODE -ne 0) {
    throw "SSH failed on ${RemoteHost}: $Command"
  }
}

$tempDir = Join-Path $env:TEMP ("log-refill-" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

$sutScriptPath = Join-Path $tempDir "sut-refill.sh"
$controllerScriptPath = Join-Path $tempDir "controller-refill.sh"

$sutScript = @'
#!/usr/bin/env bash
set -euo pipefail

rm -rf /tmp/log-refill
mkdir -p /tmp/log-refill/kafka /tmp/log-refill/mysql /tmp/log-refill/nginx

cp -a /opt/kafka/config/server.properties /tmp/log-refill/kafka/ 2>/dev/null || true
cp -a /opt/kafka/logs /tmp/log-refill/kafka/opt-kafka-logs 2>/dev/null || true
cp -a /var/log/kafka.log /tmp/log-refill/kafka/ 2>/dev/null || true
cp -a /var/log/kafka-error.log /tmp/log-refill/kafka/ 2>/dev/null || true
cp -a /var/log/kafka-startup.log /tmp/log-refill/kafka/ 2>/dev/null || true
ps -ef | grep kafka | grep -v grep > /tmp/log-refill/kafka/kafka-process.txt 2>&1 || true
ss -lntp | grep -E '9092|9093' > /tmp/log-refill/kafka/kafka-ports.txt 2>&1 || true
tail -n 500 /var/log/kafka.log > /tmp/log-refill/kafka/kafka-tail-500.log 2>&1 || true

cp -a /var/log/mysql /tmp/log-refill/mysql/ 2>/dev/null || true
journalctl -u mysql --since '3 days ago' --no-pager > /tmp/log-refill/mysql/journalctl-mysql-full.log 2>&1 || true
journalctl -u mysqld --since '3 days ago' --no-pager > /tmp/log-refill/mysql/journalctl-mysqld-full.log 2>&1 || true
systemctl status mysql --no-pager > /tmp/log-refill/mysql/systemctl-status-mysql.txt 2>&1 || true
systemctl status mysqld --no-pager > /tmp/log-refill/mysql/systemctl-status-mysqld.txt 2>&1 || true

cp -a /etc/nginx/nginx.conf /tmp/log-refill/nginx/ 2>/dev/null || true
cp -a /etc/nginx/conf.d /tmp/log-refill/nginx/ 2>/dev/null || true
cp -a /etc/nginx/sites-available /tmp/log-refill/nginx/ 2>/dev/null || true
cp -a /etc/nginx/sites-enabled /tmp/log-refill/nginx/ 2>/dev/null || true
nginx -t > /tmp/log-refill/nginx/nginx-t.txt 2>&1 || true
systemctl status nginx --no-pager > /tmp/log-refill/nginx/systemctl-status-nginx.txt 2>&1 || true

find /tmp/log-refill -type f -printf '%P	%s	%TY-%Tm-%Td %TH:%TM:%TS\n' | sort > /tmp/log-refill/manifest.tsv
tar -C /tmp -czf /tmp/log-refill-missing-only.tar.gz log-refill
sha256sum /tmp/log-refill-missing-only.tar.gz > /tmp/log-refill-missing-only.tar.gz.sha256
'@

$controllerScript = @'
#!/usr/bin/env bash
set -euo pipefail

REMOTE_KEY=/tmp/log-refill-missing-key.pem
SUT_SCRIPT=/tmp/sut-refill.sh
SUT_INTERNAL_IP=${1:?sut internal ip required}
CONTROLLER_SAVE_ROOT=${2:?controller save root required}
BUNDLE="log-refill-missing-$(date +%Y%m%d_%H%M%S)"
BASE="${CONTROLLER_SAVE_ROOT}/${BUNDLE}"

mkdir -p "${BASE}/sut-refill"
chmod 600 "$REMOTE_KEY"

scp -i "$REMOTE_KEY" -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new "$SUT_SCRIPT" root@"${SUT_INTERNAL_IP}":/tmp/sut-refill.sh
ssh -i "$REMOTE_KEY" -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new root@"${SUT_INTERNAL_IP}" 'chmod +x /tmp/sut-refill.sh && /tmp/sut-refill.sh'
scp -i "$REMOTE_KEY" -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new root@"${SUT_INTERNAL_IP}":/tmp/log-refill-missing-only.tar.gz "${BASE}/sut-refill/"
scp -i "$REMOTE_KEY" -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new root@"${SUT_INTERNAL_IP}":/tmp/log-refill-missing-only.tar.gz.sha256 "${BASE}/sut-refill/"

cd "${BASE}/sut-refill"
EXPECTED="$(awk '{print $1}' log-refill-missing-only.tar.gz.sha256)"
ACTUAL="$(sha256sum log-refill-missing-only.tar.gz | awk '{print $1}')"
if [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "sha256_mismatch expected=${EXPECTED} actual=${ACTUAL}" | tee transfer-verify.txt
  exit 1
fi

echo "sha256_ok ${ACTUAL} log-refill-missing-only.tar.gz" | tee transfer-verify.txt
tar -xzf log-refill-missing-only.tar.gz
find "${BASE}" -type f -printf '%P	%s	%TY-%Tm-%Td %TH:%TM:%TS\n' | sort > "${BASE}/manifest.tsv"

printf 'bundle_dir=%s\n' "$BASE"
'@

Write-Utf8LfNoBom -Path $sutScriptPath -Content $sutScript
Write-Utf8LfNoBom -Path $controllerScriptPath -Content $controllerScript

Invoke-Scp -LocalPath $KeyPath -RemoteTarget "$ControllerHost`:/tmp/log-refill-missing-key.pem"
Invoke-Scp -LocalPath $sutScriptPath -RemoteTarget "$ControllerHost`:/tmp/sut-refill.sh"
Invoke-Scp -LocalPath $controllerScriptPath -RemoteTarget "$ControllerHost`:/tmp/controller-refill.sh"

$result = & ssh -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $ControllerHost "chmod 600 /tmp/log-refill-missing-key.pem && chmod +x /tmp/sut-refill.sh /tmp/controller-refill.sh && /tmp/controller-refill.sh $SutInternalIp $ControllerSaveRoot"
if ($LASTEXITCODE -ne 0) {
  throw "Controller-side refill script failed."
}

$bundleDir = (($result | Where-Object { $_ -match '^bundle_dir=' }) -replace '^bundle_dir=', '').Trim()
if ([string]::IsNullOrWhiteSpace($bundleDir)) {
  throw "Missing bundle_dir in controller output. Output: $($result -join [Environment]::NewLine)"
}

Write-Host "Bundle saved on controller: $bundleDir"