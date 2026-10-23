#!/usr/bin/env bash
# =============================================================================
# roboyam-mfa-reset.sh — 관리자 2차 인증(OTP) 초기화 명령 (서버 접근자 전용)
# =============================================================================
#
# 최고 관리자(루트)가 OTP 앱(폰)과 복구 코드를 모두 잃어 관리자 앱에 들어갈 수 없을 때 쓴다.
# 설치 폴더의 DB 컨테이너에서 psql 로 DB 함수 admin_mfa_reset(이메일) 을 부른다 — 그 운영자의 OTP·복구 코드·신뢰 기기를 지우고
# 감사 기록(MFA_RESET, 행위자 SYSTEM, via SERVER_COMMAND)을 남긴다. 다른 운영자의 OTP 초기화는 관리자 앱의 운영자 상세에서 루트가 한다.
# 초기화한 뒤 그 운영자는 비밀번호로 로그인하고, 2차 인증이 켜져 있으면 유예에 따라 안내 또는 등록 화면이 나온다.
#
# 사용법
#   ./roboyam-mfa-reset.sh admin@example.com
#   ./roboyam-mfa-reset.sh --dir /opt/roboyam admin@example.com
#
# 옵션
#   --dir <폴더>      설치 폴더(.env · compose.yml · .install-manifest 가 있는 곳, 기본: 이 스크립트가 있는 폴더 → 없으면 /opt/roboyam)
#   --project <이름>  설치 기록(.install-manifest)이 없을 때 쓸 프로젝트 이름(기본 roboyam) — DB 컨테이너는 <이름>-postgres
#   --help            이 도움말
#
# 이메일은 psql 변수로 넘겨 SQL 문 안에서 문자열로만 쓰인다(SQL 주입 없음). 비밀값은 다루지 않는다.
# =============================================================================
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/common.sh
. "$SCRIPT_DIR/lib/common.sh"

DIR=""
PROJECT=""
EMAIL=""

usage() { sed -n '/^# 사용법/,/^# 이메일은/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
usage_error() { printf '%s\n' "오류: $*" >&2; printf '%s\n' "도움말: $0 --help" >&2; exit 2; }
need_arg() { if [ $# -lt 2 ] || [ -z "$2" ]; then usage_error "$1 에 값이 필요합니다"; fi; }

while [ $# -gt 0 ]; do
    case "$1" in
        --*=*) set -- "${1%%=*}" "${1#*=}" "${@:2}" ;;
    esac
    case "$1" in
        --dir)      need_arg "$@"; DIR=$2; shift 2 ;;
        --project)  need_arg "$@"; PROJECT=$2; shift 2 ;;
        --version)  echo "roboyam-mfa-reset $INSTALLER_VERSION"; exit 0 ;;
        --help|-h)  usage; exit 0 ;;
        -*)         usage_error "알 수 없는 옵션: $1" ;;
        *)
            [ -z "$EMAIL" ] || usage_error "이메일은 하나만 줄 수 있습니다"
            EMAIL=$1; shift ;;
    esac
done

