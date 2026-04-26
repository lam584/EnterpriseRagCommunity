param(
  [string]$KeyPath = "E:\DownLoads\test123.pem",
  [string]$ControllerHost = "root@8.166.143.72",
  [string]$BundlesRoot = "/root/loadtest-log-bundles",
  [string]$MainBundle = "loadtest-logs-20260418_210304",
  [string]$RefillBundle1 = "log-refill-missing-20260418_214749",
  [string]$RefillBundle2 = "log-refill-missing-20260418_215847",
  [string]$PreviousMergedDir = "loadtest-logs-merged-20260419_023555",
  [string]$FinalName = ""
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

if ([string]::IsNullOrWhiteSpace($FinalName)) {
  $FinalName = "loadtest-logs-final-$(Get-Date -Format 'yyyyMMdd_HHmmss')"
}

$tempDir = Join-Path $env:TEMP ("consolidate-loadtest-" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
$remoteScriptPath = Join-Path $tempDir "consolidate-loadtest-bundles.sh"

$remoteScript = @'
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:?root dir required}"
MAIN_BUNDLE="${2:?main bundle required}"
REFILL1_BUNDLE="${3:?refill1 bundle required}"
REFILL2_BUNDLE="${4:?refill2 bundle required}"
PREV_MERGED="${5:-}"
FINAL_NAME="${6:?final name required}"

MAIN_DIR="${ROOT_DIR}/${MAIN_BUNDLE}"
REFILL1_DIR="${ROOT_DIR}/${REFILL1_BUNDLE}"
REFILL2_DIR="${ROOT_DIR}/${REFILL2_BUNDLE}"
FINAL_DIR="${ROOT_DIR}/${FINAL_NAME}"
READING_DIR="${FINAL_DIR}/reading"
AUDIT_DIR="${FINAL_DIR}/audit"

if [ -e "$FINAL_DIR" ]; then
  echo "final_dir_exists=${FINAL_DIR}"
  exit 1
fi

mkdir -p "$READING_DIR" "$AUDIT_DIR/source-archives/main" "$AUDIT_DIR/source-archives/refill1" "$AUDIT_DIR/source-archives/refill2"
: > "$AUDIT_DIR/move-log.tsv"
: > "$AUDIT_DIR/missing.tsv"

record_move() {
  local type="$1"
  local dest="$2"
  local src="$3"
  printf '%s\t%s\t%s\n' "$type" "$dest" "$src" >> "$AUDIT_DIR/move-log.tsv"
}

record_missing() {
  local dest="$1"
  local src="$2"
  printf '%s\t%s\n' "$dest" "$src" >> "$AUDIT_DIR/missing.tsv"
}

move_file() {
  local src="$1"
  local dest="$2"
  local tag="${3:-}"

  if [ ! -f "$src" ]; then
    record_missing "$dest" "$src"
    return 0
  fi

  mkdir -p "$(dirname "$dest")"

  if [ -e "$dest" ]; then
    if cmp -s "$src" "$dest"; then
      rm -f "$src"
      record_move "dedupe" "$dest" "$src"
      return 0
    fi

    local alt_dest="$dest"
    if [ -n "$tag" ]; then
      alt_dest="${dest}.from-${tag}"
    else
      alt_dest="${dest}.from-conflict"
    fi
    mv "$src" "$alt_dest"
    record_move "file-conflict" "$alt_dest" "$src"
    return 0
  fi

  mv "$src" "$dest"
  record_move "file" "$dest" "$src"
}

move_dir() {
  local src="$1"
  local dest="$2"
  local tag="${3:-}"

  if [ ! -d "$src" ]; then
    record_missing "$dest" "$src"
    return 0
  fi

  mkdir -p "$(dirname "$dest")"
  if [ ! -e "$dest" ]; then
    mv "$src" "$dest"
    record_move "dir" "$dest" "$src"
    return 0
  fi

  while IFS= read -r file; do
    rel="${file#${src}/}"
    move_file "$file" "$dest/$rel" "$tag"
  done < <(find "$src" -type f | sort)

  find "$src" -depth -type d -empty -delete >/dev/null 2>&1 || true
  record_move "dir-merged" "$dest" "$src"
}

cleanup_dir() {
  local dir="$1"
  if [ -d "$dir" ]; then
    find "$dir" -depth -type d -empty -delete >/dev/null 2>&1 || true
    if [ -d "$dir" ] && [ -z "$(find "$dir" -mindepth 1 -print -quit)" ]; then
      rmdir "$dir" >/dev/null 2>&1 || true
    fi
  fi
}

move_file "$MAIN_DIR/manifest.tsv" "$AUDIT_DIR/source-archives/main/manifest.tsv" main
move_file "$MAIN_DIR/sut/transfer-verify.txt" "$AUDIT_DIR/source-archives/main/transfer-verify.txt" main
move_file "$MAIN_DIR/sut/controller-recomputed.sha256" "$AUDIT_DIR/source-archives/main/controller-recomputed.sha256" main
move_file "$MAIN_DIR/sut/loadtest-logs-20260418_210304-sut.tar.gz" "$AUDIT_DIR/source-archives/main/loadtest-sut.tar.gz" main
move_file "$MAIN_DIR/sut/loadtest-logs-20260418_210304-sut.tar.gz.sha256" "$AUDIT_DIR/source-archives/main/loadtest-sut.tar.gz.sha256" main

move_file "$REFILL1_DIR/manifest.tsv" "$AUDIT_DIR/source-archives/refill1/manifest.tsv" refill1
move_file "$REFILL1_DIR/sut-refill/transfer-verify.txt" "$AUDIT_DIR/source-archives/refill1/transfer-verify.txt" refill1
move_file "$REFILL1_DIR/sut-refill/log-refill-missing-only.tar.gz" "$AUDIT_DIR/source-archives/refill1/log-refill-missing-only.tar.gz" refill1
move_file "$REFILL1_DIR/sut-refill/log-refill-missing-only.tar.gz.sha256" "$AUDIT_DIR/source-archives/refill1/log-refill-missing-only.tar.gz.sha256" refill1

move_file "$REFILL2_DIR/manifest.tsv" "$AUDIT_DIR/source-archives/refill2/manifest.tsv" refill2
move_file "$REFILL2_DIR/sut-refill/transfer-verify.txt" "$AUDIT_DIR/source-archives/refill2/transfer-verify.txt" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill-missing-only.tar.gz" "$AUDIT_DIR/source-archives/refill2/log-refill-missing-only.tar.gz" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill-missing-only.tar.gz.sha256" "$AUDIT_DIR/source-archives/refill2/log-refill-missing-only.tar.gz.sha256" refill2

move_dir "$MAIN_DIR/sut/payload/enterprise-rag" "$READING_DIR/enterprise-rag" main
move_dir "$MAIN_DIR/sut/payload/elasticsearch" "$READING_DIR/elasticsearch" main
move_dir "$MAIN_DIR/sut/payload/metadata" "$READING_DIR/metadata" main
move_dir "$MAIN_DIR/controller/jmeter-config" "$READING_DIR/jmeter/config" main
move_dir "$MAIN_DIR/controller/jmeter-results" "$READING_DIR/jmeter/results" main

move_file "$MAIN_DIR/sut/payload/jmeter/jmeter-process.txt" "$READING_DIR/jmeter/jmeter-process.txt" main

move_file "$MAIN_DIR/sut/payload/nginx/var-log-nginx/access.log" "$READING_DIR/nginx/access.log" main
move_file "$MAIN_DIR/sut/payload/nginx/var-log-nginx/error.log" "$READING_DIR/nginx/error.log" main
move_file "$MAIN_DIR/sut/payload/nginx/journalctl-nginx.log" "$READING_DIR/nginx/journalctl-nginx.log" main
move_file "$MAIN_DIR/sut/payload/nginx/nginx.conf" "$READING_DIR/nginx/nginx.conf" main
move_file "$REFILL2_DIR/sut-refill/log-refill/nginx/conf.d/erc.conf" "$READING_DIR/nginx/erc.conf" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/nginx/conf.d/erc.conf.bak_" "$READING_DIR/nginx/erc.conf.bak_" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/nginx/nginx-t.txt" "$READING_DIR/nginx/nginx-t.txt" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/nginx/systemctl-status-nginx.txt" "$READING_DIR/nginx/systemctl-status-nginx.txt" refill2

move_dir "$REFILL2_DIR/sut-refill/log-refill/kafka" "$READING_DIR/kafka" refill2

move_file "$MAIN_DIR/sut/payload/mysql/journalctl-mysql.log" "$READING_DIR/mysql/journalctl-mysql.log" main
move_file "$MAIN_DIR/sut/payload/mysql/journalctl-mysqld.log" "$READING_DIR/mysql/journalctl-mysqld.log" main
move_file "$MAIN_DIR/sut/payload/mysql/var-log-mysql/mysqld.log" "$READING_DIR/mysql/mysqld.log" main
move_file "$REFILL2_DIR/sut-refill/log-refill/mysql/journalctl-mysql-full.log" "$READING_DIR/mysql/journalctl-mysql-full.log" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/mysql/journalctl-mysqld-full.log" "$READING_DIR/mysql/journalctl-mysqld-full.log" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/mysql/systemctl-status-mysql.txt" "$READING_DIR/mysql/systemctl-status-mysql.txt" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/mysql/systemctl-status-mysqld.txt" "$READING_DIR/mysql/systemctl-status-mysqld.txt" refill2
move_file "$REFILL2_DIR/sut-refill/log-refill/mysql/mysql/mysqld.log" "$READING_DIR/mysql/mysqld-full.log" refill2

cat > "$FINAL_DIR/README.md" <<EOF
# consolidated loadtest logs

- final reading root: ${READING_DIR}
- source main bundle: ${MAIN_BUNDLE}
- source refill bundle 1: ${REFILL1_BUNDLE}
- source refill bundle 2: ${REFILL2_BUNDLE}
- strategy: move files into one reading-oriented directory, keep original archives and manifests under audit/source-archives
EOF

find "$FINAL_DIR" -type f -printf '%P\t%s\t%TY-%Tm-%Td %TH:%TM:%TS\n' | sort > "$FINAL_DIR/manifest.tsv"

cleanup_dir "$MAIN_DIR"
cleanup_dir "$REFILL1_DIR"
cleanup_dir "$REFILL2_DIR"

if [ -n "$PREV_MERGED" ] && [ -d "${ROOT_DIR}/${PREV_MERGED}" ]; then
  rm -rf "${ROOT_DIR}/${PREV_MERGED}"
fi

printf 'final_dir=%s\n' "$FINAL_DIR"
'@

Write-Utf8LfNoBom -Path $remoteScriptPath -Content $remoteScript

& scp -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $remoteScriptPath "$ControllerHost`:/tmp/consolidate-loadtest-bundles.sh"
if ($LASTEXITCODE -ne 0) {
  throw "Failed to upload consolidate script."
}

$result = & ssh -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes -o StrictHostKeyChecking=accept-new $ControllerHost "chmod +x /tmp/consolidate-loadtest-bundles.sh && /tmp/consolidate-loadtest-bundles.sh $BundlesRoot $MainBundle $RefillBundle1 $RefillBundle2 $PreviousMergedDir $FinalName && rm -f /tmp/consolidate-loadtest-bundles.sh"
if ($LASTEXITCODE -ne 0) {
  throw "Failed to consolidate bundles on controller."
}

$finalDir = (($result | Where-Object { $_ -match '^final_dir=' }) -replace '^final_dir=', '').Trim()
if ([string]::IsNullOrWhiteSpace($finalDir)) {
  throw "Missing final_dir in controller output. Output: $($result -join [Environment]::NewLine)"
}

Write-Host "Consolidated bundle saved on controller: $finalDir"