#!/usr/bin/env bash
# =============================================================================
# roboyam-install.sh — ROBOYAM 설치형 설치 (Docker Compose: postgres + app)
# =============================================================================
#
# 설치 폴더를 만들고, 비밀(.env)을 만들고, 앱 이미지를 빌드해 컨테이너 둘(postgres, app)을 띄운다.
#
# 비밀은 사람이 입력하지 않는다. 설치기가 무작위로 만든다:
#   - 암호화 키(ROBOYAM_CRYPTO_KEY, AES-256 용 32바이트)
#   - DB 비밀번호 — 앱 이미지 안의 SecretCipherTool 로 위 키로 암호화해 .env 에 DB_PASSWORD=ENC(...) 로 둔다.
#     앱은 기동 때 이 암호문을 복호화해 데이터소스에 쓴다.
#   - postgres 컨테이너는 평문이 필요해서 같은 비밀번호를 secrets/db_password(권한 600)로 둔다.
#   - 설치 토큰(ROBOYAM_INSTALL_TOKEN, 무작위 24자) — 브라우저 /install 첫 화면에 입력한다. 환경 변수 ROBOYAM_INSTALL_TOKEN 으로 미리 주면 그 값을 쓴다.
#
# **실패하면 이 실행이 만든 것을 모두 지운다**(컨테이너·볼륨(데이터)·네트워크·이미지·설치 폴더·.env).
# 단계 어디서든, Ctrl-C 로 끊어도 같다. 이미 있던 자원은 건드리지 않는다.
#
# 사용법
#   ./roboyam-install.sh --dir /opt/roboyam --jar server/api/build/libs/roboyam-api.jar
#
# 옵션
#   --dir <폴더>          설치 폴더(기본 /opt/roboyam). 없으면 만든다. 쓸 수 없으면 오류
#   --project <이름>      compose 프로젝트 · 컨테이너/볼륨 이름 접두어(기본 roboyam). 소문자·숫자·-_
#   --jar <경로>          앱 jar(기본: 패키지의 app/roboyam-api.jar, 없으면 server/api/build/libs/roboyam-api.jar)
#   --image <이미지>      jar 대신 이미 있는 앱 이미지를 쓴다(빌드하지 않고, 지우지도 않는다)
#   --base-image <이미지> jar 로 빌드할 때의 베이스 이미지(기본 eclipse-temurin:25-jre)
#   --public-url <주소>   앱의 PUBLIC_URL (기본 http://localhost:<http-port>). http(s)://호스트[:포트], 끝 / 없음
#   --http-port <포트>    바깥에 여는 포트(기본 8910)
#   --bind <주소>         바깥 포트를 여는 주소(기본 127.0.0.1). 0.0.0.0 은 바깥에 열린다(경고 — 앞단 HTTPS 필요)
#   --non-interactive     묻지 않고 진행(자동화용)
#   --yes                 위험한 확인(--reinstall 의 삭제)을 생략
#   --reinstall           기존 설치가 있으면 확인 후 지우고 새로 설치
#   --startup-timeout <초> 앱이 뜨기를 기다리는 제한 시간(기본 180)
#   --version             버전
#   --help                이 도움말
#
# 단계: [1/6] 사전 점검  [2/6] 폴더·비밀  [3/6] compose 파일  [4/6] 앱 이미지·암호화  [5/6] 기동·대기  [6/6] 요약
# =============================================================================
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/common.sh
. "$SCRIPT_DIR/lib/common.sh"

# --- 기본값 --------------------------------------------------------------------
DIR="/opt/roboyam"
PROJECT="roboyam"
JAR=""
IMAGE_OPT=""
BASE_IMAGE="eclipse-temurin:25-jre"
PUBLIC_URL_OPT=""
HTTP_PORT=8910
BIND="127.0.0.1"
NONINTERACTIVE=0
ASSUME_YES=0
REINSTALL=0
STARTUP_TIMEOUT=180
DB_WAIT=120

usage() { sed -n '/^# 사용법/,/^# 단계/p' "$0" | sed 's/^# \{0,1\}//'; }

usage_error() {
    printf '%s\n' "오류: $*" >&2
    printf '%s\n' "도움말: $0 --help" >&2
    exit 2
}

need_arg() { if [ $# -lt 2 ] || [ -z "$2" ]; then usage_error "$1 에 값이 필요합니다"; fi; }

while [ $# -gt 0 ]; do
    case "$1" in
        --*=*) set -- "${1%%=*}" "${1#*=}" "${@:2}" ;;
    esac
    case "$1" in
        --dir)             need_arg "$@"; DIR=$2; shift 2 ;;
        --project)         need_arg "$@"; PROJECT=$2; shift 2 ;;
        --jar)             need_arg "$@"; JAR=$2; shift 2 ;;
        --image)           need_arg "$@"; IMAGE_OPT=$2; shift 2 ;;
        --base-image)      need_arg "$@"; BASE_IMAGE=$2; shift 2 ;;
        --public-url)      need_arg "$@"; PUBLIC_URL_OPT=$2; shift 2 ;;
        --http-port)       need_arg "$@"; HTTP_PORT=$2; shift 2 ;;
        --bind)            need_arg "$@"; BIND=$2; shift 2 ;;
        --startup-timeout) need_arg "$@"; STARTUP_TIMEOUT=$2; shift 2 ;;
        --non-interactive) NONINTERACTIVE=1; shift ;;
        --yes|-y)          ASSUME_YES=1; shift ;;
        --reinstall)       REINSTALL=1; shift ;;
        --version)         echo "roboyam-install $INSTALLER_VERSION"; exit 0 ;;
        --help|-h)         usage; exit 0 ;;
        *)                 usage_error "알 수 없는 옵션: $1" ;;
    esac
done

# --- 옵션 검증(시스템을 건드리기 전) ----------------------------------------------
RE_PROJECT='^[a-z0-9][a-z0-9_-]{0,30}$'
RE_IPV4='^[0-9]{1,3}(\.[0-9]{1,3}){3}$'
RE_URL='^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$'
RE_INT='^[0-9]+$'

