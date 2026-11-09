#!/usr/bin/env bash
# ============================================================================
# 로보얌 원스톱 설치·배포 — DB 서버에서 한 번 실행한다
#
# 설치(처음):
#   [1/3] 이 서버(DB)에 PostgreSQL 18 + pg_cron 설치          db/roboyam-db-install.sh
#   [2/3] WAS1 에 SSH 로 묶음(jar·스크립트·인증서)을 보내 풀고 설치 — api + admin, 기동·readiness 확인
#   [3/3] WAS2 에 같은 묶음 + WAS1 의 공통 환경 파일(roboyam.env)을 보내 설치 — api, 기동·readiness 확인
# 배포(--deploy, 새 jar):
#   DB 백업 → WAS1 admin → WAS1 api → WAS2 api 순서로 jar 를 보내 바꾸고 재기동(readiness 실패 시 그 서버는 이전 jar 로 되돌리고 멈춘다)
# 데이터 초기화(--reset-data, 시험 서버용):
#   앱 멈춤 → DB 백업 → DB 를 지우고 빈 DB 로 다시 만듦(확장·권한 포함) → PostgreSQL 재시작 → WAS1 api·admin → WAS2 api 기동(readiness 확인)
#   → 설치 마법사(/install)부터 다시 한다(데모 데이터를 다시 넣을 때 등). 앱 설정 파일(키·토큰)·R2 의 이미지는 그대로 둔다
#
# 묶음(make-package.sh 가 만든다 — 이 스크립트와 같은 폴더):
#   roboyam-install.sh · install.conf · lib/ · db/ · app/ · application/roboyam-{api,admin}.jar · certs/(Origin CA 인증서)
#
# 사용법 (DB 서버에서, root)
#   tar xzf roboyam-native-*.tar.gz && cd roboyam-native
#   cp install.conf.example install.conf && vi install.conf      앱 서버 사설 IP·도메인·SSH 키
#   sudo ./roboyam-install.sh --dry-run                            점검만 (DB·앱 서버 계획 출력)
#   sudo ./roboyam-install.sh                                      설치
#   sudo ./roboyam-install.sh --deploy                             새 묶음의 jar 로 배포·재기동
#   sudo ./roboyam-install.sh --reset-data                         DB 를 비우고 설치 마법사 전 상태로(시험 서버용)
#
# 옵션
#   -c, --config FILE   설정 파일 (기본: 이 폴더의 install.conf)
#       --deploy        설치 대신 배포(jar 교체·재기동)만 한다
#       --reset-data    DB 를 지우고 빈 DB 로 다시 만든 뒤 앱을 다시 띄운다 — DB 이름을 입력해야 진행한다(-y 로도 건너뛰지 않는다)
#       --only STEP     설치 단계 하나만 다시 한다: db | was1 | was2
#       --no-backup     --deploy·--reset-data 때 먼저 하는 DB 백업을 건너뛴다
#       --keep-remote   앱 서버에 보낸 임시 폴더를 지우지 않는다(문제 확인용 — 인증서 키가 남는다)
#       --dry-run       점검·계획만 (DB 는 --dry-run, 앱 서버는 묶음을 보내 --dry-run 한 뒤 지운다)
#   -y, --yes           확인 질문 없이 진행
#   -h, --help          도움말
#
# 앞 조건
#   - 앱 서버 보안 그룹이 DB 서버에서 오는 22 를 받는다(사설 IP 로 접속한다)
#   - SSH 키: install.conf 의 SSH_KEY(이 서버에 둔 .pem, 600) 또는 ssh -A 로 들어와 에이전트를 넘긴다
#   - SSH 계정은 비밀번호 없는 sudo 가 된다(EC2 기본 계정 rocky)
#   - DB 비밀번호는 시작할 때 한 번 묻는다(무인: 환경 변수 ROBOYAM_DB_PASSWORD). DB 계정에 쓰고, WAS1 에서 ENC(...) 로 암호화된다
#   - 이미지 저장소는 install.conf 의 STORAGE(기본 r2 | s3 | local)·R2_ACCOUNT_ID·S3_*. S3_ACCESS_KEY 를 주면 비밀 키를 시작할 때 묻는다(무인: ROBOYAM_S3_SECRET_KEY) — WAS1 에서 ENC(...)
# ============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
. "${SCRIPT_DIR}/lib/common.sh"
trap on_error ERR

CONFIG="${SCRIPT_DIR}/install.conf"
MODE=install
ONLY=""
DEPLOY_BACKUP=1
KEEP_REMOTE=0
DRY_RUN=0
ASSUME_YES=0
APP_PKG_DIR="${SCRIPT_DIR}/application"
LOG_FILE="/var/log/roboyam-install-${STAMP}.log"
REMOTE_DIR_NAME="roboyam-install-${STAMP}"
REMOTE_CLEANUP=()

usage() { usage_from_header "$0"; }
usage_error() { printf '[사용법 오류] %s\n\n' "$*" >&2; usage >&2; exit 2; }

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            -c|--config)   CONFIG="${2:?--config 값이 필요합니다}"; shift 2 ;;
            --deploy)      MODE=deploy; shift ;;
            --reset-data)  MODE=reset; shift ;;
            --only)        ONLY="${2:?--only 값이 필요합니다}"; shift 2 ;;
            --no-backup)   DEPLOY_BACKUP=0; shift ;;
            --keep-remote) KEEP_REMOTE=1; shift ;;
            --dry-run)     DRY_RUN=1; shift ;;
            -y|--yes)      ASSUME_YES=1; shift ;;
            -h|--help)     usage; exit 0 ;;
            *)             usage_error "알 수 없는 옵션: $1" ;;
        esac
    done
    case "$ONLY" in ""|db|was1|was2) ;; *) usage_error "--only 는 db | was1 | was2 입니다: ${ONLY}" ;; esac
    [ "$MODE" = install ] || [ -z "$ONLY" ] || usage_error "--only 는 설치에서만 씁니다."
}

