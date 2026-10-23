#!/usr/bin/env bash
# =============================================================================
# installer/lib/common.sh — roboyam-install.sh · roboyam-uninstall.sh · roboyam-mfa-reset.sh 공통 함수
# =============================================================================
#
# source 해서 쓴다(단독 실행하지 않는다). bash 3.2(macOS 기본)에서도 돌도록 bash 4 전용 문법
# (연관 배열, mapfile, ${x,,}, namerefs, $BASHPID)을 쓰지 않는다.
#
# 들어 있는 것
#   - 출력·로그: 로그 파일에는 **비밀을 쓰지 않는다**(비밀번호·키·토큰은 redact 를 거친다)
#   - 매니페스트(.install-manifest): 이 실행이 만든 자원을 **만들기 직전에** 한 줄씩 덧붙인다.
#     줄 형식은 `<종류> <값>` 이고, 같은 종류가 여러 줄일 수 있다. 상태(status)는 덧붙이기만 하고
#     마지막 줄이 현재 값이다(고쳐 쓰는 일이 없어 중간에 죽어도 파일이 망가지지 않는다).
#   - 정리(cleanup): 매니페스트에 적힌 이름 + 라벨로 찾은 자원을 컨테이너 → 볼륨 → 네트워크 → 이미지 →
#     폴더 순으로 지운다. **우리 라벨이 붙은 것만** 지운다(이름이 같아도 라벨이 다르면 건드리지 않는다).
#   - 확인용 보조: 포트 점검, HTTP 확인, 컨테이너 상태
#
# 이 파일을 읽는 쪽이 정해 두어야 하는 전역: LOG_FILE(선택), MANIFEST(매니페스트 쓰기·읽기 전)
# =============================================================================

INSTALLER_VERSION="1.0.0"

# 모든 자원에 붙는 라벨. 프로젝트(설치 하나)와 실행(설치를 한 번 돌린 것)을 가른다.
LABEL_PROJECT="com.roboyam.installer.project"
LABEL_RUN="com.roboyam.installer.run"
MANIFEST_NAME=".install-manifest"

# --- 실행 환경 ---------------------------------------------------------------
case "$(uname -s 2>/dev/null)" in
    MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
    *) IS_WINDOWS=0 ;;
esac
if [ "$IS_WINDOWS" = 1 ]; then
    # Git Bash 는 "/data/media" 같은 인자를 Windows 경로로 바꿔 docker.exe 에 넘긴다 — 끈다.
    # 호스트 경로가 필요한 곳은 쓰지 않는다(compose 는 설치 폴더에서 실행해 상대 경로만 쓴다).
    export MSYS_NO_PATHCONV=1
    export MSYS2_ARG_CONV_EXCL='*'
fi

# --- 출력·로그 ---------------------------------------------------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_BOLD=$'\033[1m'; C_OFF=$'\033[0m'
else
    C_RED=""; C_GREEN=""; C_YELLOW=""; C_BOLD=""; C_OFF=""
fi

LOG_FILE=""

# 로그 파일에만 쓴다. 호출하는 쪽이 비밀을 넘기지 않는 것이 원칙이지만, 실수로 섞여도 지우도록 redact 를 거친다.
log_raw() {
    [ -n "$LOG_FILE" ] || return 0
    printf '%s %s\n' "$(date '+%H:%M:%S')" "$(printf '%s' "$*" | redact_text)" >> "$LOG_FILE" 2>/dev/null || true
}

# 화면에 쓰지 못해도(파이프가 닫힘 등) 설치가 죽으면 안 된다 — 출력 실패는 무시한다
say()  { printf '%s\n' "$*" || true; log_raw "$*"; }
warn() { printf '%s\n' "${C_YELLOW}경고: $*${C_OFF}" >&2 || true; log_raw "경고: $*"; }
note() { printf '   %s\n' "$*" || true; log_raw "   $*"; }
out()  { printf "$@" || true; }          # 화면 출력(표준 출력) — printf 형식 그대로
errp() { printf "$@" >&2 || true; }       # 화면 출력(표준 오류)

# 한 줄 입력(확인용). 터미널이 아니면 15초만 기다린다 — 자동 실행이 입력을 기다리며 멈춰 있지 않게.
# 실패(입력 끝·시간 초과)는 호출한 쪽이 "취소"로 다룬다
read_answer() {   # <결과를 담을 변수 이름>
    local __v=""
    if [ -t 0 ]; then
        IFS= read -r __v || return 1
    else
        IFS= read -r -t 15 __v || return 1
    fi
    printf -v "$1" '%s' "$__v"
}

