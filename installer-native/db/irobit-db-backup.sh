#!/usr/bin/env bash
# ============================================================================
# 아이로빗 DB 백업 — pg_dump -Fc + 역할(globals) 덤프, 최근 N개만 남긴다
#
# irobit-db-install.sh 가 /opt/irobit/db/bin 에 복사하고 /etc/cron.d/irobit-db-backup 로 매일 부른다(root).
# 손으로도 돌린다 — 배포 전·위험한 작업 전에:
#   sudo /opt/irobit/db/bin/irobit-db-backup.sh
#
# 설정: /etc/irobit/db-backup.conf (PG_BIN · DB_NAME · BACKUP_DIR · BACKUP_KEEP · BACKUP_HOOK)
# 만드는 것:
#   <BACKUP_DIR>/irobit-<날짜시각>.dump          pg_restore 로 되살린다
#   <BACKUP_DIR>/globals-<날짜시각>.sql           역할(계정·비밀번호 해시) — pg_dumpall --globals-only
# 훅: BACKUP_HOOK 이 실행 파일이면 「덤프 경로 globals 경로」를 인자로 부른다(R2 업로드 등 — backup-hook.example.sh).
#     훅이 실패하면 종료 코드 4 (덤프는 서버에 남아 있다).
#
# 되살리기(예):
#   sudo -u postgres pg_restore -d irobit --clean --if-exists --no-owner --role=irobit <덤프>
# ============================================================================
set -Eeuo pipefail

CONF="${IROBIT_BACKUP_CONF:-/etc/irobit/db-backup.conf}"
[ -f "$CONF" ] || { echo "설정 파일이 없습니다: $CONF" >&2; exit 1; }

conf_get() { sed -n "s/^$1=//p" "$CONF" | tail -n 1; }
PG_BIN="$(conf_get PG_BIN)"
DB_NAME="$(conf_get DB_NAME)"
BACKUP_DIR="$(conf_get BACKUP_DIR)"
BACKUP_KEEP="$(conf_get BACKUP_KEEP)"
BACKUP_HOOK="$(conf_get BACKUP_HOOK)"
: "${PG_BIN:=/usr/pgsql-18/bin}" "${DB_NAME:=irobit}" "${BACKUP_DIR:=/var/backups/irobit}" "${BACKUP_KEEP:=7}"

STAMP="$(date '+%Y%m%d-%H%M')"
umask 077
mkdir -p "$BACKUP_DIR"

echo "$(date '+%F %T') 백업 시작 (${DB_NAME})"

dump="${BACKUP_DIR}/irobit-${STAMP}.dump"
globals="${BACKUP_DIR}/globals-${STAMP}.sql"

# 실패한 반쪽 파일이 남지 않게 임시 이름으로 쓰고 끝나면 바꾼다
if ! runuser -u postgres -- "${PG_BIN}/pg_dump" -d "$DB_NAME" -Fc > "${dump}.part"; then
    rm -f "${dump}.part"
    echo "$(date '+%F %T') pg_dump 실패" >&2
    exit 1
fi
mv "${dump}.part" "$dump"

if runuser -u postgres -- "${PG_BIN}/pg_dumpall" --globals-only > "${globals}.part"; then
    mv "${globals}.part" "$globals"
else
    rm -f "${globals}.part"
    globals=""
    echo "$(date '+%F %T') ⚠ globals 덤프 실패 — DB 덤프는 끝났다" >&2
fi

echo "$(date '+%F %T') 덤프: ${dump} ($(du -h "$dump" | cut -f1))"

# 서버에는 최근 것만 남긴다
# shellcheck disable=SC2012
ls -1t "$BACKUP_DIR"/irobit-*.dump 2>/dev/null | tail -n +$((BACKUP_KEEP + 1)) | xargs -r rm -f
# shellcheck disable=SC2012
ls -1t "$BACKUP_DIR"/globals-*.sql 2>/dev/null | tail -n +$((BACKUP_KEEP + 1)) | xargs -r rm -f

if [ -n "$BACKUP_HOOK" ] && [ -x "$BACKUP_HOOK" ]; then
    echo "$(date '+%F %T') 훅 실행: ${BACKUP_HOOK}"
    if ! "$BACKUP_HOOK" "$dump" ${globals:+"$globals"}; then
        echo "$(date '+%F %T') ⚠ 훅 실패 — 덤프는 서버에 남아 있다" >&2
        exit 4
    fi
fi

echo "$(date '+%F %T') 백업 끝"