[[ $PROJECT =~ $RE_PROJECT ]] || usage_error "--project 는 소문자·숫자·-_ 로 31자 이내여야 합니다: $PROJECT"
[[ $HTTP_PORT =~ $RE_INT ]] && [ "$HTTP_PORT" -ge 1 ] && [ "$HTTP_PORT" -le 65535 ] || usage_error "--http-port 는 1~65535 입니다: $HTTP_PORT"
[[ $BIND =~ $RE_IPV4 ]] || usage_error "--bind 는 IPv4 주소여야 합니다(127.0.0.1 · 0.0.0.0 …): $BIND"
[[ $STARTUP_TIMEOUT =~ $RE_INT ]] && [ "$STARTUP_TIMEOUT" -ge 1 ] || usage_error "--startup-timeout 은 1 이상의 초입니다: $STARTUP_TIMEOUT"
if [ -n "$PUBLIC_URL_OPT" ]; then
    [[ $PUBLIC_URL_OPT =~ $RE_URL ]] || usage_error "--public-url 은 http(s)://호스트[:포트] 꼴이고 끝에 / 가 없어야 합니다: $PUBLIC_URL_OPT"
fi
if [ -n "$JAR" ] && [ -n "$IMAGE_OPT" ]; then usage_error "--jar 와 --image 는 함께 쓸 수 없습니다"; fi

