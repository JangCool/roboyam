#!/usr/bin/env bash
# ============================================================================
# 로보얌 DB 서버 설치 — PostgreSQL 18 + pg_cron (Docker 없이, PGDG yum 저장소)
#
# 대상   : Rocky Linux 9 (aarch64 — EC2 t4g), root
# 설치   : postgresql18-server · contrib · pg_cron_18 (선택: timescaledb_18)
# 만드는 것
#   role     roboyam   (LOGIN 만 — SUPERUSER·CREATEDB·CREATEROLE·REPLICATION 아님)
#   database roboyam   (UTF8, 로캘 C.UTF-8 libc — pg_trgm 이 한글을 색인한다)
#   확장     pg_cron · pg_trgm · btree_gist · pg_stat_statements (superuser 로 미리 만든다)
#            + GRANT USAGE ON SCHEMA cron TO roboyam (앱이 DB 배치를 등록·변경한다)
#   설정     <PGDATA>/conf.d/roboyam.conf (메모리 자동 계산·pg_cron·접속 수)
#   접속     listen_addresses = localhost,<사설 IP>
#            pg_hba = --allow 대역에서 roboyam DB·계정만 scram-sha-256 (전체 허용 금지)
#   백업     /etc/cron.d/roboyam-db-backup → roboyam-db-backup.sh (pg_dump -Fc, 보관 개수)
#
# 사용법
#   ./roboyam-db-install.sh --allow 10.0.1.0/24 --dry-run
#   ./roboyam-db-install.sh --allow 10.0.1.10/32 --allow 10.0.1.11/32
#   ROBOYAM_DB_PASSWORD=... ./roboyam-db-install.sh --allow 10.0.1.0/24     (무인 설치)
#
# 옵션
#   -a, --allow CIDR        앱 서버 대역(IPv4/IPv6, 여러 번 가능 — 필수). 0.0.0.0/0 · ::/0 은 받지 않는다
#       --listen IP         DB 가 받을 사설 IP (기본: 기본 경로의 원본 주소를 자동으로 찾는다)
#   -d, --db NAME           DB 이름 (기본 roboyam — pg_cron 의 cron.database_name 이 된다)
#   -u, --user NAME         앱 계정 (기본 roboyam)
#   -p, --pg-version N      PostgreSQL 메이저 (기본 18)
#       --data-dir DIR      데이터 폴더 (기본 /var/lib/pgsql/<N>/data). 별도 EBS 를 쓸 때 그 마운트 아래로
#       --max-connections N 최대 접속 수 (기본 200 = 앱 2대 × 20 + 관리자 5 + 여유)
#       --memory-mb MB      메모리 계산 기준을 직접 준다 (기본: 장비 메모리)
#       --timescale         TimescaleDB(PGDG timescaledb_18)도 설치·preload 한다 — 아래 「TimescaleDB」 참고
#       --timezone TZ       DB·pg_cron 시간대 (기본 Asia/Seoul — 바꾸면 등록된 cron 식이 어긋난다)
#       --set-os-timezone   OS 시간대도 --timezone 으로 바꾼다 (기본: 건드리지 않음)
#       --backup-dir DIR    백업 폴더 (기본 /var/backups/roboyam)
#       --backup-keep N     서버에 남길 백업 수 (기본 7)
#       --backup-cron "M H" 백업 시각(분 시, 기본 "30 3" = 매일 03:30)
#       --backup-hook FILE  백업이 끝나면 덤프 파일 경로를 인자로 부를 스크립트(R2 업로드 등 — backup-hook.example.sh)
#       --no-backup         백업 cron 을 만들지 않는다
#       --dry-run           설치 계획·계산값만 보여 주고 끝낸다 (아무것도 바꾸지 않는다)
#   -h, --help              도움말
#
# 메모리 (자동 계산 — 2GB·4GB 모두)
#   shared_buffers ≈ 25% · effective_cache_size ≈ 75% · maintenance_work_mem = 메모리/16 (64MB~1GB)
#   work_mem = (메모리 − shared_buffers) / (max_connections × 3), 최소 4MB
#   예) 2GB: 512MB / 1536MB / 128MB / 4MB    4GB: 1024MB / 3072MB / 256MB / 5MB
#
# TimescaleDB
#   aarch64 는 PGDG 저장소의 timescaledb_18 로 설치한다(같은 PGDG 저장소라 PostgreSQL 과 버전이 맞는다).
#   Timescale 자체 저장소(packagecloud)는 el9 aarch64 가 빈 저장소다(2026-11-09 확인 — x86_64 만 있다).
#   설치 뒤 검증에서 판을 알려 준다: timescaledb.license = timescale(압축·연속 집계 정책 등 TSL 포함) / apache(기본 기능만).
#   지금 앱은 TimescaleDB 를 쓰지 않는다(방문 분석을 옮길 때를 위한 선택 — 기본은 설치하지 않는다).
#
# 비밀번호
#   앱 계정 비밀번호는 프롬프트로 두 번 받는다(무인 설치는 환경 변수 ROBOYAM_DB_PASSWORD). 어디에도 저장하지 않는다.
#   같은 비밀번호를 앱 서버 설치 때 다시 입력한다(그쪽에서 ENC(...) 로 암호화된다).
#   postgres(superuser)는 비밀번호를 두지 않는다 — 이 서버 안에서 peer 인증(sudo -u postgres psql)으로만 쓴다.
#
# 종료 코드: 0 완료 · 1 실패 · 2 사용법 오류 · 3 설치는 끝났으나 검증 실패
# ============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
. "${SCRIPT_DIR}/../lib/common.sh"
trap on_error ERR