[ -n "$EMAIL" ] || usage_error "초기화할 운영자의 이메일을 주세요"
# 이메일 모양만 본다(실제 확인은 DB 가 대소문자를 무시하고 한다). 254자 이하, 공백·제어문자 없음, @ 가 하나
RE_EMAIL='^[^[:space:][:cntrl:]@]+@[^[:space:][:cntrl:]@]+$'
if [ ${#EMAIL} -gt 254 ] || ! [[ $EMAIL =~ $RE_EMAIL ]]; then
    usage_error "이메일 형식이 아닙니다"
fi
RE_PROJECT='^[a-z0-9][a-z0-9_-]{0,30}$'
if [ -n "$PROJECT" ] && ! [[ $PROJECT =~ $RE_PROJECT ]]; then
    usage_error "--project 는 소문자·숫자·-_ 로 31자 이내여야 합니다: $PROJECT"
fi

# 설치 폴더: 주지 않으면 이 스크립트 옆에 설치 기록이 있으면 거기, 아니면 /opt/roboyam
if [ -z "$DIR" ]; then
    if [ -f "$SCRIPT_DIR/$MANIFEST_NAME" ] || [ -f "$SCRIPT_DIR/.env" ]; then
        DIR=$SCRIPT_DIR
    else
        DIR="/opt/roboyam"
    fi
fi
DIR=$(abs_path "$DIR")

command -v docker > /dev/null 2>&1 || { printf '%s\n' "오류: docker 를 찾을 수 없습니다." >&2; exit 1; }
docker info > /dev/null 2>&1 || { printf '%s\n' "오류: docker 데몬에 연결할 수 없습니다(Docker 가 켜져 있는지, 권한이 있는지 확인)." >&2; exit 1; }

# 프로젝트 이름: 설치 기록이 먼저, 없으면 --project, 그것도 없으면 roboyam (roboyam-uninstall.sh 와 같은 순서)
if [ -f "$DIR/$MANIFEST_NAME" ]; then
    MANIFEST="$DIR/$MANIFEST_NAME"
    m_project=$(manifest_get project)
    [ -n "$PROJECT" ] && [ -n "$m_project" ] && [ "$PROJECT" != "$m_project" ] \
        && warn "--project $PROJECT 는 무시합니다 — 설치 폴더의 기록은 프로젝트 $m_project 입니다."
    PROJECT=${m_project:-${PROJECT:-roboyam}}
else
    PROJECT=${PROJECT:-roboyam}
fi

# DB 계정: 설치 폴더의 .env 의 DB_USERNAME(설치기가 roboyam 으로 쓴다). 값만 읽고 다른 줄(비밀)은 건드리지 않는다
DB_USER=""
if [ -f "$DIR/.env" ]; then
    DB_USER=$(sed -n 's/^DB_USERNAME=//p' "$DIR/.env" 2> /dev/null | tail -1 | tr -d '\r"'"'")
fi
DB_USER=${DB_USER:-roboyam}
RE_DBUSER='^[A-Za-z_][A-Za-z0-9_]{0,62}$'
[[ $DB_USER =~ $RE_DBUSER ]] || { printf '%s\n' "오류: .env 의 DB_USERNAME 값이 올바르지 않습니다." >&2; exit 1; }

CONTAINER="$PROJECT-postgres"
if ! obj_exists container "$CONTAINER"; then
    printf '%s\n' "오류: DB 컨테이너($CONTAINER)를 찾을 수 없습니다. --dir 이나 --project 를 확인하세요." >&2
    exit 1
fi
label=$(obj_label container "$CONTAINER" "$LABEL_PROJECT")
if [ "$label" != "$PROJECT" ]; then
    printf '%s\n' "오류: $CONTAINER 는 이 설치기가 만든 컨테이너가 아닙니다(라벨 $LABEL_PROJECT=$PROJECT 없음). 건드리지 않습니다." >&2
    exit 1
fi
if [ "$(container_state "$CONTAINER")" != "running" ]; then
    printf '%s\n' "오류: DB 컨테이너($CONTAINER)가 실행 중이 아닙니다. 먼저 서비스를 켜 주세요." >&2
    exit 1
fi

say "ROBOYAM 관리자 2차 인증 초기화 (roboyam-mfa-reset $INSTALLER_VERSION)"
say "   설치 폴더 : $DIR"
say "   DB 컨테이너: $CONTAINER"
say "   대상 이메일: $EMAIL"

# psql 에 SQL 은 표준 입력으로, 이메일은 -v 변수로 준다. :'email' 은 psql 이 문자열 리터럴로 따옴표 처리한다(-c 로는 변수 치환이 안 된다).
# 함수가 없으면(관리자 서버가 아직 DB 를 이관하지 않음) NO_FUNCTION 을 돌려준다.
RESULT=$(printf '%s\n' \
    "SELECT to_regprocedure('admin_mfa_reset(text)') IS NOT NULL AS has_fn \\gset" \
    "\\if :has_fn" \
    "SELECT admin_mfa_reset(:'email');" \
    "\\else" \
    "SELECT 'NO_FUNCTION';" \
    "\\endif" \
    | docker exec -i "$CONTAINER" psql -X -q -t -A -v ON_ERROR_STOP=1 -v email="$EMAIL" -U "$DB_USER" -d roboyam 2> /dev/null | tr -d '\r' | tail -1)
status=$?

case "$RESULT" in
    RESET)
        say ""
        say "${C_GREEN}초기화했습니다.${C_OFF} 이 운영자의 OTP·복구 코드·신뢰 기기를 지우고 감사 기록(MFA_RESET)을 남겼습니다."
        say "   이제 비밀번호로 로그인할 수 있습니다. 2차 인증이 켜져 있으면 로그인할 때 OTP 를 다시 등록하게 됩니다."
        exit 0 ;;
    NOT_FOUND)
        printf '%s\n' "이 이메일의 운영자가 없습니다. 아무것도 바꾸지 않았습니다." >&2
        exit 3 ;;
    NO_FUNCTION)
        printf '%s\n' "오류: DB 에 초기화 함수(admin_mfa_reset)가 없습니다. 관리자 서버를 한 번 띄워 DB 를 최신으로 이관한 뒤 다시 실행하세요." >&2
        exit 1 ;;
    *)
        printf '%s\n' "오류: 초기화하지 못했습니다(psql 종료 코드 $status). DB 컨테이너 상태와 DB_USERNAME 을 확인하세요." >&2
        exit 1 ;;
esac