PUBLIC_URL=${PUBLIC_URL_OPT:-http://localhost:$HTTP_PORT}
DIR=$(abs_path "$DIR")

# 이 설치가 만들 자원의 이름(전부 프로젝트 이름으로 시작한다)
C_PG="$PROJECT-postgres"
C_APP="$PROJECT-app"
V_PG="$PROJECT-pgdata"
N_NET="$PROJECT-net"
PG_IMAGE_TAG="$PROJECT-postgres:18"   # 공식 postgres:18 + pg_cron(DB 배치용). 4단계에서 templates/postgres/Dockerfile 로 만든다

STAMP=$(date +%Y%m%d-%H%M%S)
RUN_ID="r-$STAMP-$(openssl rand -hex 4 2> /dev/null || printf '%s' "$$")"
IMAGE_TAG=""                       # 4단계에서 정한다(--image 이면 그 이미지)
BUILD_IMAGE=0                      # 1 이면 jar 로 이 실행이 이미지를 만든다
PROBE_HOST="$BIND"
[ "$BIND" = "0.0.0.0" ] && PROBE_HOST="127.0.0.1"

LOG_FILE="${TMPDIR:-/tmp}/roboyam-install-$STAMP.log"
( umask 077; : >> "$LOG_FILE" ) 2> /dev/null || LOG_FILE=""

# --- 실패 시 전체 삭제(트랩) -------------------------------------------------------
INSTALL_OK=0
CLEANUP_ARMED=0
CHILD_KILLED=0
TEMP_MANIFEST=""
HAVE_OLD=0
OLD_RUN_ID=""
OLD_PROJECT=""
OLD_MANIFEST=""

on_err() {
    [ "${BASH_SUBSHELL:-0}" -eq 0 ] || return 0
    FAIL_LINE=$1
    FAIL_CMD=$2
}

on_signal() {   # <신호 이름> <종료 코드>
    trap '' INT TERM HUP
    FAIL_MSG="중단 신호($1)를 받았습니다"
    say ""
    say "${C_YELLOW}중단 신호($1) — 하던 일을 멈추고 이 실행이 만든 것을 정리합니다${C_OFF}"
    if [ -n "$CUR_PID" ]; then
        kill "$CUR_PID" 2> /dev/null || true
        wait "$CUR_PID" 2> /dev/null || true
        CHILD_KILLED=1
        CUR_PID=""
    fi
    exit "$2"
}

cleanup_after_failure() {
    say ""
    say "${C_RED}${C_BOLD}설치 실패${C_OFF} — 단계 [$STEP_NO/$TOTAL_STEPS] $STEP_NAME"
    if [ -n "$FAIL_MSG" ]; then
        say "   원인: $FAIL_MSG"
    elif [ -n "${FAIL_LINE:-}" ]; then
        say "   원인: 예기치 못한 오류 (스크립트 ${FAIL_LINE}행: ${FAIL_CMD:-?})"
    fi
    say "   이 실행이 만든 환경·데이터를 모두 지웁니다(이미 있던 자원은 건드리지 않습니다)."
    CLEAN_MODE=run; CLEAN_RUN_ID=$RUN_ID; CLEAN_PROJECT=$PROJECT
    cleanup_collect run
    CLEAN_KEPT=$T_KEPT
    CLEAN_REMOVED=""; CLEAN_FAILED=0
    cleanup_execute
    if [ "$CHILD_KILLED" = 1 ]; then
        # 죽인 docker 명령을 데몬이 마저 처리하는 동안 만들어진 것이 있을 수 있어 한 번 더 훑는다
        sleep 2
        cleanup_collect run
        cleanup_execute
    fi
    cleanup_verify
    cleanup_report || true
    if [ -n "$CLEAN_LEFT" ] || [ "$CLEAN_FAILED" -gt 0 ]; then
        [ -f "$MANIFEST" ] && manifest_add status cleanup-incomplete || true
    fi
    pn=$(pulled_note)
    if [ -n "$pn" ]; then say "   남겨 둔 것: $pn"; fi
    if [ -n "$LOG_FILE" ]; then say "   진단 로그: $LOG_FILE (비밀은 기록하지 않습니다)"; fi
}

on_exit() {
    local rc=$?
    trap - ERR
    trap '' INT TERM HUP
    set +e
    if [ "$INSTALL_OK" != 1 ] && [ "$CLEANUP_ARMED" = 1 ]; then
        [ "$rc" -eq 0 ] && rc=1
        cleanup_after_failure
    fi
    exit "$rc"
}

trap 'on_err "$LINENO" "$BASH_COMMAND"' ERR
trap on_exit EXIT
trap 'on_signal INT 130' INT
trap 'on_signal TERM 143' TERM
trap 'on_signal HUP 129' HUP
trap '' PIPE   # 화면 출력이 닫혀도 설치를 죽이지 않는다(터미널이 닫히면 HUP 가 따로 온다)

# --- 보조 함수 -----------------------------------------------------------------
# compose 는 셸에 export 된 환경 변수를 .env 보다 먼저 쓴다. 개발자 셸에 DB_PASSWORD·PUBLIC_URL 같은 이름이 있으면
# 설치 값이 조용히 바뀌므로, 템플릿이 읽는 변수 이름은 compose 를 부르는 서브셸에서 모두 비운다.
COMPOSE_VARS=$(grep -o '\${[A-Za-z_][A-Za-z_0-9]*' "$SCRIPT_DIR/templates/compose.yml" 2> /dev/null | sed 's/^\${//' | sort -u || true)
compose_unset_ambient() {
    local v
    for v in $COMPOSE_VARS; do unset "$v"; done
}

compose_cmd() {   # 백그라운드(run_logged)에서 호출된다. exec 로 docker 가 바로 이 프로세스가 되어야 신호로 죽는다
    compose_unset_ambient
    cd "$DIR" && exec docker compose --env-file .env -f compose.yml "$@"
}

build_image_cmd() {
    cd "$DIR/app" && exec docker build --progress=plain \
        --label "$LABEL_PROJECT=$PROJECT" --label "$LABEL_RUN=$RUN_ID" \
        --build-arg "BASE_IMAGE=$BASE_IMAGE" -t "$IMAGE_TAG" .
}

build_pg_image_cmd() {
    cd "$DIR/postgres" && exec docker build --progress=plain \
        --label "$LABEL_PROJECT=$PROJECT" --label "$LABEL_RUN=$RUN_ID" \
        -t "$PG_IMAGE_TAG" .
}

fail_with_logs() {   # <메시지> <컨테이너>
    local msg="$1" c="$2" logs l
    logs=$(container_log_tail "$c" 25)
    if [ -n "$logs" ]; then
        say "   --- $c 로그 끝 25줄(비밀은 *** 로 가림) ---"
        while IFS= read -r l; do say "   | $l"; done <<< "$logs"
        say "   ---"
    fi
    fail "$msg"
}

# 이 실행이 새로 내려받은 공용 베이스 이미지(실패해도 지우지 않는다) 안내
BASE_ABSENT_PG=0
BASE_ABSENT_APP=0
pulled_note() {
    local out=""
    if [ "$BASE_ABSENT_PG" = 1 ] && obj_exists image "postgres:18"; then out="postgres:18"; fi
    if [ "$BASE_ABSENT_APP" = 1 ] && obj_exists image "$BASE_IMAGE"; then out="${out:+$out, }$BASE_IMAGE"; fi
    [ -z "$out" ] || printf '%s' "공용 이미지 $out (이 실행이 내려받았지만 다른 곳에서도 쓸 수 있어 지우지 않는다. 필요 없으면 docker image rm 으로 지운다)"
}

# 기존 매니페스트에서 값을 읽는다(전역 MANIFEST 를 잠시 바꾼다)
old_get() {
    local saved=$MANIFEST v
    MANIFEST=$OLD_MANIFEST
    v=$(manifest_get "$1")
    MANIFEST=$saved
    printf '%s' "$v"
}

# 이 이름·라벨이 옛 설치(재설치로 지울 것)의 것인가
is_old_run() {   # <종류> <이름>
    [ "$HAVE_OLD" = 1 ] && [ "$(obj_label "$1" "$2" "$LABEL_RUN")" = "$OLD_RUN_ID" ]
}

# 만들 자원과 이름이 겹치는 기존 자원을 찾는다 → CONFLICTS
find_conflicts() {
    local n kind
    CONFLICTS=""
    for n in "$C_PG" "$C_APP"; do
        if obj_exists container "$n" && ! is_old_run container "$n"; then list_add CONFLICTS "컨테이너 $n"; fi
    done
    for n in "$V_PG"; do
        if obj_exists volume "$n" && ! is_old_run volume "$n"; then list_add CONFLICTS "볼륨 $n"; fi
    done
    if obj_exists network "$N_NET" && ! is_old_run network "$N_NET"; then list_add CONFLICTS "네트워크 $N_NET"; fi
    if [ "$BUILD_IMAGE" = 1 ] && obj_exists image "$IMAGE_TAG" && ! is_old_run image "$IMAGE_TAG"; then
        list_add CONFLICTS "이미지 $IMAGE_TAG"
    fi
    if obj_exists image "$PG_IMAGE_TAG" && ! is_old_run image "$PG_IMAGE_TAG"; then
        list_add CONFLICTS "이미지 $PG_IMAGE_TAG"
    fi
    # 같은 프로젝트 라벨이 붙은 다른 설치의 흔적(이름은 달라도 같은 프로젝트다)
    while IFS= read -r n; do
        [ -n "$n" ] || continue
        is_old_run container "$n" || list_add CONFLICTS "컨테이너 $n (프로젝트 $PROJECT 의 흔적)"
    done < <(docker ps -a --filter "label=$LABEL_PROJECT=$PROJECT" --format '{{.Names}}' 2> /dev/null | tr -d '\r')
    while IFS= read -r n; do
        [ -n "$n" ] || continue
        is_old_run volume "$n" || list_add CONFLICTS "볼륨 $n (프로젝트 $PROJECT 의 흔적)"
    done < <(docker volume ls --filter "label=$LABEL_PROJECT=$PROJECT" --format '{{.Name}}' 2> /dev/null | tr -d '\r')
    while IFS= read -r n; do
        [ -n "$n" ] || continue
        is_old_run network "$n" || list_add CONFLICTS "네트워크 $n (프로젝트 $PROJECT 의 흔적)"
    done < <(docker network ls --filter "label=$LABEL_PROJECT=$PROJECT" --format '{{.Name}}' 2> /dev/null | tr -d '\r')
}

# 이미지가 로컬에 없으면 내려받는다(재설치에서 옛 설치를 지우기 전에 쓴다). 실패하면 아무것도 지우지 않은 채 멈춘다
ensure_image() {   # <이미지>
    obj_exists image "$1" && return 0
    note "이미지 $1 를 내려받는다(옛 설치를 지우기 전에 확인)"
    run_logged "docker pull $1" docker pull "$1" \
        || fail "이미지 $1 를 받지 못했습니다(상세는 로그: ${LOG_FILE:-없음}). 옛 설치는 아무것도 지우지 않았습니다."
}

check_port_free() {
    if [ "$HAVE_OLD" = 1 ] && [ "$(old_get http_port)" = "$HTTP_PORT" ] && obj_exists container "$(old_get app_container)"; then
        return 0   # 지울 옛 설치의 앱이 쓰는 포트다
    fi
    if port_in_use "$PROBE_HOST" "$HTTP_PORT"; then
        fail "포트 $HTTP_PORT 를 이미 다른 프로그램이 쓰고 있습니다($PROBE_HOST). --http-port 로 다른 포트를 고르세요."
    fi
}

# =============================================================================
# [1/6] 사전 점검 — 아무것도 만들지 않는다
# =============================================================================
say "ROBOYAM 설치 (roboyam-install $INSTALLER_VERSION)  프로젝트=$PROJECT  폴더=$DIR"
step 1 "사전 점검"

command -v docker > /dev/null 2>&1 || fail "docker 를 찾을 수 없습니다. Docker(Engine 또는 Desktop)를 설치하세요."
docker info > /dev/null 2>&1 || fail "docker 데몬에 연결할 수 없습니다. Docker 가 켜져 있는지, 이 계정이 docker 를 쓸 권한(docker 그룹)이 있는지 확인하세요."
docker compose version > /dev/null 2>&1 || fail "docker compose v2 가 필요합니다(docker compose version 이 동작해야 합니다)."
command -v curl > /dev/null 2>&1 || fail "curl 을 찾을 수 없습니다."
command -v openssl > /dev/null 2>&1 || fail "openssl 을 찾을 수 없습니다(무작위 비밀 생성에 씁니다)."
command -v awk > /dev/null 2>&1 || fail "awk 를 찾을 수 없습니다."
note "docker $(docker version --format '{{.Server.Version}}' 2> /dev/null | tr -d '\r'), compose $(docker compose version --short 2> /dev/null | tr -d '\r')"

# 앱: jar 또는 이미지
if [ -n "$IMAGE_OPT" ]; then
    obj_exists image "$IMAGE_OPT" || fail "앱 이미지를 찾을 수 없습니다: $IMAGE_OPT (docker image ls 로 확인. --jar 로 jar 에서 만들 수도 있습니다)"
    IMAGE_TAG=$IMAGE_OPT
    note "앱: 기존 이미지 $IMAGE_TAG (빌드하지 않음)"
else
    BUILD_IMAGE=1
    if [ -z "$JAR" ]; then
        for cand in "$SCRIPT_DIR/app/roboyam-api.jar" "$SCRIPT_DIR/roboyam-api.jar" \
                    "$SCRIPT_DIR/../server/api/build/libs/roboyam-api.jar"; do
            if [ -f "$cand" ]; then JAR=$cand; break; fi
        done
    fi
    [ -n "$JAR" ] || fail "앱 jar 를 찾을 수 없습니다. --jar <경로> 로 지정하세요(빌드: ./gradlew :server:api:bootJar). 이미 이미지가 있으면 --image."
    [ -f "$JAR" ] || fail "jar 파일이 없습니다: $JAR"
    JAR=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")
    [ "$(head -c 2 "$JAR" 2> /dev/null)" = "PK" ] || fail "jar 가 아닙니다(zip 형식이 아님): $JAR"
    jar_size=$(wc -c < "$JAR" | tr -d ' ')
    [ "$jar_size" -ge 1024 ] || fail "jar 가 너무 작습니다(${jar_size}바이트): $JAR"
    IMAGE_TAG="$PROJECT-api:$STAMP"
    note "앱: jar $JAR ($((jar_size / 1024 / 1024))MB) → 이미지 $IMAGE_TAG 를 만든다(베이스 $BASE_IMAGE)"
fi

# 기존 설치(매니페스트)
OLD_MANIFEST="$DIR/$MANIFEST_NAME"
if [ -f "$OLD_MANIFEST" ]; then
    old_status=$(old_get status)
    OLD_PROJECT=$(old_get project)
    OLD_RUN_ID=$(old_get run_id)
    if [ "$REINSTALL" != 1 ]; then
        if [ "$old_status" = "done" ]; then
            fail "이 폴더에 이미 설치가 있습니다(프로젝트 ${OLD_PROJECT:-?}). 지우고 새로 설치하려면 --reinstall, 제거만 하려면 roboyam-uninstall.sh --dir $DIR. 기존 설치는 건드리지 않았습니다."
        fi
        fail "이 폴더에 끝나지 않은 설치의 흔적이 있습니다(상태 ${old_status:-?}, 프로젝트 ${OLD_PROJECT:-?}). roboyam-uninstall.sh --dir $DIR 로 정리하거나 --reinstall 로 지우고 새로 설치하세요."
    fi
    [ -n "$OLD_RUN_ID" ] || fail "기존 매니페스트에 run_id 가 없어 안전하게 지울 수 없습니다: $OLD_MANIFEST (roboyam-uninstall.sh --project 로 정리하세요)"
    HAVE_OLD=1
    note "기존 설치 발견(상태 ${old_status:-?}, 프로젝트 ${OLD_PROJECT:-?}) — --reinstall 이라 확인 뒤 지우고 새로 설치한다"
elif [ -e "$DIR" ]; then
    if [ ! -d "$DIR" ]; then fail "설치 폴더 자리에 파일이 있습니다: $DIR"; fi
    if [ -n "$(ls -A "$DIR" 2> /dev/null)" ]; then
        fail "설치 폴더가 비어 있지 않고 설치 기록(.install-manifest)도 없습니다: $DIR — 다른 --dir 를 쓰거나 폴더를 비우세요. 아무것도 건드리지 않았습니다."
    fi
fi
if [ "$REINSTALL" = 1 ] && [ "$HAVE_OLD" = 0 ]; then
    note "--reinstall: 지울 기존 설치가 없다(새로 설치한다)"
fi

# 설치 폴더를 만들거나 쓸 수 있는가
existing=$(nearest_existing_dir "$DIR")
{ [ -d "$existing" ] && [ -w "$existing" ] && [ -x "$existing" ]; } \
    || fail "설치 폴더를 만들 수 없습니다: $DIR ($existing 에 쓸 권한이 없습니다). 권한이 있는 계정(sudo)으로 실행하거나 --dir 로 쓸 수 있는 폴더를 지정하세요."
note "설치 폴더: $DIR (쓸 수 있음)"

# 디스크
free_kb=$(df -Pk "$existing" 2> /dev/null | awk 'NR == 2 { print $4 }')
if [ -n "$free_kb" ] && [ "$free_kb" -lt 524288 ]; then
    fail "설치 폴더의 빈 공간이 부족합니다($((free_kb / 1024))MB, 최소 512MB). 다른 --dir 를 쓰세요."
fi
docker_root=$(docker info --format '{{.DockerRootDir}}' 2> /dev/null | tr -d '\r' || true)
if [ -n "$docker_root" ] && [ -d "$docker_root" ]; then
    dfree_kb=$(df -Pk "$docker_root" 2> /dev/null | awk 'NR == 2 { print $4 }')
    if [ -n "$dfree_kb" ] && [ "$dfree_kb" -lt 2097152 ]; then
        fail "Docker 저장소($docker_root)의 빈 공간이 부족합니다($((dfree_kb / 1024))MB, 최소 2GB 권장)."
    fi
    note "디스크: 설치 폴더 $((${free_kb:-0} / 1024))MB, Docker 저장소 $((${dfree_kb:-0} / 1024))MB 여유"
else
    note "디스크: 설치 폴더 $((${free_kb:-0} / 1024))MB 여유 (Docker 저장소 용량은 이 환경에서 확인할 수 없음 — Docker Desktop 등)"
fi

# 포트
check_port_free
note "포트: $BIND:$HTTP_PORT 사용 가능"

# 이름 충돌 · 같은 프로젝트의 흔적 — 만들기 전에 거부한다
find_conflicts
if [ -n "$CONFLICTS" ]; then
    errp '%s\n' "${C_RED}이미 있는 자원과 이름이 겹칩니다(아무것도 만들지 않았고, 이 자원들은 건드리지 않았습니다):${C_OFF}"
    while IFS= read -r n; do errp '   - %s\n' "$n"; done <<< "$CONFLICTS"
    fail "다른 --project 이름을 쓰거나, 이 자원이 이전 설치의 흔적이면 roboyam-uninstall.sh --project $PROJECT 로 먼저 정리하세요."
fi
note "이름 충돌 없음: 컨테이너 $C_PG $C_APP / 볼륨 $V_PG / 네트워크 $N_NET"

if [ "$BIND" != "127.0.0.1" ]; then
    warn "--bind $BIND: 앱 포트가 이 서버 바깥에 열립니다. 요청이 평문 HTTP 로 오갑니다 — 앞단에 HTTPS 리버스 프록시(Caddy·nginx·ALB)를 두고, 설치가 끝날 때까지는 신뢰할 수 있는 망에서만 여세요."
fi
case "$PUBLIC_URL" in
    http://localhost*|http://127.0.0.1*) ;;
    http://*) warn "PUBLIC_URL 이 http:// 입니다($PUBLIC_URL). 세션 쿠키의 Secure 를 끕니다. 운영에서는 HTTPS 주소를 쓰세요." ;;
esac
if [ "$BIND" = "0.0.0.0" ] && [ -z "$PUBLIC_URL_OPT" ]; then
    warn "--public-url 없이 바깥에 열었습니다. 앱이 만드는 링크가 $PUBLIC_URL 기준이 됩니다. 실제 접속 주소를 --public-url 로 주세요."
fi

# 공용 베이스 이미지가 처음부터 있었는지(이 실행이 내려받은 것은 지우지 않고 알리기만 한다)
if ! obj_exists image "postgres:18"; then BASE_ABSENT_PG=1; fi
if [ "$BUILD_IMAGE" = 1 ] && ! obj_exists image "$BASE_IMAGE"; then BASE_ABSENT_APP=1; fi

# 계획 확인(대화형)
if [ "$NONINTERACTIVE" != 1 ]; then
    say ""
    say "   설치 계획: 폴더 $DIR / 프로젝트 $PROJECT / 주소 $BIND:$HTTP_PORT / PUBLIC_URL $PUBLIC_URL"
    out '   진행할까요? [y/N]: '
    read_answer answer || fail "입력을 받지 못했습니다(터미널이 아니거나 입력이 끝났습니다). 자동 실행이면 --non-interactive 를 붙이세요."
    case "$answer" in y|Y|yes|YES) ;; *) fail "사용자가 설치를 취소했습니다(아무것도 만들지 않았습니다)." ;; esac