# 비밀이 든 변수. 아래 redact 가 이 값들을 *** 로 바꾼다(명령줄 인자로 넘기지 않고 셸 안에서만 치환한다).
unset DB_PASSWORD CRYPTO_KEY INSTALL_TOKEN   # 사용자 셸에서 export 된 같은 이름이 자식 프로세스로 새지 않게 먼저 비운다
DB_PASSWORD=""; CRYPTO_KEY=""; INSTALL_TOKEN=""

# 표준 입력의 각 줄에서 비밀 값을 지워 내보낸다
redact_text() {
    local line s
    while IFS= read -r line || [ -n "$line" ]; do
        for s in "$DB_PASSWORD" "$CRYPTO_KEY" "$INSTALL_TOKEN"; do
            if [ -n "$s" ]; then line=${line//"$s"/'***'}; fi
        done
        printf '%s\n' "$line"
    done
}

TOTAL_STEPS=6
STEP_NO=0
STEP_NAME="시작 전"
step() {
    STEP_NO="$1"; STEP_NAME="$2"
    say ""
    say "${C_BOLD}[$1/$TOTAL_STEPS] $2${C_OFF}"
}

# 실패 사유(정리 배너에 쓴다)
FAIL_MSG=""
FAIL_LINE=""
fail() {
    FAIL_MSG="$*"
    printf '%s\n' "${C_RED}오류: $*${C_OFF}" >&2 || true
    log_raw "오류: $*"
    exit 1
}

# --- 난수·검증 -----------------------------------------------------------------
# 영숫자 난수 N자. 명령 치환 안에서 파이프를 닫는 head 를 쓰면 pipefail 에서 SIGPIPE 로 죽을 수 있어
# openssl 출력을 통째로 받아 bash 로 자른다.
rand_alnum() {   # <길이> [허용 문자 tr 집합]
    local n="$1" set="${2:-A-Za-z0-9}" raw=""
    while [ "${#raw}" -lt "$n" ]; do
        raw="$raw$(openssl rand -base64 96 | tr -d '\n' | tr -dc "$set")"
    done
    printf '%s' "${raw:0:$n}"
}

# --- 매니페스트 ----------------------------------------------------------------
MANIFEST=""

manifest_add() {   # <종류> <값>
    printf '%s %s\n' "$1" "$2" >> "$MANIFEST"
}
manifest_get() {   # <종류> → 마지막 값
    [ -f "$MANIFEST" ] || return 0
    awk -v k="$1" '$1 == k { sub(/^[^ ]+ ?/, ""); v = $0 } END { print v }' "$MANIFEST"
}
manifest_list() {  # <종류> → 모든 값(줄마다)
    [ -f "$MANIFEST" ] || return 0
    awk -v k="$1" '$1 == k { sub(/^[^ ]+ ?/, ""); print }' "$MANIFEST"
}

# --- 목록(줄바꿈으로 구분한 문자열) ------------------------------------------------
list_add() {   # <변수 이름> <값> — 중복 없이 덧붙인다
    local __cur
    eval "__cur=\${$1}"
    case $'\n'"$__cur"$'\n' in
        *$'\n'"$2"$'\n'*) return 0 ;;
    esac
    if [ -z "$__cur" ]; then
        printf -v "$1" '%s' "$2"
    else
        printf -v "$1" '%s\n%s' "$__cur" "$2"
    fi
}

# --- docker 보조 ---------------------------------------------------------------
dkq() { docker "$@" >> "${LOG_FILE:-/dev/null}" 2>&1; }   # 출력은 로그로

obj_exists() {   # container|volume|network|image <이름>
    docker "$1" inspect "$2" > /dev/null 2>&1
}

# 라벨 값(없으면 빈 문자열)
obj_label() {   # container|volume|network|image <이름> <라벨 키>
    local fmt
    case "$1" in
        container|image) fmt="{{index .Config.Labels \"$3\"}}" ;;
        *)               fmt="{{index .Labels \"$3\"}}" ;;
    esac
    docker "$1" inspect --format "$fmt" "$2" 2> /dev/null | tr -d '\r' || true
}