# ----------------------------------------------------------------------------
# 기본값
# ----------------------------------------------------------------------------
PG_MAJOR=18
DB_NAME=roboyam
DB_USER=roboyam
DB_PASSWORD="${ROBOYAM_DB_PASSWORD:-}"
ALLOW=()
LISTEN_IP=""
DATA_DIR=""
MAX_CONNECTIONS=200
MEMORY_MB=0
WITH_TIMESCALE=0
PG_TIMEZONE="Asia/Seoul"
SET_OS_TIMEZONE=0
BACKUP_DIR=/var/backups/roboyam
BACKUP_KEEP=7
BACKUP_CRON="30 3"
BACKUP_HOOK=""
WITH_BACKUP=1
DRY_RUN=0

# pg_cron 은 백그라운드 워커로 돌린다(접속을 새로 열지 않아 pg_hba·비밀번호가 필요 없다). 동시 실행 상한만큼 워커 슬롯을 더 잡는다
CRON_MAX_RUNNING_JOBS=4
TS_BACKGROUND_WORKERS=4

BIN_DIR=/opt/roboyam/db/bin
BACKUP_CONF=/etc/roboyam/db-backup.conf
LOG_FILE="/var/log/roboyam-db-install-${STAMP}.log"

usage() { usage_from_header "$0"; }
usage_error() { printf '[사용법 오류] %s\n\n' "$*" >&2; usage >&2; exit 2; }

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            -a|--allow)        ALLOW+=("${2:?--allow 값이 필요합니다}"); shift 2 ;;
            --listen)          LISTEN_IP="${2:?--listen 값이 필요합니다}"; shift 2 ;;
            -d|--db)           DB_NAME="${2:?--db 값이 필요합니다}"; shift 2 ;;
            -u|--user)         DB_USER="${2:?--user 값이 필요합니다}"; shift 2 ;;
            -p|--pg-version)   PG_MAJOR="${2:?--pg-version 값이 필요합니다}"; shift 2 ;;
            --data-dir)        DATA_DIR="${2:?--data-dir 값이 필요합니다}"; shift 2 ;;
            --max-connections) MAX_CONNECTIONS="${2:?--max-connections 값이 필요합니다}"; shift 2 ;;
            --memory-mb)       MEMORY_MB="${2:?--memory-mb 값이 필요합니다}"; shift 2 ;;
            --timescale)       WITH_TIMESCALE=1; shift ;;
            --timezone)        PG_TIMEZONE="${2:?--timezone 값이 필요합니다}"; shift 2 ;;
            --set-os-timezone) SET_OS_TIMEZONE=1; shift ;;
            --backup-dir)      BACKUP_DIR="${2:?--backup-dir 값이 필요합니다}"; shift 2 ;;
            --backup-keep)     BACKUP_KEEP="${2:?--backup-keep 값이 필요합니다}"; shift 2 ;;
            --backup-cron)     BACKUP_CRON="${2:?--backup-cron 값이 필요합니다}"; shift 2 ;;
            --backup-hook)     BACKUP_HOOK="${2:?--backup-hook 값이 필요합니다}"; shift 2 ;;
            --no-backup)       WITH_BACKUP=0; shift ;;
            --dry-run)         DRY_RUN=1; shift ;;
            -h|--help)         usage; exit 0 ;;
            *)                 usage_error "알 수 없는 옵션: $1" ;;
        esac
    done

    [[ "$PG_MAJOR" =~ ^[0-9]+$ ]] || usage_error "--pg-version 은 숫자여야 합니다: ${PG_MAJOR}"
    [[ "$DB_NAME" =~ ^[a-z_][a-z0-9_]*$ ]] || usage_error "--db 이름이 올바르지 않습니다(소문자·숫자·_): ${DB_NAME}"
    [[ "$DB_USER" =~ ^[a-z_][a-z0-9_]*$ ]] || usage_error "--user 이름이 올바르지 않습니다(소문자·숫자·_): ${DB_USER}"
    [ "$DB_USER" != "postgres" ] || usage_error "--user 로 postgres 를 쓸 수 없습니다(앱 계정은 superuser 가 아니어야 한다)."
    [[ "$MAX_CONNECTIONS" =~ ^[0-9]+$ ]] && [ "$MAX_CONNECTIONS" -ge 50 ] || usage_error "--max-connections 는 50 이상 숫자여야 합니다."
    [[ "$MEMORY_MB" =~ ^[0-9]+$ ]] || usage_error "--memory-mb 는 숫자(MB)여야 합니다."
    [[ "$BACKUP_KEEP" =~ ^[0-9]+$ ]] && [ "$BACKUP_KEEP" -ge 1 ] || usage_error "--backup-keep 는 1 이상 숫자여야 합니다."
    [[ "$BACKUP_CRON" =~ ^[0-9]{1,2}\ [0-9]{1,2}$ ]] || usage_error "--backup-cron 은 \"분 시\" 형식이어야 합니다(예: \"30 3\")."
    if [ -n "$BACKUP_HOOK" ]; then
        [ -f "$BACKUP_HOOK" ] || usage_error "--backup-hook 파일이 없습니다: ${BACKUP_HOOK}"
        BACKUP_HOOK="$(cd "$(dirname "$BACKUP_HOOK")" && pwd)/$(basename "$BACKUP_HOOK")"
    fi

    [ "${#ALLOW[@]}" -gt 0 ] || usage_error "--allow 로 앱 서버 대역을 하나 이상 주어야 합니다(전체 허용은 하지 않는다)."
    local c
    for c in "${ALLOW[@]}"; do
        case "$c" in
            0.0.0.0/0|::/0) usage_error "전체 허용(${c})은 받지 않습니다. 앱 서버의 사설 IP·서브넷을 주세요." ;;
        esac
        is_cidr4 "$c" || is_cidr6 "$c" || usage_error "--allow 형식이 올바르지 않습니다(CIDR, 예 10.0.1.0/24): ${c}"
    done
}