fi

# 기존 설치 삭제(--reinstall)
if [ "$HAVE_OLD" = 1 ]; then
    # 옛 설치를 지우기 *전에* 새 설치에 필요한 이미지를 준비한다 — 받지 못하면 아무것도 지우지 않고 멈춘다
    ensure_image "postgres:18"
    if [ "$BUILD_IMAGE" = 1 ]; then ensure_image "$BASE_IMAGE"; fi
    say ""
    say "${C_BOLD}기존 설치를 지웁니다(되돌릴 수 없습니다 — 데이터 볼륨 포함)${C_OFF}"
    MANIFEST=$OLD_MANIFEST
    CLEAN_MODE=run; CLEAN_RUN_ID=$OLD_RUN_ID; CLEAN_PROJECT=$OLD_PROJECT
    cleanup_collect run
    cleanup_print_plan
    if [ "$ASSUME_YES" != 1 ]; then
        if [ "$NONINTERACTIVE" = 1 ]; then
            MANIFEST=""
            fail "--non-interactive 에서 --reinstall 은 --yes 도 필요합니다(삭제 확인을 생략하는 위험한 동작이라 명시해야 합니다). 아무것도 지우지 않았습니다."
        fi
        out '   지우려면 기존 프로젝트 이름(%s)을 그대로 입력하세요: ' "$OLD_PROJECT"
        read_answer answer || { MANIFEST=""; fail "입력을 받지 못했습니다(터미널이 아니거나 입력이 끝났습니다). 아무것도 지우지 않았습니다. 자동 실행이면 --yes 를 붙이세요."; }
        if [ "$answer" != "$OLD_PROJECT" ]; then
            MANIFEST=""
            fail "입력이 일치하지 않아 취소했습니다(아무것도 지우지 않았습니다)."
        fi
    fi
    CLEAN_REMOVED=""; CLEAN_FAILED=0
    cleanup_execute
    cleanup_verify
    if [ -n "$CLEAN_LEFT" ] || [ "$CLEAN_FAILED" -gt 0 ]; then
        cleanup_report || true
        MANIFEST=""
        fail "기존 설치를 완전히 지우지 못해 새 설치를 시작하지 않았습니다. 위 남은 것을 지운 뒤 다시 실행하세요."
    fi
    cleanup_report || true
    say "   기존 설치를 지웠습니다. 이제 새로 설치합니다."
    MANIFEST=""
    # 지운 뒤 다시 확인(남이 끼어들지 않았는지)
    HAVE_OLD=0
    check_port_free
    find_conflicts
    if [ -n "$CONFLICTS" ]; then
        fail "기존 설치를 지운 뒤에도 이름이 겹치는 자원이 있어 중단합니다: $(printf '%s' "$CONFLICTS" | tr '\n' ',')"
    fi