container_state() {   # <이름> → running | exited | restarting | created | paused | dead | (없으면 빈 값)
    docker inspect --format '{{.State.Status}}' "$1" 2> /dev/null | tr -d '\r' || true
}
container_health() {
    docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$1" 2> /dev/null | tr -d '\r' || true
}
container_restarts() {
    docker inspect --format '{{.RestartCount}}' "$1" 2> /dev/null | tr -d '\r' || true
}

# 컨테이너 로그 끝부분(비밀은 지운다)
container_log_tail() {   # <이름> [줄 수]
    docker logs --tail "${2:-30}" "$1" 2>&1 | tr -d '\r' | redact_text || true
}

# 이 PC 에서 그 포트로 이미 접속되는 것이 있는가(= 누가 쓰고 있다)
port_in_use() {   # <호스트> <포트>
    ( exec 3<> "/dev/tcp/$1/$2" ) 2> /dev/null
}

# 현재 실행 중인 오래 걸리는 외부 명령(중단 신호가 오면 이것부터 죽인다)
CUR_PID=""

# 외부 명령을 백그라운드로 돌리고 기다린다 — 포그라운드로 돌리면 bash 는 그 명령이 끝나야 신호 처리기를
# 돌리므로 Ctrl-C·TERM 에 바로 반응하지 못한다. 출력은 로그 파일로 보낸다.
run_logged() {   # <설명> <명령...>
    local desc="$1" rc=0
    shift
    log_raw "실행: $desc"
    "$@" >> "${LOG_FILE:-/dev/null}" 2>&1 &
    CUR_PID=$!
    wait "$CUR_PID" || rc=$?
    CUR_PID=""
    return "$rc"
}

# 신호에 바로 깨어나는 sleep
sleep_int() {
    sleep "$1" &
    CUR_PID=$!
    wait "$CUR_PID" 2> /dev/null || true
    CUR_PID=""
}

