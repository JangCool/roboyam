#!/usr/bin/env bash
# ============================================================================
# 로보얌 배포 — 새 jar 로 바꾸고 재시작, readiness 가 안 오면 이전 jar 로 되돌린다
#
# 대상   : roboyam-app-install.sh 로 설치한 앱 서버, root
# 배치   : /opt/roboyam/<앱>/releases/roboyam-<앱>-<날짜시각>.jar  (보관)
#          /opt/roboyam/<앱>/current.jar → 지금 쓰는 jar 로 가는 심볼릭 링크 (systemd 가 이것을 띄운다)
#
# 사용법
#   roboyam-deploy.sh api   /tmp/roboyam-api.jar
#   roboyam-deploy.sh admin /tmp/roboyam-admin.jar --timeout 300
#   roboyam-deploy.sh api --rollback          바로 이전 jar 로 되돌린다
#   roboyam-deploy.sh api --list              보관 중인 jar 와 지금 쓰는 것
#
# 옵션
#       --keep N      보관할 이전 jar 수 (기본 5 — 지금 쓰는 것은 따로 늘 남긴다)
#       --timeout S   readiness 를 기다릴 최대 초 (기본 300 — 처음 기동·Flyway 이관이 길 수 있다)
#   -h, --help        도움말
#
# 순서: jar 검사 → releases 에 복사 → current.jar 를 새 jar 로 → systemctl restart →
#       http://127.0.0.1:<포트>/actuator/health/readiness 가 200 이 될 때까지 기다린다.
#       실패하면 current.jar 를 이전 jar 로 되돌리고 다시 띄운 뒤 종료 코드 1.
#
# 주의
#   - 되돌려도 DB 는 되돌리지 않는다. 새 jar 가 Flyway 이관을 이미 적용했을 수 있다
#     (옛 jar 는 모르는 새 이관을 건너뛰고 뜬다 — 그래도 표 변경이 옛 코드와 맞지 않으면 직접 판단한다).
#   - 서버 두 대에 같은 jar 를 올릴 때는 한 대씩 한다(Cloudflare 로드밸런싱이 내려간 서버를 잠시 뺀다).
#   - api 와 admin 은 각각 부른다. 둘 다 바꾸는 릴리스는 admin → api 순서를 권한다(관리자 앱이 DB 배치를 등록한다).
# ============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/common.sh
. "${SCRIPT_DIR}/../lib/common.sh"
trap on_error ERR

BASE_DIR="${ROBOYAM_BASE_DIR:-/opt/roboyam}"
ENV_DIR="${ROBOYAM_ENV_DIR:-/etc/roboyam}"
KEEP=5
TIMEOUT=300
MODE=deploy
APP=""
NEW_JAR=""

usage() { usage_from_header "$0"; }
usage_error() { printf '[사용법 오류] %s\n\n' "$*" >&2; usage >&2; exit 2; }

parse_args() {
    [ $# -gt 0 ] || usage_error "앱 이름(api 또는 admin)이 필요합니다."
    case "$1" in
        -h|--help) usage; exit 0 ;;
        api|admin) APP="$1"; shift ;;
        *) usage_error "앱 이름은 api 또는 admin 입니다: $1" ;;
    esac
    while [ $# -gt 0 ]; do
        case "$1" in
            --keep)     KEEP="${2:?--keep 값이 필요합니다}"; shift 2 ;;
            --timeout)  TIMEOUT="${2:?--timeout 값이 필요합니다}"; shift 2 ;;
            --rollback) MODE=rollback; shift ;;
            --list)     MODE=list; shift ;;
            -h|--help)  usage; exit 0 ;;
            -*)         usage_error "알 수 없는 옵션: $1" ;;
            *)          [ -z "$NEW_JAR" ] || usage_error "jar 는 하나만 줍니다."; NEW_JAR="$1"; shift ;;
        esac
    done
    [[ "$KEEP" =~ ^[0-9]+$ ]] && [ "$KEEP" -ge 1 ] || usage_error "--keep 는 1 이상 숫자여야 합니다."
    [[ "$TIMEOUT" =~ ^[0-9]+$ ]] && [ "$TIMEOUT" -ge 10 ] || usage_error "--timeout 은 10 이상 숫자(초)여야 합니다."
    if [ "$MODE" = deploy ]; then
        [ -n "$NEW_JAR" ] || usage_error "배포할 jar 경로가 필요합니다."
        [ -f "$NEW_JAR" ] || fail "jar 파일이 없습니다: ${NEW_JAR}"
    fi

    APP_DIR="${BASE_DIR}/${APP}"
    RELEASES="${APP_DIR}/releases"
    CURRENT="${APP_DIR}/current.jar"
    SERVICE="roboyam-${APP}"
    ENV_FILE="${ENV_DIR}/${APP}.env"
    [ -f "$ENV_FILE" ] || fail "${ENV_FILE} 가 없습니다. 먼저 roboyam-app-install.sh 로 이 서버에 ${APP} 를 설치하세요."
    PORT="$(env_get "$ENV_FILE" SERVER_PORT)"
    [ -n "$PORT" ] || fail "${ENV_FILE} 에 SERVER_PORT 가 없습니다."
    READY_URL="http://127.0.0.1:${PORT}/actuator/health/readiness"
}

current_target() { [ -L "$CURRENT" ] && readlink -f "$CURRENT" || true; }