fi

# =============================================================================
# [2/6] 설치 폴더와 비밀 만들기 — 여기부터 만드는 모든 것이 정리 대상이다
# =============================================================================
step 2 "설치 폴더와 비밀 만들기"

TEMP_MANIFEST="${TMPDIR:-/tmp}/roboyam-install-$STAMP.manifest"
MANIFEST=$TEMP_MANIFEST
( umask 077; : > "$MANIFEST" )
manifest_add version 1
manifest_add run_id "$RUN_ID"
manifest_add project "$PROJECT"
manifest_add install_dir "$DIR"
manifest_add started_at "$(date '+%Y-%m-%d %H:%M:%S')"
manifest_add installer_version "$INSTALLER_VERSION"
manifest_add status installing
CLEANUP_ARMED=1

# 설치 폴더 만들기 — 없던 폴더는 하나씩 기록한 뒤 만든다(위 단계부터)
missing=""
p=$DIR
while [ ! -e "$p" ]; do
    if [ -z "$missing" ]; then missing=$p; else missing="$p"$'\n'"$missing"; fi
    p=$(dirname "$p")
done
if [ -n "$missing" ]; then
    manifest_add dir_created 1
    while IFS= read -r p; do
        manifest_add created_dir "$p"
        mkdir "$p"
    done <<< "$missing"
    chmod 750 "$DIR" 2> /dev/null || true