resolve_paths() {
    SERVICE_NAME="postgresql-${PG_MAJOR}"
    PG_BIN="/usr/pgsql-${PG_MAJOR}/bin"
    PSQL="${PG_BIN}/psql"
    PG_SETUP="${PG_BIN}/postgresql-${PG_MAJOR}-setup"
    [ -n "$DATA_DIR" ] || DATA_DIR="/var/lib/pgsql/${PG_MAJOR}/data"
    PG_CONF="${DATA_DIR}/postgresql.conf"
    PG_HBA="${DATA_DIR}/pg_hba.conf"
    ROBOYAM_CONF="${DATA_DIR}/conf.d/roboyam.conf"
    case "$(uname -m)" in
        aarch64) PGDG_ARCH=aarch64 ;;
        *)       PGDG_ARCH=x86_64 ;;
    esac
    PGDG_REPO_URL="https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-${PGDG_ARCH}/pgdg-redhat-repo-latest.noarch.rpm"
}

resolve_listen_ip() {
    [ -n "$LISTEN_IP" ] || LISTEN_IP="$(detect_private_ip || true)"
    [ -n "$LISTEN_IP" ] || fail "사설 IP 를 찾지 못했습니다. --listen 으로 주세요."
    is_ipv4 "$LISTEN_IP" || fail "--listen 은 IPv4 주소여야 합니다: ${LISTEN_IP}"
    case "$LISTEN_IP" in
        10.*|192.168.*|172.1[6-9].*|172.2[0-9].*|172.3[01].*) ;;
        *) warn "listen 주소(${LISTEN_IP})가 사설 대역이 아닙니다. 공인 IP 에 5432 를 열지 않도록 보안 그룹을 확인하세요." ;;
    esac
}

# ----------------------------------------------------------------------------
# 메모리 계산 — 서버 메모리(또는 --memory-mb)에 맞춘다
# ----------------------------------------------------------------------------
compute_tuning() {
    local ram cores
    ram="$MEMORY_MB"
    [ "$ram" -gt 0 ] || ram="$(( $(mem_nominal_gb) * 1024 ))"
    cores="$(nproc)"
    RAM_MB="$ram"
    CORES="$cores"

    SHARED_BUFFERS_MB=$(( ram * 25 / 100 ))
    EFFECTIVE_CACHE_MB=$(( ram * 75 / 100 ))

    MAINT_WORK_MB=$(( ram / 16 ))
    [ "$MAINT_WORK_MB" -lt 64 ] && MAINT_WORK_MB=64
    [ "$MAINT_WORK_MB" -gt 1024 ] && MAINT_WORK_MB=1024

    WORK_MEM_MB=$(( (ram - SHARED_BUFFERS_MB) / (MAX_CONNECTIONS * 3) ))
    [ "$WORK_MEM_MB" -lt 4 ] && WORK_MEM_MB=4

    # 작은 장비 — WAL 은 1GB 까지만 쌓는다(체크포인트가 잦아져도 디스크를 아낀다)
    MAX_WAL_MB=$(( ram / 2 ))
    [ "$MAX_WAL_MB" -lt 512 ] && MAX_WAL_MB=512
    [ "$MAX_WAL_MB" -gt 2048 ] && MAX_WAL_MB=2048
    MIN_WAL_MB=$(( MAX_WAL_MB / 4 ))

    PARALLEL_WORKERS="$cores"
    PARALLEL_PER_GATHER=$(( cores / 2 ))
    [ "$PARALLEL_PER_GATHER" -lt 1 ] && PARALLEL_PER_GATHER=1

    # 워커 슬롯 = 병렬 + pg_cron + (TimescaleDB) + 여유 2 (PostgreSQL 기본 8 보다 작게 두지 않는다)
    MAX_WORKER_PROCESSES=$(( PARALLEL_WORKERS + CRON_MAX_RUNNING_JOBS + 2 ))
    [ "$WITH_TIMESCALE" -eq 1 ] && MAX_WORKER_PROCESSES=$(( MAX_WORKER_PROCESSES + TS_BACKGROUND_WORKERS ))
    [ "$MAX_WORKER_PROCESSES" -lt 8 ] && MAX_WORKER_PROCESSES=8

    PRELOAD="pg_cron,pg_stat_statements"
    [ "$WITH_TIMESCALE" -eq 1 ] && PRELOAD="pg_cron,pg_stat_statements,timescaledb"
    return 0
}