# --- HTTP ----------------------------------------------------------------------
HTTP_CODE=""
HTTP_BODY=""
http_get() {   # <url> → HTTP_CODE, HTTP_BODY (접속 못 하면 000)
    local out
    out=$(curl -s -m 5 -w $'\n%{http_code}' "$1" 2> /dev/null) || out=$'\n000'
    HTTP_CODE=${out##*$'\n'}
    HTTP_BODY=${out%$'\n'*}
}
json_has() {   # <본문> <키> <값(정규식)>
    printf '%s' "$1" | grep -Eq "\"$2\"[[:space:]]*:[[:space:]]*$3"
}

# --- 파일 시스템 ---------------------------------------------------------------
# 존재하는 가장 가까운 윗 폴더(쓸 수 있는지 볼 때 쓴다)
nearest_existing_dir() {
    local p="$1"
    while [ ! -e "$p" ]; do
        p=$(dirname "$p")
    done
    printf '%s' "$p"
}

# 절대 경로로. 존재하지 않아도 된다
abs_path() {
    case "$1" in
        /*|[A-Za-z]:[\\/]*) printf '%s' "${1%/}" ;;
        *) printf '%s' "$(pwd)/${1%/}" ;;
    esac
}

# rm -rf 를 해도 되는 폴더인가: 절대 경로이고 두 단계 이상이며 홈·루트·흔한 시스템 폴더가 아니다
safe_to_remove_dir() {
    local d="$1"
    case "$d" in
        ""|/|"$HOME"|"${HOME%/}"/|/opt|/usr|/etc|/var|/home|/root|/bin|/lib|/tmp|/d|/c) return 1 ;;
    esac
    case "$d" in
        /*/*|[A-Za-z]:/*/*) return 0 ;;
    esac
    return 1
}

# =============================================================================
# 정리(cleanup)
# =============================================================================
#
# 호출 전에 CLEAN_MODE 와 (CLEAN_RUN_ID 또는 CLEAN_PROJECT), MANIFEST 를 정한다.
#   CLEAN_MODE=run      라벨 `run=<id>` 가 붙은 것 + 매니페스트에 적힌 이름 중 같은 라벨이 붙은 것
#   CLEAN_MODE=project  라벨 `project=<이름>` 이 붙은 모든 것(매니페스트가 없을 때의 제거)
# 결과: CLEAN_REMOVED(지운 것, 여러 줄) · CLEAN_KEPT(우리 것이 아니라 건드리지 않은 것) ·
#       CLEAN_FAILED(실패 수) · CLEAN_LEFT(아직 남은 것, 여러 줄)

CLEAN_MODE="run"
CLEAN_RUN_ID=""
CLEAN_PROJECT=""
T_CONTAINERS=""; T_VOLUMES=""; T_NETWORKS=""; T_IMAGES=""; T_PATHS=""; T_KEPT=""
CLEAN_REMOVED=""; CLEAN_KEPT=""; CLEAN_FAILED=0; CLEAN_LEFT=""

_clean_key_val() {
    if [ "$CLEAN_MODE" = run ]; then
        CK_KEY="$LABEL_RUN"; CK_VAL="$CLEAN_RUN_ID"
    else
        CK_KEY="$LABEL_PROJECT"; CK_VAL="$CLEAN_PROJECT"
    fi
}

# 폴더·파일 정보는 정리 도중 매니페스트가 있는 폴더가 사라지므로 미리 읽어 둔다(파일이 없으면 이전 값을 유지)
M_INSTALL_DIR=""; M_DIR_CREATED=""; M_PATHS=""; M_CREATED_DIRS_REV=""
cleanup_load_manifest() {
    [ -f "$MANIFEST" ] || return 0
    M_INSTALL_DIR=$(manifest_get install_dir)
    M_DIR_CREATED=$(manifest_get dir_created)
    M_PATHS=$(manifest_list path)
    M_CREATED_DIRS_REV=$(manifest_list created_dir | awk '{ a[NR] = $0 } END { for (i = NR; i >= 1; i--) print a[i] }')
}

# 지울 대상을 모은다(지우지 않는다) → T_* 목록
cleanup_collect() {
    local n id flt kind
    _clean_key_val
    cleanup_load_manifest
    flt="label=$CK_KEY=$CK_VAL"
    T_CONTAINERS=""; T_VOLUMES=""; T_NETWORKS=""; T_IMAGES=""; T_PATHS=""; T_KEPT=""

    # 1) 라벨로 찾은 것 — 우리 것이 확실하다
    while IFS= read -r n; do
        [ -n "$n" ] && list_add T_CONTAINERS "$n"
    done < <(docker ps -a --filter "$flt" --format '{{.Names}}' 2> /dev/null | tr -d '\r')
    while IFS= read -r n; do
        [ -n "$n" ] && list_add T_VOLUMES "$n"
    done < <(docker volume ls --filter "$flt" --format '{{.Name}}' 2> /dev/null | tr -d '\r')
    while IFS= read -r n; do
        [ -n "$n" ] && list_add T_NETWORKS "$n"
    done < <(docker network ls --filter "$flt" --format '{{.Name}}' 2> /dev/null | tr -d '\r')
    # 태그가 있으면 태그로, 태그 없이 라벨만 남은 이미지(빌드가 중간에 끊긴 경우)는 ID 로
    while IFS= read -r n; do
        [ -n "$n" ] || continue
        id=${n#* }
        n=${n%% *}
        case "$n" in
            "<none>:<none>"|"<none>"*) list_add T_IMAGES "$id" ;;
            *) list_add T_IMAGES "$n" ;;
        esac
    done < <(docker image ls -a --filter "$flt" --format '{{.Repository}}:{{.Tag}} {{.ID}}' 2> /dev/null | tr -d '\r')

    # 2) 매니페스트에 적힌 이름 — 라벨이 같을 때만(이름만 같은 남의 것은 건드리지 않는다)
    for kind in container volume network image; do
        while IFS= read -r n; do
            [ -n "$n" ] || continue
            obj_exists "$kind" "$n" || continue
            if [ "$(obj_label "$kind" "$n" "$CK_KEY")" = "$CK_VAL" ]; then
                case "$kind" in
                    container) list_add T_CONTAINERS "$n" ;;
                    volume)    list_add T_VOLUMES "$n" ;;
                    network)   list_add T_NETWORKS "$n" ;;
                    image)     list_add T_IMAGES "$n" ;;
                esac
            else
                list_add T_KEPT "$kind $n"
            fi
        done <<< "$(manifest_list "$kind")"
    done
}

# 지울 것을 사람이 읽게 출력한다(실제로 지우기 전에 보여 주는 목록)
cleanup_print_plan() {
    local n dir created
    [ -n "$T_CONTAINERS" ] && while IFS= read -r n; do note "컨테이너  $n"; done <<< "$T_CONTAINERS"
    [ -n "$T_VOLUMES" ]    && while IFS= read -r n; do note "볼륨(데이터)  $n"; done <<< "$T_VOLUMES"
    [ -n "$T_NETWORKS" ]   && while IFS= read -r n; do note "네트워크  $n"; done <<< "$T_NETWORKS"
    [ -n "$T_IMAGES" ]     && while IFS= read -r n; do note "이미지  $n"; done <<< "$T_IMAGES"
    dir=$M_INSTALL_DIR
    if [ -n "$dir" ] && [ -e "$dir" ]; then
        created=$M_DIR_CREATED
        if [ "$created" = 1 ]; then
            note "설치 폴더(.env 와 내려받은 jar 포함)  $dir"
        else
            note "설치가 만든 파일(폴더 자체는 남김)  $dir"
        fi
    fi
    if [ -n "$T_KEPT" ]; then
        while IFS= read -r n; do note "건드리지 않음(우리 라벨이 아님)  $n"; done <<< "$T_KEPT"
    fi
    return 0
}

_removed() { list_add CLEAN_REMOVED "$1"; }
_clean_fail() { CLEAN_FAILED=$((CLEAN_FAILED + 1)); }

# 목록을 실제로 지운다. 하나가 실패해도 나머지는 계속한다(중간에 멈추면 반쯤 지워진 상태가 남는다).
cleanup_execute() {
    local n tries ok dir created p
    CLEAN_REMOVED=""; CLEAN_FAILED=0

    # ① 컨테이너(중지 포함) — 볼륨·네트워크가 쓰이는 동안은 못 지우므로 가장 먼저
    if [ -n "$T_CONTAINERS" ]; then
        while IFS= read -r n; do
            [ -n "$n" ] || continue
            if dkq rm -f -v "$n"; then _removed "컨테이너 $n"; else obj_exists container "$n" && _clean_fail; fi
        done <<< "$T_CONTAINERS"
    fi

    # ② 볼륨(데이터). 컨테이너가 막 지워진 직후에는 잠깐 사용 중으로 보일 수 있어 몇 번 다시 시도한다
    if [ -n "$T_VOLUMES" ]; then
        while IFS= read -r n; do
            [ -n "$n" ] || continue
            ok=0; tries=0
            while [ "$tries" -lt 5 ]; do
                if dkq volume rm -f "$n" || ! obj_exists volume "$n"; then ok=1; break; fi
                tries=$((tries + 1)); sleep 1
            done
            if [ "$ok" = 1 ]; then _removed "볼륨 $n"; else _clean_fail; fi
        done <<< "$T_VOLUMES"
    fi

    # ③ 네트워크
    if [ -n "$T_NETWORKS" ]; then
        while IFS= read -r n; do
            [ -n "$n" ] || continue
            ok=0; tries=0
            while [ "$tries" -lt 5 ]; do
                if dkq network rm "$n" || ! obj_exists network "$n"; then ok=1; break; fi
                tries=$((tries + 1)); sleep 1
            done
            if [ "$ok" = 1 ]; then _removed "네트워크 $n"; else _clean_fail; fi
        done <<< "$T_NETWORKS"
    fi

    # ④ 이 실행이 만든 이미지(태그 → 남은 ID). 공용 베이스 이미지(postgres, temurin)는 대상이 아니다
    if [ -n "$T_IMAGES" ]; then
        while IFS= read -r n; do
            [ -n "$n" ] || continue
            if ! obj_exists image "$n"; then continue; fi      # 태그를 지우면서 ID 도 함께 사라진 경우
            if dkq image rm "$n"; then _removed "이미지 $n"; else obj_exists image "$n" && _clean_fail; fi
        done <<< "$T_IMAGES"
    fi

    # ⑤ 설치 폴더 · 파일
    dir=$M_INSTALL_DIR
    if [ -n "$dir" ] && [ -e "$dir" ]; then
        created=$M_DIR_CREATED
        if [ "$created" = 1 ]; then
            if safe_to_remove_dir "$dir"; then
                if rm -rf "$dir" 2> /dev/null && [ ! -e "$dir" ]; then _removed "설치 폴더 $dir"; else _clean_fail; fi
            else
                warn "설치 폴더 경로가 안전하지 않아 지우지 않았습니다: $dir"
                _clean_fail
            fi
        else
            # 우리가 만든 폴더가 아니다 — 폴더는 두고 우리가 만든 것만 지운다
            while IFS= read -r p; do
                [ -n "$p" ] || continue
                case "$p" in
                    "$dir"/*)
                        rm -rf "$p" 2> /dev/null
                        if [ -e "$p" ]; then _clean_fail; else _removed "파일 $p"; fi ;;
                esac
            done <<< "$M_PATHS"
            rm -f "$dir/$MANIFEST_NAME" 2> /dev/null || true
        fi
    fi
    # 우리가 만든 윗 폴더(--dir 가 여러 단계였을 때). 아래부터, 비어 있을 때만 지운다
    while IFS= read -r p; do
        [ -n "$p" ] || continue
        [ "$p" = "$dir" ] && continue
        if [ -d "$p" ] && rmdir "$p" 2> /dev/null; then _removed "폴더 $p"; fi
    done <<< "$M_CREATED_DIRS_REV"

    # 임시 위치에 둔 매니페스트(설치 폴더를 만들기 전 단계에서 쓰던 것)
    if [ -n "${TEMP_MANIFEST:-}" ] && [ -f "$TEMP_MANIFEST" ]; then
        rm -f "$TEMP_MANIFEST" 2> /dev/null || true
    fi
    return 0
}

# 정리가 끝난 뒤 남은 것을 다시 센다 → CLEAN_LEFT
cleanup_verify() {
    local n dir
    cleanup_collect "$CLEAN_MODE"
    CLEAN_LEFT=""
    if [ -n "$T_CONTAINERS" ]; then while IFS= read -r n; do list_add CLEAN_LEFT "container $n"; done <<< "$T_CONTAINERS"; fi
    if [ -n "$T_VOLUMES" ];    then while IFS= read -r n; do list_add CLEAN_LEFT "volume $n"; done <<< "$T_VOLUMES"; fi
    if [ -n "$T_NETWORKS" ];   then while IFS= read -r n; do list_add CLEAN_LEFT "network $n"; done <<< "$T_NETWORKS"; fi
    if [ -n "$T_IMAGES" ];     then while IFS= read -r n; do list_add CLEAN_LEFT "image $n"; done <<< "$T_IMAGES"; fi
    dir=$M_INSTALL_DIR
    if [ -n "$dir" ] && [ -e "$dir" ] && [ "$M_DIR_CREATED" = 1 ]; then
        list_add CLEAN_LEFT "dir $dir"
    fi
    return 0
}

# 정리 결과를 출력한다. 남은 것이 있으면 크게 경고하고 손으로 지우는 명령을 보여 준다.
cleanup_report() {
    local n kind name
    if [ -n "$CLEAN_REMOVED" ]; then
        say "   지운 것:"
        while IFS= read -r n; do say "     - $n"; done <<< "$CLEAN_REMOVED"
    else
        say "   지울 것이 없었습니다."
    fi
    if [ -n "$CLEAN_KEPT" ]; then
        say "   건드리지 않은 것(우리 라벨이 없어 우리 것이 아님):"
        while IFS= read -r n; do say "     - $n"; done <<< "$CLEAN_KEPT"
    fi
    if [ -n "$CLEAN_LEFT" ] || [ "$CLEAN_FAILED" -gt 0 ]; then
        printf '%s\n' "${C_RED}${C_BOLD}!!! 정리가 끝까지 되지 않았습니다 — 아래가 남아 있습니다 !!!${C_OFF}" >&2
        log_raw "정리 미완료"
        if [ -n "$CLEAN_LEFT" ]; then
            while IFS= read -r n; do
                kind=${n%% *}; name=${n#* }
                case "$kind" in
                    container) printf '%s\n' "     남음: 컨테이너 $name   → docker rm -f -v $name" >&2 ;;
                    volume)    printf '%s\n' "     남음: 볼륨(데이터) $name   → docker volume rm -f $name" >&2 ;;
                    network)   printf '%s\n' "     남음: 네트워크 $name   → docker network rm $name" >&2 ;;
                    image)     printf '%s\n' "     남음: 이미지 $name   → docker image rm $name" >&2 ;;
                    dir)       printf '%s\n' "     남음: 폴더 $name   → rm -rf '$name'" >&2 ;;
                esac
            done <<< "$CLEAN_LEFT"
        fi
        printf '%s\n' "     위 명령으로 직접 지우거나, roboyam-uninstall.sh --project ${CLEAN_PROJECT:-<프로젝트>} 를 실행하세요." >&2
        return 1
    fi
    return 0
}