# current.jar 를 한 번에 바꾼다(임시 링크를 만들고 rename — 중간 상태가 없다)
point_current_to() {   # <jar 절대 경로>
    ln -sfn "$1" "${CURRENT}.new"
    mv -Tf "${CURRENT}.new" "$CURRENT"
}

wait_ready() {
    local deadline=$((SECONDS + TIMEOUT)) code
    while [ "$SECONDS" -lt "$deadline" ]; do
        code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$READY_URL" 2>/dev/null || true)"
        [ "$code" = "200" ] && return 0
        sleep 3
    done
    return 1
}

restart_and_wait() {
    log "${SERVICE} 재시작 → ${READY_URL} 대기 (최대 ${TIMEOUT}초)"
    systemctl restart "$SERVICE"
    wait_ready
}

prune_releases() {
    local cur; cur="$(current_target)"
    local f n=0
    # 최근 것부터 보며 지금 쓰는 것 말고 KEEP 개만 남긴다
    while IFS= read -r f; do
        [ "$f" = "$cur" ] && continue
        n=$((n + 1))
        if [ "$n" -gt "$KEEP" ]; then
            rm -f "$f"
            log "오래된 jar 삭제: $(basename "$f")"
        fi
    done < <(find "$RELEASES" -maxdepth 1 -name "roboyam-${APP}-*.jar" -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
}

# 바로 이전 jar = 올린 시각 순으로 지금 것 바로 앞의 것(지금 것이 목록에 없으면 가장 최근 것)
previous_release() {
    local cur; cur="$(current_target)"
    find "$RELEASES" -maxdepth 1 -name "roboyam-${APP}-*.jar" -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2- |
        awk -v c="$cur" 'BEGIN { seen = (c == "") } seen && !found && $0 != c { print; found = 1 } $0 == c { seen = 1 }'
}

show_logs() {
    echo
    journalctl -u "$SERVICE" -n 60 --no-pager || true
}

do_list() {
    local cur; cur="$(current_target)"
    echo "${APP} — 지금: ${cur:-<없음>}"
    find "$RELEASES" -maxdepth 1 -name "roboyam-${APP}-*.jar" -printf '%TY-%Tm-%Td %TH:%TM  %s  %p\n' 2>/dev/null | sort -r |
        awk -v c="$cur" '{ mark = ($4 == c) ? " ← 지금" : ""; printf "  %s %s  %6.1fMB  %s%s\n", $1, $2, $3 / 1048576, $4, mark }'
}

do_rollback() {
    local prev; prev="$(previous_release)"
    [ -n "$prev" ] || fail "되돌릴 이전 jar 가 없습니다."
    local cur; cur="$(current_target)"
    log "되돌리기: $(basename "${cur:-없음}") → $(basename "$prev")"
    point_current_to "$prev"
    if restart_and_wait; then
        log "되돌리기 완료: $(basename "$prev")"
    else
        show_logs
        fail "되돌린 jar 도 ${TIMEOUT}초 안에 준비되지 않았습니다. journalctl -u ${SERVICE} 를 확인하세요."
    fi
}

do_deploy() {
    # Spring Boot jar(zip) 인지 대략 본다 — 잘못 올린 파일로 서비스를 내리지 않게
    [ "$(head -c 2 "$NEW_JAR")" = "PK" ] || fail "jar(zip) 파일이 아닙니다: ${NEW_JAR}"
    grep -qa 'BOOT-INF/' "$NEW_JAR" || fail "Spring Boot jar 가 아닙니다(BOOT-INF 없음): ${NEW_JAR}"

    mkdir -p "$RELEASES"
    local sum dest
    sum="$(sha256sum "$NEW_JAR" | cut -c1-8)"
    dest="${RELEASES}/roboyam-${APP}-${STAMP}-${sum}.jar"
    install -m 644 -o root -g roboyam "$NEW_JAR" "$dest"

    local prev; prev="$(current_target)"
    if [ -n "$prev" ] && cmp -s "$prev" "$dest"; then
        rm -f "$dest"
        log "지금 쓰는 jar 와 같은 파일입니다($(basename "$prev")) — 재시작만 합니다."
        dest="$prev"
    fi

    log "배포: $(basename "${prev:-없음}") → $(basename "$dest")"
    point_current_to "$dest"
    if restart_and_wait; then
        log "준비 완료: ${APP} $(basename "$dest")"
        prune_releases
        return 0
    fi

    warn "${TIMEOUT}초 안에 readiness 가 오지 않았습니다."
    show_logs
    if [ -z "$prev" ] || [ "$prev" = "$dest" ]; then
        fail "되돌릴 이전 jar 가 없습니다. 로그를 확인하세요(journalctl -u ${SERVICE})."
    fi
    warn "이전 jar 로 되돌립니다: $(basename "$prev") (DB 이관은 되돌리지 않습니다)"
    point_current_to "$prev"
    if restart_and_wait; then
        warn "이전 jar 로 돌아왔습니다. 실패한 jar 는 ${dest} 에 남겨 둡니다."
    else
        show_logs
        warn "이전 jar 도 준비되지 않았습니다 — DB·설정 문제일 수 있습니다."
    fi
    exit 1
}

main() {
    parse_args "$@"
    case "$MODE" in
        list)     do_list ;;
        rollback) require_root; do_rollback ;;
        deploy)   require_root; do_deploy ;;
    esac
}

main "$@"