print_plan() {
    step "설치 계획"
    printf '   %-24s %s\n' "PostgreSQL"        "${PG_MAJOR} (PGDG EL-9-${PGDG_ARCH})"
    printf '   %-24s %s\n' "확장"              "pg_cron, pg_trgm, btree_gist, pg_stat_statements$([ "$WITH_TIMESCALE" -eq 1 ] && echo ', timescaledb')"
    printf '   %-24s %s\n' "DB / 앱 계정"      "${DB_NAME} / ${DB_USER} (superuser 아님)"
    printf '   %-24s %s\n' "데이터 폴더"       "${DATA_DIR}"
    printf '   %-24s %s\n' "listen_addresses"  "localhost,${LISTEN_IP}"
    printf '   %-24s %s\n' "접속 허용(pg_hba)" "${ALLOW[*]} → ${DB_NAME}/${DB_USER} scram-sha-256"
    printf '   %-24s %s\n' "시간대"            "${PG_TIMEZONE} (cron.timezone 같음, OS 변경: $([ "$SET_OS_TIMEZONE" -eq 1 ] && echo 예 || echo 아니오))"
    if [ "$WITH_BACKUP" -eq 1 ]; then
        printf '   %-24s %s\n' "백업" "매일 $(awk '{printf "%02d:%02d", $2, $1}' <<<"$BACKUP_CRON") → ${BACKUP_DIR} (최근 ${BACKUP_KEEP}개)${BACKUP_HOOK:+, 훅 ${BACKUP_HOOK}}"
    else
        printf '   %-24s %s\n' "백업" "만들지 않음 (--no-backup)"
    fi
    echo
    echo "   [메모리 — 기준 ${RAM_MB}MB / ${CORES} 코어]"
    printf '   %-24s %s\n' "max_connections"      "${MAX_CONNECTIONS}"
    printf '   %-24s %s\n' "shared_buffers"       "${SHARED_BUFFERS_MB}MB"
    printf '   %-24s %s\n' "effective_cache_size" "${EFFECTIVE_CACHE_MB}MB"
    printf '   %-24s %s\n' "work_mem"             "${WORK_MEM_MB}MB"
    printf '   %-24s %s\n' "maintenance_work_mem" "${MAINT_WORK_MB}MB"
    printf '   %-24s %s\n' "WAL"                  "max ${MAX_WAL_MB}MB / min ${MIN_WAL_MB}MB"
    printf '   %-24s %s\n' "max_worker_processes" "${MAX_WORKER_PROCESSES} (병렬 ${PARALLEL_WORKERS} + pg_cron ${CRON_MAX_RUNNING_JOBS}$([ "$WITH_TIMESCALE" -eq 1 ] && echo " + TimescaleDB ${TS_BACKGROUND_WORKERS}") + 여유)"
    printf '   %-24s %s\n' "shared_preload"       "${PRELOAD}"
}

# ----------------------------------------------------------------------------
# 패키지
# ----------------------------------------------------------------------------
check_network() {
    getent hosts download.postgresql.org > /dev/null ||
        fail "download.postgresql.org 를 찾지 못했습니다. 외부 통신(NAT·DNS)을 확인하세요."
}

install_packages() {
    log "기본 패키지"
    dnf install -y ca-certificates curl tar gzip dnf-plugins-core cronie

    if rpm -q pgdg-redhat-repo > /dev/null 2>&1; then
        log "PGDG 저장소 : 이미 설치됨"
    else
        log "PGDG 저장소 설치 (${PGDG_ARCH})"
        dnf install -y "${PGDG_REPO_URL}"
    fi
    # OS 기본 postgresql 모듈이 PGDG 패키지를 가린다
    dnf -qy module disable postgresql || true

    log "PostgreSQL ${PG_MAJOR} · pg_cron 설치"
    dnf install -y "postgresql${PG_MAJOR}" "postgresql${PG_MAJOR}-server" "postgresql${PG_MAJOR}-contrib" "pg_cron_${PG_MAJOR}"

    if [ "$WITH_TIMESCALE" -eq 1 ]; then
        log "TimescaleDB 설치 (PGDG timescaledb_${PG_MAJOR})"
        dnf install -y "timescaledb_${PG_MAJOR}" ||
            fail "timescaledb_${PG_MAJOR} 를 설치하지 못했습니다. --timescale 없이 다시 실행하세요."
        log "TimescaleDB $(rpm -q --qf '%{VERSION}' "timescaledb_${PG_MAJOR}")$(compgen -G "/usr/pgsql-${PG_MAJOR}/lib/timescaledb-tsl-*.so" > /dev/null && echo ' — TSL 모듈 있음' || echo ' — TSL 모듈 없음(Apache 판)')"
    fi

    systemctl enable --now crond
}