else
    manifest_add dir_created 0
fi
# 매니페스트를 설치 폴더로 옮긴다(이후는 폴더 안 파일에 덧붙인다)
cp "$TEMP_MANIFEST" "$DIR/$MANIFEST_NAME"
MANIFEST="$DIR/$MANIFEST_NAME"
rm -f "$TEMP_MANIFEST"
TEMP_MANIFEST=""
manifest_add path "$DIR/$MANIFEST_NAME"
manifest_add path "$DIR/.env"
manifest_add path "$DIR/compose.yml"
manifest_add path "$DIR/app"
manifest_add http_port "$HTTP_PORT"
manifest_add bind "$BIND"
manifest_add public_url "$PUBLIC_URL"
manifest_add app_container "$C_APP"
note "설치 폴더: $DIR"

# 비밀. 값은 이 셸 변수와 설치 폴더의 파일(.env · secrets/)에만 있다(명령줄 인자·로그에 넣지 않는다)
#   CRYPTO_KEY   암호화 키(AES-256, base64 32바이트) — .env 의 ROBOYAM_CRYPTO_KEY
#   DB_PASSWORD  DB 평문 비밀번호 — postgres 컨테이너용 secrets/db_password 에만 둔다. 앱용은 4단계에서 암호화한다
CRYPTO_KEY=$(openssl rand -base64 32)
DB_PASSWORD=$(rand_alnum 32)
# 설치 토큰: 환경 변수로 미리 준 값이 있으면 그것을(자동화용), 없으면 혼동되는 글자를 뺀 무작위 24자
if [ -n "${ROBOYAM_INSTALL_TOKEN:-}" ]; then INSTALL_TOKEN=$ROBOYAM_INSTALL_TOKEN; else INSTALL_TOKEN=$(rand_alnum 24 'A-HJ-NP-Za-km-z2-9'); fi
[[ $INSTALL_TOKEN =~ ^[A-Za-z0-9._-]{8,128}$ ]] || fail "설치 토큰은 영숫자 . _ - 로 8~128자여야 합니다(ROBOYAM_INSTALL_TOKEN 을 확인하세요)."
[ "${#CRYPTO_KEY}" -eq 44 ] || fail "암호화 키 생성에 실패했습니다(openssl rand -base64 32 가 44자가 아님)."
[ "${#DB_PASSWORD}" -eq 32 ] || fail "DB 비밀번호 생성에 실패했습니다."

# postgres 컨테이너가 읽는 평문 비밀번호 파일(POSTGRES_PASSWORD_FILE). 폴더 700 · 파일 600
mkdir -p "$DIR/secrets"
chmod 700 "$DIR/secrets" 2> /dev/null || true
( umask 077; printf '%s' "$DB_PASSWORD" > "$DIR/secrets/db_password" )
chmod 600 "$DIR/secrets/db_password" 2> /dev/null || true
manifest_add path "$DIR/secrets"

write_env_file() {
    local f="$DIR/.env" secure=false
    case "$PUBLIC_URL" in https://*) secure=true ;; esac
    (
        umask 077
        {
            printf '%s\n' "# ROBOYAM 설치 환경 변수 — roboyam-install.sh 가 만들었다($(date '+%Y-%m-%d %H:%M:%S'))."
            printf '%s\n' "# 비밀이 들어 있다: 권한 600 으로 두고, 저장소·메신저·티켓에 올리지 않는다."
            printf '%s\n' "# 값을 바꾼 뒤에는 이 폴더에서 docker compose up -d 로 다시 반영한다."
            printf '\n%s\n' "# ── 설치기가 쓰는 값 ──"
            printf 'INSTALLER_IMAGE=%s\n' "$IMAGE_TAG"
            printf 'INSTALLER_BIND=%s\n' "$BIND"
            printf 'INSTALLER_HTTP_PORT=%s\n' "$HTTP_PORT"
            printf '\n%s\n' "# ── 앱 환경 ──"
            printf 'SPRING_PROFILES_ACTIVE=prod\n'
            printf 'PUBLIC_URL=%s\n' "$PUBLIC_URL"
            printf '%s\n' "# Snowflake ID 서버 번호(0~1023). 앱 서버를 여러 대 두면 서버마다 다르게 한다"
            printf 'NODE_ID=1\n'
            printf '%s\n' "# 앱 앞에 리버스 프록시가 몇 겹인가(요청자 IP 판단용). 설치기는 앱 포트를 바로 열어 두므로 0 — 프록시를 앞에 두면 그 수로 고친다"
            printf 'ROBOYAM_PROXY_TRUSTED_HOPS=0\n'
            printf '\n%s\n' "# ── 비밀 ── (ROBOYAM_CRYPTO_KEY 를 잃으면 아래 DB_PASSWORD 암호문을 풀 수 없어 앱이 뜨지 않는다 — 따로 백업)"
            printf 'DB_USERNAME=roboyam\n'
            printf 'ROBOYAM_CRYPTO_KEY=%s\n' "$CRYPTO_KEY"
            printf '%s\n' "# 설치 토큰 — 설치 마법사(/install)가 끝나면 쓸모가 없다(이 줄을 지워도 된다)"
            printf 'ROBOYAM_INSTALL_TOKEN=%s\n' "$INSTALL_TOKEN"
            printf '%s\n' "# DB_PASSWORD=ENC(...) 는 4단계(앱 이미지 준비)에서 이 파일 끝에 덧붙는다"
            printf '\n%s\n' "# ── 평문 HTTP 로 쓰면 false, https 앞단이 있으면 true (PUBLIC_URL 에 맞춰 설치기가 정했다) ──"
            printf 'SERVER_SERVLET_SESSION_COOKIE_SECURE=%s\n' "$secure"
            printf '\n%s\n' "# ── 선택(필요할 때 줄을 추가한다) ──"
            printf '%s\n' "# JAVA_OPTS=-XX:MaxRAMPercentage=70"
            printf '%s\n' "# DB_POOL_SIZE=10"
        } > "$f"
    )
    chmod 600 "$f" 2> /dev/null || true
}
write_env_file
note ".env 생성: $DIR/.env (암호화 키 base64 32바이트, PUBLIC_URL, prod 프로필 값), secrets/db_password (DB 비밀번호 32자)"
if [ "$IS_WINDOWS" = 1 ]; then
    note "(Windows: chmod 600 은 NTFS 접근 권한을 바꾸지 않는다 — 이 폴더를 본인 계정만 읽도록 직접 확인하세요)"
