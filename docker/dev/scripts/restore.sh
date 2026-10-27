#!/usr/bin/env bash
# =============================================================================
# restore.sh — backup.sh 가 만든 백업으로 DB(와 올린 이미지)를 되살린다  ⚠ 지금 DB 를 지우고 덮어쓴다
# =============================================================================
#
# 사용법(서버에서):
#   aws s3 ls <BACKUP_S3_URI>/db/                    # 되살릴 백업 고르기
#   /opt/roboyam/dev/scripts/restore.sh <BACKUP_S3_URI>/db/roboyam-20261027-0400.dump
#   /opt/roboyam/dev/scripts/restore.sh <dump> --media <BACKUP_S3_URI>/media/media-20261027-0400.tar.gz
#
# 인자는 S3 주소(s3://...)나 서버의 파일 경로. 옵션 --yes 는 확인 질문을 건너뛴다.
#
# 순서: (지금 DB 를 before-restore-*.dump 로 한 번 더 떠 둔다) → app · admin 정지 → DB 를 지우고 같은 로캘(C.UTF-8)로 다시 만든다 →
#       pg_restore → postgres 재시작(pg_cron 이 새 DB 에 다시 붙게) → app · admin 기동 → (--media 면) 올린 이미지 덮어쓰기
#
# 키: 백업을 되살리는 서버의 ROBOYAM_DATA_KEY · ROBOYAM_INDEX_KEY 가 백업을 뜬 서버와 같아야 개인정보 칸을 읽을 수 있다.
#     새 서버라면 init-env.sh 로 만든 .env 의 두 키를 SSM(/roboyam/dev/*)의 예전 값으로 바꾼 뒤 되살린다(docker/dev/README.md 「서버를 잃었을 때」).
# ⚠ 실제 서버에서 아직 시험하지 않았다(문법만 확인). 처음 쓰기 전에 한 번 연습해 둔다.
# =============================================================================
set -Eeuo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

BACKUP_DIR=${BACKUP_DIR:-/opt/roboyam/backups}
DUMP="" MEDIA="" YES=0
while [ $# -gt 0 ]; do
    case "$1" in
        --media) MEDIA=${2:-}; shift 2 ;;
        --yes)   YES=1; shift ;;
        -h|--help) sed -n '/^# 사용법/,/^# 인자/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*)      fail "알 수 없는 옵션: $1" ;;
        *)       DUMP=$1; shift ;;
    esac
done
[ -n "$DUMP" ] || fail "되살릴 DB 백업(s3://... 또는 파일)을 주세요 (도움말: $0 --help)"
require_env_file
DB_USER=$(env_get DB_USERNAME)
umask 077
mkdir -p "$BACKUP_DIR"

# S3 주소면 서버로 내려받는다
fetch() {
    case "$1" in
        s3://*) local f="$BACKUP_DIR/$(basename "$1")"; aws s3 cp --only-show-errors "$1" "$f" || fail "내려받기 실패: $1"; printf '%s' "$f" ;;
        *)      [ -f "$1" ] || fail "파일이 없습니다: $1"; printf '%s' "$1" ;;
    esac
}
DUMP_FILE=$(fetch "$DUMP")
MEDIA_FILE=""
[ -z "$MEDIA" ] || MEDIA_FILE=$(fetch "$MEDIA")

if [ $YES = 0 ]; then
    echo "지금 DB(roboyam)를 지우고 $(basename "$DUMP_FILE") 로 덮어씁니다.${MEDIA_FILE:+ 올린 이미지도 $(basename "$MEDIA_FILE") 로 덮어씁니다.}"
    echo "그동안 사이트는 멈춥니다. 계속하려면 restore 를 입력하세요:"
    read -r answer
    [ "$answer" = "restore" ] || fail "취소했습니다."
fi

echo "[1/5] 지금 DB 를 한 번 더 떠 두기"
before="$BACKUP_DIR/before-restore-$(date '+%Y%m%d-%H%M').dump"
if dc exec -T postgres pg_dump -U "$DB_USER" -d roboyam -Fc > "$before" 2> /dev/null; then
    note "$before"
else
    rm -f "$before"
    note "⚠ 지금 DB 를 뜨지 못했습니다(이미 망가졌을 수 있다) — 그대로 진행"
fi

echo "[2/5] app · admin 정지"
dc stop app admin

echo "[3/5] DB 다시 만들기 (로캘 C.UTF-8 — pg_trgm 한글 색인에 필요)"
dc exec -T postgres dropdb -U "$DB_USER" --if-exists --force roboyam
dc exec -T postgres createdb -U "$DB_USER" -O "$DB_USER" -T template0 -E UTF8 --locale-provider=libc --locale=C.UTF-8 roboyam

echo "[4/5] pg_restore"
# 확장 주석 같은 사소한 오류에도 0 이 아닌 값으로 끝날 수 있어 멈추지 않고 경고만 한다 — 아래 기동 확인으로 판단한다
if ! dc exec -T postgres pg_restore -U "$DB_USER" -d roboyam --no-owner < "$DUMP_FILE"; then
    note "⚠ pg_restore 가 경고 · 오류를 냈습니다(위 출력 확인). 앱이 정상으로 뜨는지 확인하세요."
fi
dc restart postgres

echo "[5/5] app · admin 기동"
dc up -d app admin
if [ -n "$MEDIA_FILE" ]; then
    sleep 5
    dc exec -T admin sh -c 'find /var/lib/roboyam/media -mindepth 1 -delete && tar -C /var/lib/roboyam/media -xzf -' < "$MEDIA_FILE" \
        || fail "올린 이미지 되살리기 실패(DB 는 되살렸다)"
    note "올린 이미지 되살림"
fi

echo
echo "완료. 확인: $SCRIPT_DIR/deploy.sh (태그 없이 — 기동 확인만 다시 한다), 사이트에서 최근 글 · 회원 확인"