configure_os_timezone() {
    [ "$SET_OS_TIMEZONE" -eq 1 ] || return 0
    log "OS 시간대 → ${PG_TIMEZONE}"
    timedatectl set-timezone "${PG_TIMEZONE}"
}

detect_locale() {
    if   locale -a | grep -x "C.utf8" > /dev/null;  then DB_LOCALE="C.utf8"
    elif locale -a | grep -x "C.UTF-8" > /dev/null; then DB_LOCALE="C.UTF-8"
    else fail "C.UTF-8 로캘이 없습니다(glibc-minimal-langpack 확인)."
    fi
    log "로캘 : ${DB_LOCALE}"
}

# ----------------------------------------------------------------------------
# 클러스터
# ----------------------------------------------------------------------------
# 데이터 폴더를 바꾸면 서비스의 PGDATA 를 drop-in 으로 바꾼다 — postgresql-N-setup 도 이 값을 읽는다
configure_data_dir() {
    local default_dir="/var/lib/pgsql/${PG_MAJOR}/data"
    local dropin="/etc/systemd/system/${SERVICE_NAME}.service.d/roboyam-pgdata.conf"
    if [ "$DATA_DIR" = "$default_dir" ]; then
        rm -f "$dropin"
    else
        log "데이터 폴더 → ${DATA_DIR}"
        mkdir -p "$DATA_DIR"
        chown postgres:postgres "$DATA_DIR"
        chmod 700 "$DATA_DIR"
        # SELinux 가 켜져 있으면 새 폴더에 PostgreSQL 문맥을 붙인다
        if command -v semanage > /dev/null 2>&1 && [ "$(getenforce 2>/dev/null || echo Disabled)" != "Disabled" ]; then
            semanage fcontext -a -t postgresql_db_t "${DATA_DIR}(/.*)?" 2>/dev/null ||
                semanage fcontext -m -t postgresql_db_t "${DATA_DIR}(/.*)?"
            restorecon -R "$DATA_DIR"
        fi
        mkdir -p "$(dirname "$dropin")"
        printf '[Service]\nEnvironment=PGDATA=%s\n' "$DATA_DIR" > "$dropin"
    fi
    systemctl daemon-reload
}

initialize_cluster() {
    if [ -f "${DATA_DIR}/PG_VERSION" ]; then
        log "클러스터 : 이미 초기화됨 (initdb 생략 — 로캘은 처음 만들 때만 정해진다)"
        return 0
    fi
    log "initdb (UTF8 · ${DB_LOCALE} · libc)"
    PGSETUP_INITDB_OPTIONS="--encoding=UTF8 --locale-provider=libc --locale=${DB_LOCALE} --auth-local=peer --auth-host=scram-sha-256" \
        "${PG_SETUP}" initdb
}

write_postgresql_conf() {
    log "설정 파일 ${ROBOYAM_CONF}"
    mkdir -p "$(dirname "$ROBOYAM_CONF")"

    # postgresql.conf 는 한 줄만 손댄다 — conf.d 를 읽게 한다(이 설치의 값은 전부 roboyam.conf 에 있다)
    if ! grep -Eq "^[[:space:]]*include_dir[[:space:]]*=[[:space:]]*'conf\.d'" "$PG_CONF"; then
        cp -a "$PG_CONF" "${PG_CONF}.backup.${STAMP}"
        printf "\n# roboyam installer-native — 아래 폴더의 설정이 위 값을 덮는다\ninclude_dir = 'conf.d'\n" >> "$PG_CONF"
    fi

    cat > "$ROBOYAM_CONF" <<EOF
# 로보얌 DB 설정 — installer-native/db/roboyam-db-install.sh 가 만든다(다시 실행하면 덮어쓴다)
# 기준: ${RAM_MB}MB / ${CORES} 코어 · ${STAMP}

listen_addresses = 'localhost,${LISTEN_IP}'
port = 5432
max_connections = ${MAX_CONNECTIONS}
password_encryption = 'scram-sha-256'

timezone = '${PG_TIMEZONE}'
log_timezone = '${PG_TIMEZONE}'

# 메모리
shared_buffers = ${SHARED_BUFFERS_MB}MB
effective_cache_size = ${EFFECTIVE_CACHE_MB}MB
work_mem = ${WORK_MEM_MB}MB
maintenance_work_mem = ${MAINT_WORK_MB}MB

# 디스크(EBS gp3 — SSD)
random_page_cost = 1.1
effective_io_concurrency = 200
max_wal_size = ${MAX_WAL_MB}MB
min_wal_size = ${MIN_WAL_MB}MB
checkpoint_completion_target = 0.9

# 워커
max_worker_processes = ${MAX_WORKER_PROCESSES}
max_parallel_workers = ${PARALLEL_WORKERS}
max_parallel_workers_per_gather = ${PARALLEL_PER_GATHER}

# 확장 — shared_preload_libraries 는 재기동해야 바뀐다
shared_preload_libraries = '${PRELOAD}'

# pg_cron (docker/prod/compose.db.yml 의 command 와 같은 값)
#   cron.database_name : 앱 DB 와 같아야 DB 배치가 돈다
#   cron.timezone      : DB 배치 cron 식의 시간대(한국시간). 나중에 바꾸면 등록된 cron 식이 어긋난다
#   use_background_workers : 작업마다 접속을 새로 열지 않는다(비밀번호·pg_hba 가 필요 없다)
cron.database_name = '${DB_NAME}'
cron.timezone = '${PG_TIMEZONE}'
cron.use_background_workers = on
cron.max_running_jobs = ${CRON_MAX_RUNNING_JOBS}

pg_stat_statements.max = 1000
pg_stat_statements.track = top

# 로그 — 느린 쿼리·잠금 대기만 남긴다
log_min_duration_statement = 1000
log_lock_waits = on
log_temp_files = 0
log_checkpoints = on
log_connections = off
log_disconnections = off
EOF

    if [ "$WITH_TIMESCALE" -eq 1 ]; then
        cat >> "$ROBOYAM_CONF" <<EOF

# TimescaleDB
timescaledb.max_background_workers = ${TS_BACKGROUND_WORKERS}
timescaledb.telemetry_level = off
EOF
    fi
    chown -R postgres:postgres "$(dirname "$ROBOYAM_CONF")"
    chmod 600 "$ROBOYAM_CONF"
}