fi
log_raw ".env · secrets 작성 완료(값은 기록하지 않음)"

# =============================================================================
# [3/6] compose 파일 생성
# =============================================================================
step 3 "compose 파일 만들기"
[ -f "$SCRIPT_DIR/templates/compose.yml" ] || fail "템플릿이 없습니다: $SCRIPT_DIR/templates/compose.yml"
sed -e "s|@@PROJECT@@|$PROJECT|g" -e "s|@@RUN_ID@@|$RUN_ID|g" "$SCRIPT_DIR/templates/compose.yml" > "$DIR/compose.yml"
( compose_unset_ambient; cd "$DIR" && docker compose --env-file .env -f compose.yml config -q ) >> "${LOG_FILE:-/dev/null}" 2>&1 \
    || fail "compose 파일 검증에 실패했습니다(상세는 로그: ${LOG_FILE:-없음})."
note "compose.yml 생성·검증 완료 — postgres(pg_cron) + app, 네트워크 $N_NET, 볼륨 $V_PG, 앱 포트 $BIND:$HTTP_PORT 만 바깥에 열림, 모든 자원에 라벨 $LABEL_PROJECT=$PROJECT"

# =============================================================================
# [4/6] 앱 이미지
# =============================================================================
step 4 "앱 이미지 준비"
if [ "$BUILD_IMAGE" = 1 ]; then
    mkdir -p "$DIR/app"
    cp "$JAR" "$DIR/app/roboyam-api.jar"
    cp "$SCRIPT_DIR/templates/Dockerfile" "$DIR/app/Dockerfile"
    manifest_add image "$IMAGE_TAG"          # 만들기 전에 적는다(정리는 라벨까지 확인한 뒤에만 지운다)
    manifest_add image_built 1
    note "docker build $IMAGE_TAG (오래 걸릴 수 있다 — 베이스 이미지가 없으면 내려받는다)"
    started=$SECONDS
    run_logged "docker build $IMAGE_TAG" build_image_cmd \
        || fail "앱 이미지 빌드에 실패했습니다(상세는 로그: ${LOG_FILE:-없음}). 베이스 이미지 $BASE_IMAGE 를 받을 수 있는지 확인하세요."
    obj_exists image "$IMAGE_TAG" || fail "빌드는 끝났지만 이미지 $IMAGE_TAG 가 없습니다."
    note "이미지 $IMAGE_TAG 생성 완료($((SECONDS - started))초)"
else
    manifest_add image_built 0
fi

# DB 이미지 — 공식 postgres:18 에 pg_cron 을 더한다(DB 배치). 베이스(postgres:18)와 pgdg 저장소에서 패키지를 내려받으므로 인터넷(또는 사설 미러)이 필요하다.
[ -f "$SCRIPT_DIR/templates/postgres/Dockerfile" ] || fail "템플릿이 없습니다: $SCRIPT_DIR/templates/postgres/Dockerfile"
mkdir -p "$DIR/postgres"
cp "$SCRIPT_DIR/templates/postgres/Dockerfile" "$SCRIPT_DIR/templates/postgres/init-pg-cron.sh" "$DIR/postgres/"
manifest_add image "$PG_IMAGE_TAG"
note "docker build $PG_IMAGE_TAG (postgres:18 + pg_cron — 패키지를 내려받는다)"
started=$SECONDS
run_logged "docker build $PG_IMAGE_TAG" build_pg_image_cmd \
    || fail "DB 이미지 빌드에 실패했습니다(상세는 로그: ${LOG_FILE:-없음}). postgres:18 과 PostgreSQL 공식 apt 저장소(pgdg)에서 postgresql-18-cron 을 받을 수 있는지 확인하세요."
obj_exists image "$PG_IMAGE_TAG" || fail "빌드는 끝났지만 이미지 $PG_IMAGE_TAG 가 없습니다."
note "이미지 $PG_IMAGE_TAG 생성 완료($((SECONDS - started))초)"

# DB 비밀번호를 암호화한다 — 앱 이미지 안의 SecretCipherTool(AES-256-GCM)로 한다.
# 평문은 표준 입력으로만 넘기고(인자·로그에 남지 않는다), 키는 환경 변수 이름만 넘겨 docker 가 값을 읽게 한다.
encrypt_db_password() {
    printf '%s' "$DB_PASSWORD" | ROBOYAM_CRYPTO_KEY="$CRYPTO_KEY" docker run --rm -i --pull never \
        --label "$LABEL_RUN=$RUN_ID" --label "$LABEL_PROJECT=$PROJECT" \
        -e ROBOYAM_CRYPTO_KEY --entrypoint java "$IMAGE_TAG" \
        -Dloader.main=com.community.zconfiguration.crypto.SecretCipherTool \
        -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher encrypt \
        2>> "${LOG_FILE:-/dev/null}" | tr -d '\r'
}
DB_PASSWORD_ENC=$(encrypt_db_password || true)
case "$DB_PASSWORD_ENC" in
    'ENC(v1:'*')') ;;
    *) fail "DB 비밀번호 암호화에 실패했습니다(앱 이미지에 SecretCipherTool 이 있는지 확인: com.community.zconfiguration.crypto). 상세는 로그: ${LOG_FILE:-없음}" ;;
esac
printf 'DB_PASSWORD=%s\n' "$DB_PASSWORD_ENC" >> "$DIR/.env"
note "DB 비밀번호를 암호화해 .env 에 DB_PASSWORD=ENC(...) 로 기록(AES-256-GCM)"

