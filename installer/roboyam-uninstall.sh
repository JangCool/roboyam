#!/usr/bin/env bash
# =============================================================================
# roboyam-uninstall.sh — roboyam-install.sh 가 만든 설치를 완전히 제거한다 (되돌릴 수 없다)
# =============================================================================
#
# 설치 폴더의 .install-manifest 를 읽어 그 설치가 만든 컨테이너 · 볼륨(데이터) · 네트워크 · 이미지 · 설치 폴더를
# 지운다. 설치가 도중에 끊겨 남은 것(status 가 done 이 아님)도 같은 방식으로 지운다.
# 매니페스트가 없으면(폴더를 손으로 지운 경우 등) 프로젝트 라벨(com.roboyam.installer.project=<이름>)이 붙은
# 자원을 찾아 지운다. 라벨이 없는 자원은 이름이 같아도 건드리지 않는다.
#
# 사용법
#   ./roboyam-uninstall.sh --dir /opt/roboyam
#   ./roboyam-uninstall.sh --project roboyam --yes
#
# 옵션
#   --dir <폴더>      설치 폴더(기본 /opt/roboyam)
#   --project <이름>  매니페스트가 없을 때 라벨로 찾을 프로젝트 이름(기본 roboyam)
#   --yes             확인 입력(프로젝트 이름 타이핑)을 생략
#   --dry-run         지울 목록만 보여 주고 아무것도 지우지 않는다
#   --help            이 도움말
#
# 지우기 전에 무엇이 지워질지 목록을 보여 주고, 프로젝트 이름을 직접 입력해야 진행한다(--yes 로 생략).
# 공용 베이스 이미지(postgres, eclipse-temurin)는 지우지 않는다.
# =============================================================================
# set -e 를 쓰지 않는다 — 지우는 도중 이미 없는 것을 만나도 나머지는 계속 지워야 한다(반쯤 지워진 상태를 남기지 않는다).
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/common.sh
. "$SCRIPT_DIR/lib/common.sh"

DIR="/opt/roboyam"
PROJECT=""
ASSUME_YES=0
DRY_RUN=0

usage() { sed -n '/^# 사용법/,/^# 지우기 전에/p' "$0" | sed 's/^# \{0,1\}//'; }
usage_error() { printf '%s\n' "오류: $*" >&2; printf '%s\n' "도움말: $0 --help" >&2; exit 2; }
need_arg() { if [ $# -lt 2 ] || [ -z "$2" ]; then usage_error "$1 에 값이 필요합니다"; fi; }

while [ $# -gt 0 ]; do
    case "$1" in
        --*=*) set -- "${1%%=*}" "${1#*=}" "${@:2}" ;;
    esac
    case "$1" in
        --dir)      need_arg "$@"; DIR=$2; shift 2 ;;
        --project)  need_arg "$@"; PROJECT=$2; shift 2 ;;
        --yes|-y)   ASSUME_YES=1; shift ;;
        --dry-run)  DRY_RUN=1; shift ;;
        --version)  echo "roboyam-uninstall $INSTALLER_VERSION"; exit 0 ;;
        --help|-h)  usage; exit 0 ;;
        *)          usage_error "알 수 없는 옵션: $1" ;;
    esac
done

RE_PROJECT='^[a-z0-9][a-z0-9_-]{0,30}$'
if [ -n "$PROJECT" ] && ! [[ $PROJECT =~ $RE_PROJECT ]]; then
    usage_error "--project 는 소문자·숫자·-_ 로 31자 이내여야 합니다: $PROJECT"
fi
DIR=$(abs_path "$DIR")

command -v docker > /dev/null 2>&1 || { printf '%s\n' "오류: docker 를 찾을 수 없습니다." >&2; exit 1; }
docker info > /dev/null 2>&1 || { printf '%s\n' "오류: docker 데몬에 연결할 수 없습니다(Docker 가 켜져 있는지, 권한이 있는지 확인)." >&2; exit 1; }

STAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="${TMPDIR:-/tmp}/roboyam-uninstall-$STAMP.log"
( umask 077; : >> "$LOG_FILE" ) 2> /dev/null || LOG_FILE=""