# 하모니카와 달리 전체 허용(0.0.0.0/0)을 두지 않는다 — --allow 대역에서 앱 DB·앱 계정만 받는다.
# 블록을 표시 줄로 감싸 다시 실행하면 통째로 바꾼다.
write_pg_hba() {
    log "pg_hba.conf"
    [ -f "$PG_HBA" ] || fail "pg_hba.conf 가 없습니다: ${PG_HBA}"
    cp -a "$PG_HBA" "${PG_HBA}.backup.${STAMP}"

    local begin="# >>> roboyam installer-native >>>"
    local end="# <<< roboyam installer-native <<<"
    sed -i "\|^${begin}\$|,\|^${end}\$|d" "$PG_HBA"
    {
        echo "$begin"
        echo "# 앱 서버(--allow)에서 ${DB_NAME} DB 에 ${DB_USER} 계정으로만 접속한다. postgres 는 이 서버 안에서 peer 로만 쓴다"
        local c
        for c in "${ALLOW[@]}"; do
            printf 'host    %s    %s    %s    scram-sha-256\n' "$DB_NAME" "$DB_USER" "$c"
        done
        echo "$end"
    } >> "$PG_HBA"
    chown postgres:postgres "$PG_HBA"
    chmod 600 "$PG_HBA"
}

restart_postgresql() {
    log "PostgreSQL (재)기동"
    systemctl enable "${SERVICE_NAME}"
    systemctl restart "${SERVICE_NAME}"
    local i
    for i in $(seq 1 30); do
        runuser -u postgres -- "${PG_BIN}/pg_isready" -q && return 0
        sleep 1
    done
    systemctl status "${SERVICE_NAME}" --no-pager --full || true
    journalctl -u "${SERVICE_NAME}" -n 80 --no-pager || true
    fail "PostgreSQL 이 뜨지 않았습니다(shared_preload_libraries 의 패키지·메모리 설정을 확인)."
}

# ----------------------------------------------------------------------------
# 계정·DB·확장
# ----------------------------------------------------------------------------
psql_su() {   # <db> [psql 인자...] — postgres(superuser)로, 실패하면 멈춘다
    local db="$1"; shift
    runuser -u postgres -- "$PSQL" -X -q -d "$db" -v ON_ERROR_STOP=1 "$@"
}
psql_val() {   # <db> <sql> — 값 하나, 실패해도 멈추지 않는다(검증용)
    runuser -u postgres -- "$PSQL" -X -d "$1" -tAc "$2" 2>/dev/null | head -n 1 || true
}