# =============================================================================
# [5/6] DB 기동·대기 → 앱 기동·대기
# =============================================================================
step 5 "DB · 앱 기동과 대기"
manifest_add container "$C_PG"
manifest_add container "$C_APP"
manifest_add volume "$V_PG"
manifest_add network "$N_NET"

wait_db() {
    local deadline=$((SECONDS + DB_WAIT)) st h
    while :; do
        st=$(container_state "$C_PG")
        if [ "$st" != "running" ]; then
            fail_with_logs "DB 컨테이너가 실행 중이 아닙니다(상태: ${st:-없음})." "$C_PG"
        fi
        h=$(container_health "$C_PG")
        [ "$h" = "healthy" ] && return 0
        if [ "$SECONDS" -ge "$deadline" ]; then
            fail_with_logs "DB 가 ${DB_WAIT}초 안에 준비되지 않았습니다(헬스: ${h:-없음})." "$C_PG"
        fi
        sleep_int 2
    done
}

INFO_BODY=""
wait_app() {
    local deadline=$((SECONDS + STARTUP_TIMEOUT)) st dbst rc url
    url="http://$PROBE_HOST:$HTTP_PORT/actuator/health/readiness"
    while :; do
        dbst=$(container_state "$C_PG")
        if [ "$dbst" != "running" ]; then
            fail_with_logs "DB 컨테이너가 중단되었습니다(상태: ${dbst:-없음})." "$C_PG"
        fi
        st=$(container_state "$C_APP")
        case "$st" in
            restarting|exited|dead)
                fail_with_logs "앱 컨테이너가 기동하지 못하고 종료되었습니다(상태: $st)." "$C_APP" ;;
        esac
        rc=$(container_restarts "$C_APP")
        if [[ $rc =~ $RE_INT ]] && [ "$rc" -ge 1 ]; then
            fail_with_logs "앱 컨테이너가 기동 중 비정상 종료해 재시작되었습니다(${rc}회)." "$C_APP"
        fi
        http_get "$url"
        if [ "$HTTP_CODE" = "200" ] && json_has "$HTTP_BODY" status '"UP"'; then
            INFO_BODY=$HTTP_BODY
            return 0
        fi
        if [ "$SECONDS" -ge "$deadline" ]; then
            fail_with_logs "앱이 ${STARTUP_TIMEOUT}초 안에 준비되지 않았습니다(마지막 응답: HTTP $HTTP_CODE, $url)." "$C_APP"
        fi
        sleep_int 2
    done
}

note "DB 기동(postgres:18 이 없으면 내려받는다 — 오래 걸릴 수 있다)"
run_logged "compose up postgres" compose_cmd up -d postgres \
    || fail "DB 컨테이너를 띄우지 못했습니다(상세는 로그: ${LOG_FILE:-없음})."
started=$SECONDS
wait_db
note "DB 준비됨($((SECONDS - started))초)"

note "앱 기동"
run_logged "compose up" compose_cmd up -d \
    || fail "앱 컨테이너를 띄우지 못했습니다(상세는 로그: ${LOG_FILE:-없음})."
started=$SECONDS
note "앱 응답 대기(최대 ${STARTUP_TIMEOUT}초): http://$PROBE_HOST:$HTTP_PORT/actuator/health/readiness"
wait_app
note "앱 기동됨($((SECONDS - started))초): $INFO_BODY"

# =============================================================================
# [6/6] 요약
# =============================================================================
step 6 "설치 요약"
say "   설치 폴더   : $DIR"
say "   프로젝트    : $PROJECT  (컨테이너 $C_PG · $C_APP, 볼륨 $V_PG)"
say "   앱 주소     : $PUBLIC_URL    (이 PC 에서: http://$PROBE_HOST:$HTTP_PORT)"
say "   .env 위치   : $DIR/.env  (권한 600 — 암호화 키와 DB 비밀번호 암호문이 들어 있다)"
say "   DB 평문     : $DIR/secrets/db_password  (권한 600 — postgres 컨테이너가 읽는다. 앱은 이 파일을 쓰지 않는다)"
say "   설치 주소   : $PUBLIC_URL/install    (이 PC 에서: http://$PROBE_HOST:$HTTP_PORT/install)"
# 설치 토큰은 화면에만 한 번 출력한다(로그 파일·매니페스트에 쓰지 않는다)
out '   %s설치 토큰%s   : %s%s%s   (브라우저 /install 첫 화면에 입력 — 이 화면에만 출력합니다. 잃으면 .env 의 ROBOYAM_INSTALL_TOKEN 을 본다)\n' \
    "$C_BOLD" "$C_OFF" "$C_BOLD" "$INSTALL_TOKEN" "$C_OFF"
log_raw "   설치 토큰   : (화면에만 출력 — 로그에 기록하지 않음)"
say ""
out '%s\n' "${C_YELLOW}${C_BOLD}암호화 키 백업${C_OFF}${C_YELLOW}: $DIR/.env 의 ROBOYAM_CRYPTO_KEY 를 안전한 곳에 따로 보관하세요. 이 키를 잃으면 .env 의 DB_PASSWORD 암호문을 풀 수 없어 앱이 뜨지 않습니다(DB 자체는 secrets/db_password 로 접속할 수 있어 새 키로 다시 암호화하면 복구됩니다).${C_OFF}"
log_raw "암호화 키 백업 경고 출력"
if [ "$BIND" = "127.0.0.1" ]; then
    say "   이 주소는 이 서버 안에서만 열립니다(--bind 127.0.0.1). 다른 PC 에서 쓰려면 앞단에 HTTPS 리버스 프록시를 두세요."
else
    out '%s\n' "${C_YELLOW}   바깥에 열려 있습니다($BIND:$HTTP_PORT). 평문 HTTP 라 요청이 그대로 오갑니다 — HTTPS 프록시 없이 인터넷에 열어 두지 마세요.${C_OFF}"
fi
say ""
say "   관리: cd $DIR && docker compose ps | logs -f app | restart app    제거: $SCRIPT_DIR/roboyam-uninstall.sh --dir $DIR"
say "   진단 로그: ${LOG_FILE:-없음} (비밀은 기록하지 않습니다)"
pn=$(pulled_note)
if [ -n "$pn" ]; then say "   참고: $pn"; fi

manifest_add status done
manifest_add finished_at "$(date '+%Y-%m-%d %H:%M:%S')"
INSTALL_OK=1
set +e
say ""
say "${C_GREEN}${C_BOLD}설치 완료${C_OFF} (실패하면 이 실행이 만든 것을 모두 지우는 안전장치는 여기서 해제되었습니다)"
exit 0
