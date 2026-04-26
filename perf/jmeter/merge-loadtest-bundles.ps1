param(
  [string]$KeyPath = "E:\DownLoads\test123.pem",
  [string]$ControllerHost = "root@8.166.143.72",
  [string]$BundlesRoot = "/root/loadtest-log-bundles",
  [string]$MainBundle = "loadtest-logs-20260418_210304",
  [string]$RefillBundle1 = "log-refill-missing-20260418_214749",
  [string]$RefillBundle2 = "log-refill-missing-20260418_215847",
  [string]$MergedName = ""
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

if ([string]::IsNullOrWhiteSpace($MergedName)) {
  $MergedName = "loadtest-logs-merged-$(Get-Date -Format 'yyyyMMdd_HHmmss')"
}

$tempDir = Join-Path $env:TEMP ("merge-loadtest-" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
$remoteScriptPath = Join-Path $tempDir "merge-loadtest-bundles.sh"

$remoteScript = @'
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:?root dir required}"
MAIN_BUNDLE="${2:?main bundle required}"
REFILL1_BUNDLE="${3:?refill1 bundle required}"
REFILL2_BUNDLE="${4:?refill2 bundle required}"
MERGED_NAME="${5:?merged name required}"

MAIN_DIR="${ROOT_DIR}/${MAIN_BUNDLE}"
REFILL1_DIR="${ROOT_DIR}/${REFILL1_BUNDLE}"
REFILL2_DIR="${ROOT_DIR}/${REFILL2_BUNDLE}"
MERGED_DIR="${ROOT_DIR}/${MERGED_NAME}"
READING_DIR="${MERGED_DIR}/reading"
AUDIT_DIR="${MERGED_DIR}/audit"

if [ -e "$MERGED_DIR" ]; then
  echo "merged_dir_exists=${MERGED_DIR}"
  exit 1
fi

mkdir -p "$READING_DIR" "$AUDIT_DIR"
: > "$AUDIT_DIR/merge-map.tsv"
: > "$AUDIT_DIR/missing.tsv"

record_link() {
  local src="$1"
  local dest="$2"
  local inode size
  inode="$(stat -c '%i' "$dest")"
  size="$(stat -c '%s' "$dest")"
  printf '%s\t%s\t%s\t%s\n' "$dest" "$src" "$inode" "$size" >> "$AUDIT_DIR/merge-map.tsv"
}

link_file() {
  local src="$1"
  local rel="$2"
  local dest="${READING_DIR}/${rel}"

  if [ ! -f "$src" ]; then
    printf '%s\t%s\n' "$rel" "$src" >> "$AUDIT_DIR/missing.tsv"
    return 0
  fi

  mkdir -p "$(dirname "$dest")"
  if [ -e "$dest" ]; then
    if cmp -s "$src" "$dest"; then
      record_link "$src" "$dest"
      return 0
    fi

    local alt_dest="${dest}.from-$(basename "$(dirname "$src")")"
    ln "$src" "$alt_dest"
    record_link "$src" "$alt_dest"
    return 0
  fi

  ln "$src" "$dest"
  record_link "$src" "$dest"
}

link_tree() {
  local src_dir="$1"
  local rel_root="$2"
  if [ ! -d "$src_dir" ]; then
    printf '%s\t%s\n' "$rel_root" "$src_dir" >> "$AUDIT_DIR/missing.tsv"
    return 0
  fi

  while IFS= read -r src; do
    local rel_path
    rel_path="${src#${src_dir}/}"
    link_file "$src" "${rel_root}/${rel_path}"
  done < <(find "$src_dir" -type f | sort)
}

printf 'main\t%s\nrefill1\t%s\nrefill2\t%s\n' "$MAIN_DIR" "$REFILL1_DIR" "$REFILL2_DIR" > "$AUDIT_DIR/source-bundles.tsv"

link_file "$MAIN_DIR/manifest.tsv" "audit-source/main-manifest.tsv"
link_file "$MAIN_DIR/sut/transfer-verify.txt" "audit-source/main-transfer-verify.txt"
link_file "$MAIN_DIR/sut/controller-recomputed.sha256" "audit-source/main-controller-recomputed.sha256"
link_file "$REFILL1_DIR/manifest.tsv" "audit-source/refill1-manifest.tsv"
link_file "$REFILL1_DIR/sut-refill/transfer-verify.txt" "audit-source/refill1-transfer-verify.txt"
link_file "$REFILL2_DIR/manifest.tsv" "audit-source/refill2-manifest.tsv"
link_file "$REFILL2_DIR/sut-refill/transfer-verify.txt" "audit-source/refill2-transfer-verify.txt"

link_tree "$MAIN_DIR/sut/payload/enterprise-rag" "enterprise-rag"
link_tree "$MAIN_DIR/sut/payload/elasticsearch" "elasticsearch"

link_file "$MAIN_DIR/sut/payload/nginx/var-log-nginx/access.log" "nginx/access.log"
link_file "$MAIN_DIR/sut/payload/nginx/var-log-nginx/error.log" "nginx/error.log"
link_file "$MAIN_DIR/sut/payload/nginx/journalctl-nginx.log" "nginx/journalctl-nginx.log"
link_file "$MAIN_DIR/sut/payload/nginx/nginx.conf" "nginx/nginx.conf"
link_file "$REFILL2_DIR/sut-refill/log-refill/nginx/conf.d/erc.conf" "nginx/erc.conf"
link_file "$REFILL2_DIR/sut-refill/log-refill/nginx/conf.d/erc.conf.bak_" "nginx/erc.conf.bak_"
link_file "$REFILL2_DIR/sut-refill/log-refill/nginx/nginx-t.txt" "nginx/nginx-t.txt"
link_file "$REFILL2_DIR/sut-refill/log-refill/nginx/systemctl-status-nginx.txt" "nginx/systemctl-status-nginx.txt"

link_tree "$MAIN_DIR/controller/jmeter-config" "jmeter/config"
link_tree "$MAIN_DIR/controller/jmeter-results" "jmeter/results"

link_tree "$REFILL2_DIR/sut-refill/log-refill/kafka" "kafka"

link_file "$MAIN_DIR/sut/payload/mysql/journalctl-mysql.log" "mysql/journalctl-mysql.log"
link_file "$MAIN_DIR/sut/payload/mysql/journalctl-mysqld.log" "mysql/journalctl-mysqld.log"
link_file "$MAIN_DIR/sut/payload/mysql/var-log-mysql/mysqld.log" "mysql/mysqld.log"
link_file "$REFILL2_DIR/sut-refill/log-refill/mysql/journalctl-mysql-full.log" "mysql/journalctl-mysql-full.log"
link_file "$REFILL2_DIR/sut-refill/log-refill/mysql/journalctl-mysqld-full.log" "mysql/journalctl-mysqld-full.log"
link_file "$REFILL2_DIR/sut-refill/log-refill/mysql/systemctl-status-mysql.txt" "mysql/systemctl-status-mysql.txt"
link_file "$REFILL2_DIR/sut-refill/log-refill/mysql/systemctl-status-mysqld.txt" "mysql/systemctl-status-mysqld.txt"

link_tree "$MAIN_DIR/sut/payload/metadata" "metadata"

cat > "$MERGED_DIR/README.md" <<EOF
# loadtest merged bundle

- source main: ${MAIN_DIR}
- source refill1: ${REFILL1_DIR}
- source refill2: ${REFILL2_DIR}
- merge strategy: hard-link files into a single reading-oriented tree
- integrity: merged files share inode with source files on the same filesystem; see audit/merge-map.tsv
EOF

find "$MERGED_DIR" -type f -printf '%P\t%s\t%TY-%Tm-%Td %TH:%TM:%TS\n' | sort > "$MERGED_DIR/manifest.tsv"

printf 'merged_dir=%s\n' "$MERGED_DIR"
'@

Write-Utf8LfNoBom -Path $remoteScriptPath -Content $remoteScript

& scp -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $remoteScriptPath "$ControllerHost`:/tmp/merge-loadtest-bundles.sh"
if ($LASTEXITCODE -ne 0) {
  throw "Failed to upload merge script."
}

$result = & ssh -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $ControllerHost "chmod +x /tmp/merge-loadtest-bundles.sh && /tmp/merge-loadtest-bundles.sh $BundlesRoot $MainBundle $RefillBundle1 $RefillBundle2 $MergedName && rm -f /tmp/merge-loadtest-bundles.sh"
if ($LASTEXITCODE -ne 0) {
  throw "Failed to merge bundles on controller."
}

$mergedDir = (($result | Where-Object { $_ -match '^merged_dir=' }) -replace '^merged_dir=', '').Trim()
if ([string]::IsNullOrWhiteSpace($mergedDir)) {
  throw "Missing merged_dir in controller output. Output: $($result -join [Environment]::NewLine)"
}

Write-Host "Merged bundle saved on controller: $mergedDir"