say "ROBOYAM 설치 제거 (roboyam-uninstall $INSTALLER_VERSION)"

# --- 무엇을 지울지 정한다 ----------------------------------------------------------
MODE_DESC=""
if [ -f "$DIR/$MANIFEST_NAME" ]; then
    MANIFEST="$DIR/$MANIFEST_NAME"
    m_status=$(manifest_get status)
    m_project=$(manifest_get project)
    m_run=$(manifest_get run_id)
    [ -n "$PROJECT" ] && [ "$PROJECT" != "$m_project" ] \
        && warn "--project $PROJECT 는 무시합니다 — 설치 폴더의 기록은 프로젝트 ${m_project:-?} 입니다."
    PROJECT=${m_project:-${PROJECT:-roboyam}}
    if [ -n "$m_run" ]; then
        CLEAN_MODE=run; CLEAN_RUN_ID=$m_run; CLEAN_PROJECT=$PROJECT
        MODE_DESC="설치 기록($DIR/$MANIFEST_NAME)  상태: ${m_status:-?}  실행 ID: $m_run"
    else
        warn "매니페스트에 run_id 가 없어 프로젝트 라벨로 찾습니다."
        CLEAN_MODE=project; CLEAN_PROJECT=$PROJECT
        MODE_DESC="설치 기록($DIR/$MANIFEST_NAME)에 run_id 가 없어 프로젝트 라벨로 찾음"
    fi
    case "${m_status:-}" in
        done) ;;
        *) say "   이 설치는 끝나지 않았거나 정리가 덜 끝난 상태입니다(status: ${m_status:-?}). 남은 것을 지웁니다." ;;
    esac
else
    MANIFEST=""
    PROJECT=${PROJECT:-roboyam}
    CLEAN_MODE=project; CLEAN_PROJECT=$PROJECT
    MODE_DESC="설치 기록이 없어 프로젝트 라벨(${LABEL_PROJECT}=$PROJECT)로 찾음"
fi
say "   찾는 방법: $MODE_DESC"

cleanup_collect "$CLEAN_MODE"
CLEAN_KEPT=$T_KEPT

has_something=0
[ -n "$T_CONTAINERS$T_VOLUMES$T_NETWORKS$T_IMAGES" ] && has_something=1
if [ -n "$MANIFEST" ] && [ -n "$M_INSTALL_DIR" ] && [ -e "$M_INSTALL_DIR" ]; then has_something=1; fi

if [ "$has_something" = 0 ]; then
    say ""
    say "지울 것이 없습니다(프로젝트 $PROJECT 의 자원도, 설치 폴더 기록도 찾지 못했습니다)."
    [ -n "$CLEAN_KEPT" ] && cleanup_report
    exit 0
fi

say ""
say "${C_BOLD}지워질 것(되돌릴 수 없다 — 데이터 볼륨 포함):${C_OFF}"
cleanup_print_plan

if [ "$DRY_RUN" = 1 ]; then
    say ""
    say "(--dry-run: 아무것도 지우지 않았습니다)"
    exit 0
fi

if [ "$ASSUME_YES" != 1 ]; then
    say ""
    out '지우려면 프로젝트 이름(%s)을 그대로 입력하세요: ' "$PROJECT"
    if ! read_answer answer; then
        errp '\n%s\n' "오류: 입력을 받지 못했습니다(터미널이 아니거나 입력이 끝났습니다). 자동 실행이면 --yes 를 붙이세요. 아무것도 지우지 않았습니다."
        exit 1
    fi
    if [ "$answer" != "$PROJECT" ]; then
        errp '%s\n' "입력이 일치하지 않아 취소했습니다(아무것도 지우지 않았습니다)."
        exit 1
    fi
fi

say ""
say "지우는 중…"
CLEAN_REMOVED=""; CLEAN_FAILED=0
cleanup_execute
cleanup_verify
say ""
if cleanup_report; then
    say "${C_GREEN}${C_BOLD}제거 완료${C_OFF} — 프로젝트 $PROJECT 의 컨테이너·볼륨·네트워크·이미지·설치 폴더를 지웠습니다."
    exit 0
fi
exit 1