input_password() {
    step "앱 계정 비밀번호"
    if [ -n "$DB_PASSWORD" ]; then
        log "${DB_USER} : 환경 변수 ROBOYAM_DB_PASSWORD 로 받음"
        return 0
    fi
    if [ "$(psql_val postgres "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'" 2>/dev/null)" = "1" ]; then
        echo "   ${DB_USER} 계정이 이미 있습니다. 비워 두고 Enter 를 두 번 누르면 비밀번호를 바꾸지 않습니다."
        local p1="" p2=""
        read -r -s -p "   새 비밀번호(바꾸지 않으려면 Enter): " p1; echo
        if [ -z "$p1" ]; then KEEP_PASSWORD=1; return 0; fi
        read -r -s -p "   새 비밀번호 확인: " p2; echo
        [ "$p1" = "$p2" ] || fail "비밀번호가 일치하지 않습니다."
        DB_PASSWORD="$p1"; unset p1 p2
        return 0
    fi
    echo "   앱 계정(${DB_USER}) 비밀번호 — 앱 서버 설치 때 같은 값을 다시 입력합니다(그쪽에서 ENC(...) 로 암호화)."
    read_password_twice "${DB_USER} 비밀번호" DB_PASSWORD
    [ "${#DB_PASSWORD}" -ge 12 ] || warn "비밀번호가 12자보다 짧습니다."
}

create_role_and_db() {
    log "계정 ${DB_USER} (LOGIN, superuser 아님)"
    if [ "$(psql_val postgres "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'")" = "1" ]; then
        psql_su postgres -c "ALTER ROLE ${DB_USER} WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;"
    else
        psql_su postgres -c "CREATE ROLE ${DB_USER} WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;"
    fi
    if [ "${KEEP_PASSWORD:-0}" -ne 1 ]; then
        psql_su postgres --set=pw="${DB_PASSWORD}" <<SQL
ALTER ROLE ${DB_USER} WITH PASSWORD :'pw';
SQL
    fi

    if [ "$(psql_val postgres "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'")" = "1" ]; then
        log "DB ${DB_NAME} : 이미 있음 — 소유자만 맞춘다"
        psql_su postgres -c "ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};"
    else
        log "DB ${DB_NAME} 생성 (UTF8 · ${DB_LOCALE})"
        psql_su postgres -c "CREATE DATABASE ${DB_NAME} WITH OWNER = ${DB_USER} ENCODING = 'UTF8'
            LOCALE_PROVIDER = libc LC_COLLATE = '${DB_LOCALE}' LC_CTYPE = '${DB_LOCALE}' TEMPLATE = template0;"
    fi
    # PUBLIC 의 접속 권한을 거두고 앱 계정에만 준다
    psql_su postgres -c "REVOKE ALL ON DATABASE ${DB_NAME} FROM PUBLIC; GRANT CONNECT, TEMPORARY ON DATABASE ${DB_NAME} TO ${DB_USER};"
}

# 앱(Flyway)은 superuser 가 아니어서 pg_cron 을 만들지 못한다 — 여기서 superuser 로 만들고 cron 스키마를 쓰게 한다.
# pg_trgm·btree_gist 는 trusted 확장이라 DB 소유자도 만들 수 있지만 같이 만들어 둔다(Flyway 의 IF NOT EXISTS 는 그냥 지나간다).
create_extensions() {
    log "확장 생성 (superuser)"
    local ts_sql=""
    [ "$WITH_TIMESCALE" -eq 1 ] && ts_sql="CREATE EXTENSION IF NOT EXISTS timescaledb;"
    psql_su "$DB_NAME" <<SQL
CREATE EXTENSION IF NOT EXISTS pg_cron;
GRANT USAGE ON SCHEMA cron TO ${DB_USER};
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
${ts_sql}
SQL
}

# ----------------------------------------------------------------------------
# 백업
# ----------------------------------------------------------------------------
install_backup() {
    [ "$WITH_BACKUP" -eq 1 ] || { rm -f /etc/cron.d/roboyam-db-backup; return 0; }
    log "백업 cron"
    mkdir -p "$BIN_DIR" /etc/roboyam "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"
    install -m 755 "${SCRIPT_DIR}/roboyam-db-backup.sh" "${BIN_DIR}/roboyam-db-backup.sh"
    if [ -n "$BACKUP_HOOK" ]; then
        install -m 700 "$BACKUP_HOOK" "${BIN_DIR}/backup-hook.sh"
    fi
    cat > "$BACKUP_CONF" <<EOF
# roboyam-db-backup.sh 설정 — roboyam-db-install.sh 가 만든다
PG_BIN=${PG_BIN}
DB_NAME=${DB_NAME}
BACKUP_DIR=${BACKUP_DIR}
BACKUP_KEEP=${BACKUP_KEEP}
# 백업이 끝나면 덤프 파일 경로를 인자로 부른다(없으면 건너뛴다) — R2 업로드 등
BACKUP_HOOK=$([ -n "$BACKUP_HOOK" ] && echo "${BIN_DIR}/backup-hook.sh")
EOF
    chmod 600 "$BACKUP_CONF"
    cat > /etc/cron.d/roboyam-db-backup <<EOF
# 로보얌 DB 백업 (roboyam-db-install.sh 가 만든다)
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
${BACKUP_CRON} * * * root ${BIN_DIR}/roboyam-db-backup.sh >> /var/log/roboyam-db-backup.log 2>&1
EOF
    chmod 644 /etc/cron.d/roboyam-db-backup
}

# ----------------------------------------------------------------------------
# 검증 — 기대와 다르면 실패로 세고 종료 코드 3
# ----------------------------------------------------------------------------
CHECK_FAILED=0
ok()  { printf '   [OK]   %-28s %s\n' "$1" "$2"; }
bad() { printf '   [실패] %-28s %s\n' "$1" "$2" >&2; CHECK_FAILED=$((CHECK_FAILED + 1)); }
check_eq() { if [ "$2" = "$3" ]; then ok "$1" "$3"; else bad "$1" "기대=$2  실제=${3:-<없음>}"; fi; }

verify() {
    step "검증"
    local db="$DB_NAME"
    check_eq "서버 인코딩"         "UTF8"              "$(psql_val "$db" "SHOW server_encoding")"
    check_eq "DB 로캘(ctype)"      "$DB_LOCALE"        "$(psql_val "$db" "SELECT datctype FROM pg_database WHERE datname = current_database()")"
    check_eq "한글 trigram"        "t"                 "$(psql_val "$db" "SELECT cardinality(show_trgm('한글')) > 0")"
    check_eq "max_connections"     "$MAX_CONNECTIONS"  "$(psql_val "$db" "SHOW max_connections")"
    check_eq "shared_buffers"      "${SHARED_BUFFERS_MB}MB" "$(psql_val "$db" "SELECT (setting::bigint * 8 / 1024) || 'MB' FROM pg_settings WHERE name = 'shared_buffers'")"
    check_eq "shared_preload"      "$PRELOAD"          "$(psql_val "$db" "SHOW shared_preload_libraries")"
    check_eq "cron.database_name"  "$db"               "$(psql_val "$db" "SHOW cron.database_name")"
    check_eq "cron.timezone"       "$PG_TIMEZONE"      "$(psql_val "$db" "SHOW cron.timezone")"
    check_eq "cron 백그라운드 워커" "on"               "$(psql_val "$db" "SHOW cron.use_background_workers")"
    check_eq "확장 pg_cron"        "1"                 "$(psql_val "$db" "SELECT count(*) FROM pg_extension WHERE extname = 'pg_cron'")"
    check_eq "cron 스키마 권한"    "t"                 "$(psql_val "$db" "SELECT has_schema_privilege('${DB_USER}', 'cron', 'USAGE')")"
    check_eq "앱 계정 superuser"   "f"                 "$(psql_val "$db" "SELECT rolsuper FROM pg_roles WHERE rolname = '${DB_USER}'")"
    check_eq "DB 소유자"           "$DB_USER"          "$(psql_val "$db" "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()")"
    check_eq "listen_addresses"    "localhost,${LISTEN_IP}" "$(psql_val "$db" "SHOW listen_addresses")"
    if [ "$WITH_TIMESCALE" -eq 1 ]; then
        check_eq "확장 timescaledb" "1" "$(psql_val "$db" "SELECT count(*) FROM pg_extension WHERE extname = 'timescaledb'")"
        # 판은 실패로 세지 않고 알리기만 한다 — apache 판이면 압축·연속 집계 정책 같은 TSL 기능을 쓸 수 없다
        local ts_license
        ts_license="$(psql_val "$db" "SELECT current_setting('timescaledb.license', true)")"
        if [ "$ts_license" = "timescale" ]; then
            ok "TimescaleDB 판" "timescale (TSL 포함 — 압축·연속 집계 정책 사용 가능)"
        else
            warn "TimescaleDB 판이 ${ts_license:-알 수 없음} 입니다 — 압축·연속 집계 정책 같은 TSL 기능은 쓸 수 없습니다."
        fi
    fi
}

print_result() {
    echo
    echo "============================================================"
    if [ "$CHECK_FAILED" -eq 0 ]; then echo " DB 서버 설치 완료"; else echo " DB 서버 설치 완료 — 검증 실패 ${CHECK_FAILED}건"; fi
    echo "============================================================"
    printf '   %-20s %s\n' "앱 DB_URL"   "jdbc:postgresql://${LISTEN_IP}:5432/${DB_NAME}"
    printf '   %-20s %s\n' "DB_USERNAME" "${DB_USER}"
    printf '   %-20s %s\n' "설정"        "${ROBOYAM_CONF}"
    printf '   %-20s %s\n' "로그"        "${LOG_FILE}"
    [ "$WITH_BACKUP" -eq 1 ] && printf '   %-20s %s\n' "백업 손으로" "${BIN_DIR}/roboyam-db-backup.sh"
    echo
    echo "   다음: 앱 서버에서 installer-native/app/roboyam-app-install.sh --db-host ${LISTEN_IP} ..."
    echo "   앱(관리자 앱)이 처음 뜨면 Flyway 가 표를 만들고 DB 배치를 pg_cron 에 등록한다. 확인:"
    echo "     sudo -u postgres psql -d ${DB_NAME} -c 'SELECT jobname, schedule, active FROM cron.job ORDER BY 1'"
    if [ "$CHECK_FAILED" -ne 0 ]; then
        echo; echo "   위 [실패] 항목을 해결한 뒤 다시 실행하세요." >&2
        exit 3
    fi
}

main() {
    parse_args "$@"
    resolve_paths
    require_root
    check_os
    resolve_listen_ip
    compute_tuning

    if [ "$DRY_RUN" -eq 1 ]; then
        echo "============================================================"
        echo " 로보얌 DB 서버 설치 — dry-run (아무것도 바꾸지 않는다)"
        echo "============================================================"
        print_plan
        getent hosts download.postgresql.org > /dev/null || warn "download.postgresql.org 를 찾지 못했습니다(외부 통신 확인)."
        echo; log "dry-run 끝"
        exit 0
    fi

    install -m 600 /dev/null "$LOG_FILE"   # 설치 토큰 등이 찍히므로 root 만 읽게
    exec > >(tee -a "$LOG_FILE") 2>&1
    echo "============================================================"
    echo " 로보얌 DB 서버 설치 (PostgreSQL ${PG_MAJOR} + pg_cron)"
    echo "============================================================"
    print_plan
    check_network

    step "패키지"
    install_packages
    configure_os_timezone
    detect_locale

    step "클러스터"
    configure_data_dir
    initialize_cluster
    write_postgresql_conf
    write_pg_hba
    restart_postgresql

    input_password
    step "계정·DB·확장"
    create_role_and_db
    create_extensions
    DB_PASSWORD=""; unset DB_PASSWORD ROBOYAM_DB_PASSWORD

    step "백업"
    install_backup

    verify
    print_result
}

main "$@"