# ----------------------------------------------------------------------------
# 설정
# ----------------------------------------------------------------------------
load_config() {
    [ -f "$CONFIG" ] || fail "설정 파일이 없습니다: ${CONFIG}
       cp ${SCRIPT_DIR}/install.conf.example ${SCRIPT_DIR}/install.conf 후 값을 채우세요."
    # 운영자가 직접 만든 설정 파일이다(셸 변수 형식)
    # shellcheck disable=SC1090
    . "$CONFIG"

    SSH_USER="${SSH_USER:-rocky}"
    SSH_KEY="${SSH_KEY:-}"
    WAS1="${WAS1:-}"
    WAS2="${WAS2:-}"
    DB_PRIVATE_IP="${DB_PRIVATE_IP:-}"
    DB_NAME="${DB_NAME:-roboyam}"
    DB_USER="${DB_USER:-roboyam}"
    DOMAINS="${DOMAINS:-}"
    ADMIN_DOMAIN="${ADMIN_DOMAIN:-}"
    ADMIN_ALLOW="${ADMIN_ALLOW:-}"
    ORIGIN_CERT="${ORIGIN_CERT:-certs/origin.pem}"
    ORIGIN_KEY="${ORIGIN_KEY:-certs/origin.key}"
    WAS1_NODE_ID="${WAS1_NODE_ID:-1}"
    WAS2_NODE_ID="${WAS2_NODE_ID:-2}"
    ADMIN_NODE_ID="${ADMIN_NODE_ID:-3}"
    WAS1_SWAP="${WAS1_SWAP:-}"
    WAS2_SWAP="${WAS2_SWAP:-}"
    WAS1_EXTRA_ARGS="${WAS1_EXTRA_ARGS:-}"
    WAS2_EXTRA_ARGS="${WAS2_EXTRA_ARGS:-}"
    DB_EXTRA_ARGS="${DB_EXTRA_ARGS:-}"
    DB_BACKUP_HOOK="${DB_BACKUP_HOOK:-}"
    STORAGE="${STORAGE:-r2}"
    R2_ACCOUNT_ID="${R2_ACCOUNT_ID:-}"
    S3_ENDPOINT="${S3_ENDPOINT:-}"
    S3_REGION="${S3_REGION:-}"
    S3_BUCKET="${S3_BUCKET:-}"
    S3_PUBLIC_URL="${S3_PUBLIC_URL:-}"
    S3_PATH_STYLE="${S3_PATH_STYLE:-false}"
    S3_ACCESS_KEY="${S3_ACCESS_KEY:-}"

    is_ipv4 "$WAS1" || valid_hostname "$WAS1" || fail "install.conf 의 WAS1(앱 서버 1 사설 IP)이 비었거나 올바르지 않습니다: ${WAS1:-<없음>}"
    [ -z "$WAS2" ] || is_ipv4 "$WAS2" || valid_hostname "$WAS2" || fail "install.conf 의 WAS2 가 올바르지 않습니다: ${WAS2}"
    [ -n "$DOMAINS" ] || fail "install.conf 의 DOMAINS(공개 사이트 호스트)가 비었습니다."
    [ -n "$ADMIN_DOMAIN" ] || fail "install.conf 의 ADMIN_DOMAIN(관리자 앱 호스트)이 비었습니다."
    case "$STORAGE" in local|r2|s3) ;; *) fail "install.conf 의 STORAGE 는 r2 · s3 · local 중 하나입니다: ${STORAGE}" ;; esac
    if [ "$STORAGE" != local ] && [ "$MODE" = install ]; then
        [ -n "$S3_BUCKET" ] || fail "install.conf 의 S3_BUCKET 이 비었습니다(STORAGE=${STORAGE} — 앱 서버 두 대가 같은 이미지를 보려면 버킷이 필요하다. 서버 한 대면 STORAGE=local)."
        if [ "$STORAGE" = r2 ]; then
            [ -n "$R2_ACCOUNT_ID" ] || [ -n "$S3_ENDPOINT" ] || fail "install.conf 의 R2_ACCOUNT_ID 가 비었습니다(Cloudflare › R2 › 개요의 계정 ID)."
            [ -n "$S3_ACCESS_KEY" ] || fail "install.conf 의 S3_ACCESS_KEY 가 비었습니다(STORAGE=r2 — R2 API 토큰의 Access Key ID)."
        else
            [ -n "$S3_REGION" ] || fail "install.conf 의 S3_REGION 이 비었습니다(STORAGE=s3 — 예 ap-northeast-2)."
        fi
    fi
    [ "$STORAGE" != local ] || [ -z "$WAS2" ] || warn "STORAGE=local 인데 WAS2 가 있습니다 — 한 서버에 올린 이미지를 다른 서버가 못 읽습니다."
    if [ -n "$SSH_KEY" ]; then
        case "$SSH_KEY" in /*) ;; *) SSH_KEY="${SCRIPT_DIR}/${SSH_KEY}" ;; esac
        [ -f "$SSH_KEY" ] || fail "SSH 키 파일이 없습니다: ${SSH_KEY}"
        chmod 600 "$SSH_KEY" 2>/dev/null || true
    elif [ -z "${SSH_AUTH_SOCK:-}" ]; then
        fail "SSH_KEY 가 비었고 ssh-agent 도 없습니다. install.conf 에 SSH_KEY 를 주거나 ssh -A 로 이 서버에 들어오세요(sudo 는 -E 필요)."
    fi
    case "$ORIGIN_CERT" in /*) ;; *) ORIGIN_CERT="${SCRIPT_DIR}/${ORIGIN_CERT}" ;; esac
    case "$ORIGIN_KEY" in /*) ;; *) ORIGIN_KEY="${SCRIPT_DIR}/${ORIGIN_KEY}" ;; esac
    if [ -n "$DB_BACKUP_HOOK" ]; then
        case "$DB_BACKUP_HOOK" in /*) ;; *) DB_BACKUP_HOOK="${SCRIPT_DIR}/${DB_BACKUP_HOOK}" ;; esac
    fi
}

valid_hostname() { [[ "$1" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]]; }

# --only 가 없거나 주어진 단계 중 하나면 참
want() { local s; [ -z "$ONLY" ] && return 0; for s in "$@"; do [ "$ONLY" = "$s" ] && return 0; done; return 1; }

check_package() {
    API_JAR="${APP_PKG_DIR}/roboyam-api.jar"
    ADMIN_JAR="${APP_PKG_DIR}/roboyam-admin.jar"
    [ -f "$API_JAR" ] || fail "jar 가 없습니다: ${API_JAR} (make-package.sh 로 묶음을 만드세요)"
    [ -f "$ADMIN_JAR" ] || fail "jar 가 없습니다: ${ADMIN_JAR} (make-package.sh 로 묶음을 만드세요)"
    if [ "$MODE" = install ]; then
        [ -f "$ORIGIN_CERT" ] || fail "Origin CA 인증서가 없습니다: ${ORIGIN_CERT} (Cloudflare › SSL/TLS › Origin Server 에서 발급)"
        [ -f "$ORIGIN_KEY" ] || fail "Origin CA 개인 키가 없습니다: ${ORIGIN_KEY}"
    fi
    local f
    for f in "${SCRIPT_DIR}"/lib/*.sh "${SCRIPT_DIR}"/db/*.sh "${SCRIPT_DIR}"/app/*.sh; do
        if grep -q $'\r' "$f"; then fail "줄 끝이 CRLF 인 스크립트가 있습니다(Windows 에서 고친 파일?): ${f}"; fi
    done
    [ -f "${SCRIPT_DIR}/PACKAGE_INFO" ] && sed 's/^/   /' "${SCRIPT_DIR}/PACKAGE_INFO"
    return 0
}

# ----------------------------------------------------------------------------
# SSH
# ----------------------------------------------------------------------------
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -o BatchMode=yes -o ServerAliveInterval=30)

rssh() {   # <호스트> <원격 명령...>
    local host="$1"; shift
    ssh "${SSH_OPTS[@]}" ${SSH_KEY:+-i "$SSH_KEY"} "${SSH_USER}@${host}" "$@"
}

# 원격 명령줄 — 인자를 원격 셸에 맞게 따옴표 친다
quote_args() { local a out=""; for a in "$@"; do out+="$(printf '%q' "$a") "; done; printf '%s' "$out"; }

check_remote() {   # <이름> <호스트>
    local name="$1" host="$2" info
    # 원격에서 한 줄씩 「이름=값」으로 알려 준다(입력이 필요 없으므로 표준 입력은 막는다 — 키보드 입력을 가로채지 않게)
    info="$(rssh "$host" 'sudo -n true 2>/dev/null && echo SUDO_OK; echo "ARCH=$(uname -m)"; . /etc/os-release && echo "OS=$PRETTY_NAME"; command -v tar >/dev/null && echo TAR_OK; ip -4 route get 1.1.1.1 2>/dev/null | awk "{for(i=1;i<NF;i++) if(\$i==\"src\"){print \"IP=\" \$(i+1); exit}}"; free -m | awk "/^Mem:/{print \"MEM=\" \$2}"' < /dev/null 2>&1)" ||
        fail "${name}(${host}) 에 SSH 로 접속하지 못했습니다.
       - 앱 서버 보안 그룹이 DB 서버(이 서버)의 22 를 받는지
       - SSH_USER(${SSH_USER})·SSH_KEY 가 맞는지
       출력: ${info}"
    grep -qx SUDO_OK <<<"$info" || fail "${name}(${host}): ${SSH_USER} 가 비밀번호 없이 sudo 할 수 없습니다."
    grep -qx 'ARCH=aarch64' <<<"$info" || warn "${name}(${host}): aarch64 가 아닙니다 ($(sed -n 's/^ARCH=//p' <<<"$info"))"
    if ! grep -qx TAR_OK <<<"$info"; then
        log "${name}: tar 설치"
        rssh "$host" 'sudo -n dnf install -y -q tar' < /dev/null > /dev/null
    fi
    local ip mem
    ip="$(sed -n 's/^IP=//p' <<<"$info")"
    mem="$(sed -n 's/^MEM=//p' <<<"$info")"
    log "${name} ${host} : $(sed -n 's/^OS=//p' <<<"$info") · 사설 IP ${ip:-?} · 메모리 ${mem:-?}MB · sudo OK"
    printf -v "${name}_PRIVATE_IP" '%s' "${ip:-$host}"
}

# 묶음을 보낸다 — lib·app·application(jar)·certs. 원격 홈의 임시 폴더에 풀린다
send_bundle() {   # <호스트>
    local host="$1" rdir="${REMOTE_DIR_NAME}"
    log "묶음 전송 → ${host}:~/${rdir}"
    local items=(lib app application)
    [ "$MODE" = install ] && items+=(certs-send)
    local stage; stage="$(mktemp -d)"
    cp -a "${SCRIPT_DIR}/lib" "${SCRIPT_DIR}/app" "${APP_PKG_DIR}" "$stage/"
    [ "$(basename "$APP_PKG_DIR")" = application ] || mv "$stage/$(basename "$APP_PKG_DIR")" "$stage/application"
    if [ "$MODE" = install ]; then
        mkdir -p "$stage/certs-send"
        install -m 600 "$ORIGIN_CERT" "$stage/certs-send/origin.pem"
        install -m 600 "$ORIGIN_KEY" "$stage/certs-send/origin.key"
    fi
    tar -czf - -C "$stage" "${items[@]}" |
        rssh "$host" "umask 077; rm -rf ~/${rdir} && mkdir -p ~/${rdir} && tar -xzf - -C ~/${rdir}"
    rm -rf "$stage"
    REMOTE_CLEANUP+=("$host")
}

cleanup_remote() {
    [ "$KEEP_REMOTE" -eq 1 ] && return 0
    local host
    for host in "${REMOTE_CLEANUP[@]}"; do
        rssh "$host" "rm -rf ~/${REMOTE_DIR_NAME}" < /dev/null 2>/dev/null || warn "${host}:~/${REMOTE_DIR_NAME} 를 지우지 못했습니다(인증서 키가 있다 — 손으로 지우세요)."
    done
    REMOTE_CLEANUP=()
}
trap 'cleanup_remote' EXIT

# ----------------------------------------------------------------------------
# 단계
# ----------------------------------------------------------------------------
input_db_password() {
    DB_PASSWORD="${ROBOYAM_DB_PASSWORD:-}"
    unset ROBOYAM_DB_PASSWORD
    [ -n "$DB_PASSWORD" ] && { log "DB 비밀번호 : 환경 변수로 받음"; return 0; }
    step "DB 비밀번호"
    echo "   앱 계정(${DB_USER}) 비밀번호 — DB 계정에 쓰고, WAS1 에서 ENC(...) 로 암호화해 앱 환경 파일에 둔다(평문 저장 없음)."
    echo "   이미 설치된 DB·앱이면 지금 쓰는 비밀번호를 그대로 입력한다."
    read_password_twice "DB 비밀번호" DB_PASSWORD
}

input_s3_secret() {
    S3_SECRET="${ROBOYAM_S3_SECRET_KEY:-}"
    unset ROBOYAM_S3_SECRET_KEY
    [ "$STORAGE" != local ] && [ -n "$S3_ACCESS_KEY" ] || return 0
    [ -n "$S3_SECRET" ] && { log "S3 비밀 키 : 환경 변수로 받음"; return 0; }
    step "S3 비밀 키"
    echo "   접근 키 ${S3_ACCESS_KEY} 의 비밀 키(R2 › API 토큰의 Secret Access Key) — WAS1 에서 ENC(...) 로 암호화해 앱 환경 파일에 둔다(평문 저장 없음)."
    echo "   WAS1 에 공통 환경 파일이 이미 있으면 쓰이지 않는다."
    read_password_twice "S3 비밀 키" S3_SECRET
}

was_allow_args() {
    local args=(--allow "${WAS1_PRIVATE_IP}/32")
    [ -n "$WAS2" ] && args+=(--allow "${WAS2_PRIVATE_IP}/32")
    printf '%s\n' "${args[@]}"
}

step_db() {
    step "[1/3] DB 설치 (이 서버)"
    local args=()
    mapfile -t args < <(was_allow_args)
    [ -n "$DB_PRIVATE_IP" ] && args+=(--listen "$DB_PRIVATE_IP")
    args+=(--db "$DB_NAME" --user "$DB_USER")
    [ -n "$DB_BACKUP_HOOK" ] && args+=(--backup-hook "$DB_BACKUP_HOOK")
    # shellcheck disable=SC2206
    [ -n "$DB_EXTRA_ARGS" ] && args+=($DB_EXTRA_ARGS)
    if [ "$DRY_RUN" -eq 1 ]; then
        bash "${SCRIPT_DIR}/db/roboyam-db-install.sh" "${args[@]}" --dry-run
    else
        ROBOYAM_DB_PASSWORD="$DB_PASSWORD" bash "${SCRIPT_DIR}/db/roboyam-db-install.sh" "${args[@]}"
    fi
}

storage_args() {   # <배열 이름> — 이미지 저장소 옵션을 그 배열에 붙인다
    local -n _a="$1"
    _a+=(--storage "$STORAGE")
    [ "$STORAGE" != local ] || return 0
    [ -n "$R2_ACCOUNT_ID" ] && [ "$STORAGE" = r2 ] && _a+=(--r2-account-id "$R2_ACCOUNT_ID")
    [ -n "$S3_REGION" ] && _a+=(--s3-region "$S3_REGION")
    [ -n "$S3_BUCKET" ] && _a+=(--s3-bucket "$S3_BUCKET")
    [ -n "$S3_ENDPOINT" ] && _a+=(--s3-endpoint "$S3_ENDPOINT")
    [ -n "$S3_PUBLIC_URL" ] && _a+=(--s3-public-url "$S3_PUBLIC_URL")
    [ "$S3_PATH_STYLE" = true ] && _a+=(--s3-path-style)
    [ -n "$S3_ACCESS_KEY" ] && _a+=(--s3-access-key "$S3_ACCESS_KEY")
    return 0
}

was_install_args() {   # <was1|was2>
    local which="$1" args=()
    local d
    for d in $DOMAINS; do args+=(--domain "$d"); done
    args+=(--origin-cert "certs-send/origin.pem" --origin-key "certs-send/origin.key" --db-name "$DB_NAME" --db-user "$DB_USER")
    if [ "$which" = was1 ]; then
        args+=(--node-id "$WAS1_NODE_ID" --with-admin --admin-node-id "$ADMIN_NODE_ID" --admin-domain "$ADMIN_DOMAIN"
               --api-jar application/roboyam-api.jar --admin-jar application/roboyam-admin.jar --db-host "$DB_LISTEN_IP")
        local c; for c in $ADMIN_ALLOW; do args+=(--admin-allow "$c"); done
        # 이미지 저장소 — WAS1 이 공통 환경 파일을 만들 때 쓴다(WAS2 는 그 파일을 복사해 간다)
        storage_args args
        [ -n "$WAS1_SWAP" ] && args+=(--swap "$WAS1_SWAP")
        # shellcheck disable=SC2206
        [ -n "$WAS1_EXTRA_ARGS" ] && args+=($WAS1_EXTRA_ARGS)
    else
        args+=(--node-id "$WAS2_NODE_ID" --api-jar application/roboyam-api.jar)
        # dry-run 은 공통 환경 파일을 보내지 않는다(키가 담겨 있다) — 대신 WAS1 과 같은 값으로 계획만 보게 DB·저장소 값을 준다
        if [ "$DRY_RUN" -eq 0 ]; then
            args+=(--shared-env roboyam.env)
        else
            args+=(--db-host "$DB_LISTEN_IP")
            storage_args args
        fi
        [ -n "$WAS2_SWAP" ] && args+=(--swap "$WAS2_SWAP")
        # shellcheck disable=SC2206
        [ -n "$WAS2_EXTRA_ARGS" ] && args+=($WAS2_EXTRA_ARGS)
    fi
    printf '%s\n' "${args[@]}"
}

run_remote_install() {   # <was1|was2> <호스트>
    local which="$1" host="$2" args=()
    mapfile -t args < <(was_install_args "$which")
    local cmd="cd ~/${REMOTE_DIR_NAME} && sudo -n bash app/roboyam-app-install.sh $(quote_args "${args[@]}")"
    if [ "$DRY_RUN" -eq 1 ]; then
        rssh "$host" "${cmd} --dry-run" < /dev/null
    else
        # 비밀번호는 명령줄이 아니라 표준 입력으로 넘긴다(프로세스 목록·셸 기록에 남지 않게). WAS1 에 S3 접근 키를 주면 둘째 줄이 S3 비밀 키
        # WAS2 는 WAS1 의 공통 환경 파일을 복사해 쓰므로 비밀번호가 필요 없다 — --only was2 면 묻지 않아 비어 있다
        if [ "$which" = was1 ] && [ "$STORAGE" != local ] && [ -n "$S3_ACCESS_KEY" ]; then
            printf '%s\n%s\n' "${DB_PASSWORD:-}" "${S3_SECRET:-}" | rssh "$host" "${cmd} --password-stdin"
        elif [ "$which" = was2 ]; then
            rssh "$host" "$cmd" < /dev/null
        else
            printf '%s\n' "${DB_PASSWORD:-}" | rssh "$host" "${cmd} --password-stdin"
        fi
    fi
}

step_was1() {
    step "[2/3] WAS1 ${WAS1} — api + admin"
    send_bundle "$WAS1"
    run_remote_install was1 "$WAS1"
    cleanup_remote
}

fetch_shared_env() {
    SHARED_ENV_CONTENT="$(rssh "$WAS1" 'sudo -n cat /etc/roboyam/roboyam.env' < /dev/null)" ||
        fail "WAS1 의 /etc/roboyam/roboyam.env 를 읽지 못했습니다(WAS1 설치가 먼저다)."
    grep -q '^ROBOYAM_CRYPTO_KEY=.' <<<"$SHARED_ENV_CONTENT" || fail "WAS1 의 roboyam.env 에 ROBOYAM_CRYPTO_KEY 가 없습니다."
}

step_was2() {
    [ -n "$WAS2" ] || { log "WAS2 없음 — 앱 서버 1대 구성"; return 0; }
    step "[3/3] WAS2 ${WAS2} — api"
    send_bundle "$WAS2"
    if [ "$DRY_RUN" -eq 0 ]; then
        fetch_shared_env
        # 공통 환경 파일은 이 서버 디스크에 쓰지 않고 바로 WAS2 의 임시 폴더로 보낸다(설치 뒤 폴더째 지운다)
        printf '%s\n' "$SHARED_ENV_CONTENT" | rssh "$WAS2" "umask 077; cat > ~/${REMOTE_DIR_NAME}/roboyam.env"
    fi
    run_remote_install was2 "$WAS2"
    cleanup_remote
}

check_db_batches() {
    local n
    n="$(runuser -u postgres -- "/usr/pgsql-18/bin/psql" -X -d "$DB_NAME" -tAc "SELECT count(*) FROM cron.job" 2>/dev/null || echo "?")"
    if [ "$n" = "?" ] || [ "$n" = "0" ]; then
        warn "pg_cron 에 등록된 DB 배치가 ${n}개입니다. 관리자 앱이 떴는지(WAS1: journalctl -u roboyam-admin) 확인하세요."
    else
        log "DB 배치 ${n}개 등록됨 (cron.job)"
    fi
}

# --deploy — 배포: 한 대씩, 실패하면 그 자리에서 멈춘다(그 서버는 roboyam-deploy 가 이전 jar 로 되돌린다)
remote_deploy() {   # <호스트> <api|admin>
    local host="$1" app="$2"
    log "${host} ${app} 배포"
    rssh "$host" "cd ~/${REMOTE_DIR_NAME} && sudo -n bash app/roboyam-deploy.sh ${app} application/roboyam-${app}.jar" < /dev/null ||
        fail "${host} 의 ${app} 배포가 실패했습니다(그 서버는 이전 jar 로 되돌렸다). 다음 서버는 건드리지 않았습니다."
}

run_deploy() {
    if [ "$DEPLOY_BACKUP" -eq 1 ] && [ -x /opt/roboyam/db/bin/roboyam-db-backup.sh ]; then
        step "배포 전 DB 백업"
        /opt/roboyam/db/bin/roboyam-db-backup.sh || fail "DB 백업 실패 — 배포하지 않았습니다(--no-backup 으로 건너뛸 수 있다)."
    fi
    step "WAS1 ${WAS1} — admin → api"
    send_bundle "$WAS1"
    remote_deploy "$WAS1" admin
    remote_deploy "$WAS1" api
    cleanup_remote
    if [ -n "$WAS2" ]; then
        step "WAS2 ${WAS2} — api"
        send_bundle "$WAS2"
        remote_deploy "$WAS2" api
        cleanup_remote
    fi
    check_db_batches
}

# --reset-data — 시험 서버용: DB 를 비워 설치 마법사 전 상태로 되돌린다(앱 설정 파일·키·설치 토큰은 그대로)
PG_MAJOR=18
PG_PSQL="/usr/pgsql-${PG_MAJOR}/bin/psql"
RESET_TIMEOUT=600   # 빈 DB 라 Flyway 가 표를 처음부터 만든다 — 처음 설치만큼 걸린다

pg() { runuser -u postgres -- "$PG_PSQL" -X -q -v ON_ERROR_STOP=1 "$@"; }

confirm_reset() {
    [ "$DRY_RUN" -eq 1 ] && return 0
    echo
    echo "   ⚠ DB ${DB_NAME} 의 데이터(회원·글·설정·DB 배치)를 모두 지우고 빈 DB 로 다시 만듭니다. 되돌리려면 백업에서 복원해야 합니다."
    echo "     앱 설정 파일(/etc/roboyam — 키·설치 토큰)과 버킷에 올라간 이미지는 그대로 둡니다."
    local ans=""
    read -r -p "   계속하려면 DB 이름(${DB_NAME})을 입력하세요: " ans
    [ "$ans" = "$DB_NAME" ] || fail "취소했습니다."
}

remote_systemctl() {   # <호스트> <stop|start> <서비스...>
    local host="$1" action="$2"; shift 2
    log "${host} : systemctl ${action} $*"
    rssh "$host" "sudo -n systemctl ${action} $*" < /dev/null || fail "${host} 에서 systemctl ${action} $* 이 실패했습니다."
}

# 앱 서버에서 /etc/roboyam/<앱>.env 의 SERVER_PORT 로 readiness 가 200 이 될 때까지 기다린다
wait_remote_ready() {   # <호스트> <api|admin>
    local host="$1" app="$2"
    log "${host} ${app} readiness 기다림 (최대 ${RESET_TIMEOUT}초)"
    rssh "$host" "sudo -n bash -s -- ${app} ${RESET_TIMEOUT}" <<'REMOTE' || fail "${host} 의 ${app} 이 ${RESET_TIMEOUT}초 안에 뜨지 않았습니다. 그 서버에서: sudo journalctl -u roboyam-${app} -n 200 --no-pager"
app="$1"; timeout="$2"
port="$(sed -n 's/^SERVER_PORT=//p' "/etc/roboyam/${app}.env")"
[ -n "$port" ] || { echo "/etc/roboyam/${app}.env 에 SERVER_PORT 가 없습니다" >&2; exit 2; }
end=$(( $(date +%s) + timeout ))
while [ "$(date +%s)" -lt "$end" ]; do
    [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:${port}/actuator/health/readiness" || true)" = 200 ] && exit 0
    systemctl is-failed --quiet "roboyam-${app}" && { echo "roboyam-${app} 이 멈췄습니다(failed)" >&2; exit 1; }
    sleep 5
done
exit 1
REMOTE
    log "${host} ${app} 준비됨"
}

# DB 를 지우고 같은 로캘·소유자로 다시 만든다 — 확장·권한은 db/roboyam-db-install.sh 의 create_database·create_extensions 와 같게 맞춘다
recreate_database() {
    step "DB ${DB_NAME} 다시 만들기"
    [ -x "$PG_PSQL" ] || fail "${PG_PSQL} 이 없습니다 — 이 스크립트는 DB 서버에서 실행합니다."
    [ "$(pg -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'")" = 1 ] ||
        fail "DB 계정 ${DB_USER} 이 없습니다 — 처음 설치(sudo ./roboyam-install.sh)를 먼저 하세요."
    local loc preload
    loc="$(pg -d postgres -tAc "SELECT datcollate FROM pg_database WHERE datname = '${DB_NAME}'")"
    [ -n "$loc" ] || loc="$(pg -d postgres -tAc "SELECT datcollate FROM pg_database WHERE datname = 'template0'")"
    preload="$(pg -d postgres -tAc "SHOW shared_preload_libraries")"
    log "지우기: ${DB_NAME}"
    pg -d postgres -c "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE);"
    log "만들기: ${DB_NAME} (UTF8 · ${loc} · 소유자 ${DB_USER})"
    pg -d postgres -c "CREATE DATABASE ${DB_NAME} WITH OWNER = ${DB_USER} ENCODING = 'UTF8' LOCALE_PROVIDER = libc LC_COLLATE = '${loc}' LC_CTYPE = '${loc}' TEMPLATE = template0;"
    pg -d postgres -c "REVOKE ALL ON DATABASE ${DB_NAME} FROM PUBLIC; GRANT CONNECT, TEMPORARY ON DATABASE ${DB_NAME} TO ${DB_USER};"
    local ts_sql=""
    [[ ",${preload// /}," == *",timescaledb,"* ]] && ts_sql="CREATE EXTENSION IF NOT EXISTS timescaledb;"
    pg -d "$DB_NAME" <<SQL
CREATE EXTENSION IF NOT EXISTS pg_cron;
GRANT USAGE ON SCHEMA cron TO ${DB_USER};
GRANT pg_read_all_settings TO ${DB_USER};
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
${ts_sql}
SQL
    # pg_cron 의 배치 실행기는 cron.database_name 의 DB 에 붙어 있다 — DB 를 새로 만들었으니 다시 띄운다
    log "PostgreSQL 재시작 (pg_cron 실행기를 새 DB 에 다시 붙인다)"
    systemctl restart "postgresql-${PG_MAJOR}"
    local i
    for i in $(seq 1 30); do
        runuser -u postgres -- "/usr/pgsql-${PG_MAJOR}/bin/pg_isready" -q && break
        sleep 1
    done
    runuser -u postgres -- "/usr/pgsql-${PG_MAJOR}/bin/pg_isready" -q || fail "PostgreSQL 이 다시 뜨지 않았습니다: journalctl -u postgresql-${PG_MAJOR}"
    log "DB ${DB_NAME} 준비됨 (빈 DB — 앱이 뜨면서 Flyway 가 표를 만든다)"
}

run_reset() {
    step "앱 멈추기"
    if [ -n "$WAS2" ]; then remote_systemctl "$WAS2" stop roboyam-api; fi
    remote_systemctl "$WAS1" stop roboyam-api roboyam-admin
    if [ "$DEPLOY_BACKUP" -eq 1 ] && [ -x /opt/roboyam/db/bin/roboyam-db-backup.sh ]; then
        step "지우기 전 DB 백업"
        /opt/roboyam/db/bin/roboyam-db-backup.sh || fail "DB 백업 실패 — 지우지 않았습니다(앱은 멈춘 상태다. --no-backup 으로 건너뛸 수 있다)."
    fi
    recreate_database
    step "앱 띄우기 — WAS1 api → admin$([ -n "$WAS2" ] && echo ' → WAS2 api')"
    remote_systemctl "$WAS1" start roboyam-api
    wait_remote_ready "$WAS1" api
    remote_systemctl "$WAS1" start roboyam-admin
    wait_remote_ready "$WAS1" admin
    if [ -n "$WAS2" ]; then
        remote_systemctl "$WAS2" start roboyam-api
        wait_remote_ready "$WAS2" api
    fi
    check_db_batches
    SHARED_ENV_CONTENT="$(rssh "$WAS1" 'sudo -n cat /etc/roboyam/roboyam.env' < /dev/null 2>/dev/null || true)"
}

mode_label() {   # [long]
    case "$MODE" in
        deploy) [ "${1:-}" = long ] && echo '배포(jar 교체·재기동)' || echo 배포 ;;
        reset)  [ "${1:-}" = long ] && echo '데이터 초기화(DB 를 비우고 설치 마법사 전 상태로)' || echo 데이터 초기화 ;;
        *)      [ "${1:-}" = long ] && echo "설치${ONLY:+ — ${ONLY} 만}" || echo 설치 ;;
    esac
}

print_plan() {
    step "계획"
    printf '   %-14s %s\n' "모드"    "$(mode_label long)$([ "$DRY_RUN" -eq 1 ] && echo ' · dry-run')"
    printf '   %-14s %s\n' "DB(이 서버)" "${DB_LISTEN_IP}:5432/${DB_NAME} (계정 ${DB_USER})"
    printf '   %-14s %s\n' "WAS1"    "${WAS1} — api(NODE_ID ${WAS1_NODE_ID}) + admin(NODE_ID ${ADMIN_NODE_ID})"
    printf '   %-14s %s\n' "WAS2"    "${WAS2:-없음}${WAS2:+ — api(NODE_ID ${WAS2_NODE_ID})}"
    printf '   %-14s %s\n' "공개 사이트" "${DOMAINS}"
    printf '   %-14s %s\n' "관리자 앱"   "${ADMIN_DOMAIN}${ADMIN_ALLOW:+ (방문자 ${ADMIN_ALLOW} 만)}"
    if [ "$STORAGE" = r2 ]; then
        printf '   %-14s %s\n' "이미지"  "R2 버킷 ${S3_BUCKET} (${S3_ENDPOINT:-계정 ${R2_ACCOUNT_ID}})${S3_PUBLIC_URL:+ — 공개 주소 ${S3_PUBLIC_URL}}"
    elif [ "$STORAGE" = s3 ]; then
        printf '   %-14s %s\n' "이미지"  "S3 버킷 ${S3_BUCKET} (${S3_ENDPOINT:-AWS S3}, ${S3_REGION})${S3_PUBLIC_URL:+ — 공개 주소 ${S3_PUBLIC_URL}}"
    else
        printf '   %-14s %s\n' "이미지"  "앱 서버 로컬 디스크(서버 한 대 구성에서만)"
    fi
    printf '   %-14s %s\n' "jar"     "$(basename "$API_JAR") $(du -h "$API_JAR" | cut -f1) · $(basename "$ADMIN_JAR") $(du -h "$ADMIN_JAR" | cut -f1)"
    printf '   %-14s %s\n' "SSH"     "${SSH_USER}@ (키 ${SSH_KEY:-ssh-agent})"
}

confirm() {
    [ "$ASSUME_YES" -eq 1 ] || [ "$DRY_RUN" -eq 1 ] && return 0
    local ans=""
    read -r -p "   진행할까요? [y/N] " ans
    [[ "$ans" =~ ^[Yy]$ ]] || fail "취소했습니다."
}

print_result() {
    echo
    echo "============================================================"
    echo " 완료 — $(mode_label)"
    echo "============================================================"
    echo "   로그          : ${LOG_FILE}"
    echo "   배포(다음부터): 새 묶음을 이 서버에 풀고  sudo ./roboyam-install.sh --deploy"
    if [ "$MODE" != deploy ] && [ -n "${SHARED_ENV_CONTENT:-}" ]; then
        echo "   설치 마법사   : https://$(awk '{print $1}' <<<"$DOMAINS")/install  토큰 $(sed -n 's/^ROBOYAM_INSTALL_TOKEN=//p' <<<"$SHARED_ENV_CONTENT")"
    fi
    if [ "$MODE" = install ]; then
        echo
        echo "   ⚠ 키 백업: WAS1 의 /etc/roboyam/roboyam.env 를 서버 밖 안전한 곳에 보관하세요"
        echo "     (ssh ${SSH_USER}@${WAS1} sudo cat /etc/roboyam/roboyam.env). ROBOYAM_DATA_KEY 를 잃으면 복구할 수 없습니다."
    fi
}

main() {
    parse_args "$@"
    require_root
    check_os
    load_config
    check_package

    DB_LISTEN_IP="${DB_PRIVATE_IP:-$(detect_private_ip)}"
    [ -n "$DB_LISTEN_IP" ] || fail "이 서버의 사설 IP 를 찾지 못했습니다(install.conf 의 DB_PRIVATE_IP)."

    install -m 600 /dev/null "$LOG_FILE"   # 설치 토큰이 찍히므로 root 만 읽게
    exec > >(tee -a "$LOG_FILE") 2>&1
    echo "============================================================"
    echo " 로보얌 원스톱 $(mode_label) — DB 서버에서 앱 서버로"
    echo "============================================================"

    step "앱 서버 접속 확인"
    check_remote WAS1 "$WAS1"
    if [ -n "$WAS2" ]; then check_remote WAS2 "$WAS2"; fi
    print_plan
    confirm

    if [ "$MODE" = deploy ]; then
        [ "$DRY_RUN" -eq 0 ] || { log "dry-run — 배포는 접속 확인까지만 합니다."; exit 0; }
        run_deploy
        print_result
        exit 0
    fi
    if [ "$MODE" = reset ]; then
        [ "$DRY_RUN" -eq 0 ] || { log "dry-run — 데이터 초기화는 접속 확인까지만 합니다."; exit 0; }
        confirm_reset
        run_reset
        print_result
        exit 0
    fi

    # 주의: 「조건 && 함수」로 부르면 bash 가 그 함수 안의 오류를 무시한다(set -e 가 꺼진다) — 단계는 if 로 부른다
    if [ "$DRY_RUN" -eq 0 ] && want db was1; then
        input_db_password
    fi
    if [ "$DRY_RUN" -eq 0 ] && want was1; then
        input_s3_secret
    fi
    if want db; then step_db; fi
    if want was1; then
        step_was1
        if [ "$DRY_RUN" -eq 0 ]; then check_db_batches; fi
    fi
    if want was2; then step_was2; fi
    if [ "$DRY_RUN" -eq 0 ] && [ -z "${SHARED_ENV_CONTENT:-}" ]; then
        SHARED_ENV_CONTENT="$(rssh "$WAS1" 'sudo -n cat /etc/roboyam/roboyam.env' < /dev/null 2>/dev/null || true)"
    fi
    DB_PASSWORD=""; S3_SECRET=""; unset DB_PASSWORD S3_SECRET
    print_result
}

main "$@"
